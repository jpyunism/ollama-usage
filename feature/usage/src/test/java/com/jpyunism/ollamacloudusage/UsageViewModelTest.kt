package com.jpyunism.ollamacloudusage

import android.content.Context
import android.content.SharedPreferences
import io.mockk.AnyTypedMatcher
import io.mockk.SameInstanceMatcher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class UsageViewModelTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakePrefs(): SharedPreferences {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getString(PrefsKeys.COOKIE, null) } returns null
        every { prefs.contains(PrefsKeys.COOKIE) } returns false
        every { prefs.getBoolean(PrefsKeys.NOTIF_ENABLED, true) } returns true
        every { prefs.getBoolean(PrefsKeys.PERSISTENT_ENABLED, true) } returns true
        every { prefs.getInt(PrefsKeys.WEEKLY_ALERT, 80) } returns 80
        every { prefs.getInt(PrefsKeys.WEEKLY_CRITICAL, 95) } returns 95
        every { prefs.getInt(PrefsKeys.SESSION_ALERT, 80) } returns 80
        every { prefs.getInt(PrefsKeys.SESSION_CRITICAL, 95) } returns 95
        every { prefs.getInt(PrefsKeys.REFRESH_INTERVAL, 60) } returns 60
        every { prefs.getString(PrefsKeys.RESET_DISPLAY, null) } returns null
        every { prefs.edit() } returns mockk(relaxed = true)
        return prefs
    }

    private fun sampleData() = UsageData(
        sessionPercent = 12.5,
        weeklyPercent = 41.7,
        sessionResetAt = Instant.parse("2026-08-08T18:00:00Z"),
        sessionModels = listOf(ModelUsage("deepseek-v4-flash:0731", 78, 100.0)),
        weeklyModels = listOf(ModelUsage("qwen3.5:397b", 116, 6.5)),
        plan = "pro",
    )

    /** Repo fake que delega en un fetcher real (misma semántica que el pipeline). */
    private fun fakeRepository(
        prefs: SharedPreferences,
        fetcher: UsageScraper,
        hasAuth: Boolean,
    ): UsageRepository = mockk<UsageRepository>(relaxed = true).apply {
        every { this@apply.hasAuth() } returns hasAuth
        every { this@apply.authSource() } returns AuthSource.COOKIE
        every { this@apply.currentSecret(any()) } returns ""
        coEvery { this@apply.refreshAndPropagate() } coAnswers {
            runCatching { fetcher.fetchUsage("") }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(UsageError.fromThrowable(it)) },
            )
        }
    }

    /** Crea un VM cuyo Main y dispatcher de IO comparten el scheduler del test. */
    private fun TestScope.buildVm(
        prefs: SharedPreferences,
        repository: UsageRepository = mockk(relaxed = true),
        updateRepository: UpdateRepository = mockk(relaxed = true),
        historyStore: UsageHistoryStore = mockk(relaxed = true),
        apiKeyValidator: OllamaApiKeyValidator = mockk(relaxed = true),
    ): UsageViewModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        return UsageViewModel(
            prefs = prefs,
            repository = repository,
            updateRepository = updateRepository,
            ioDispatcher = dispatcher,
            historyStoreProvider = { historyStore },
            apiKeyValidator = apiKeyValidator,
        )
    }

    @Test
    fun `sin auth queda en Idle`() = runTest {
        val repo = mockk<UsageRepository>(relaxed = true)
        every { repo.hasAuth() } returns false
        val vm = buildVm(fakePrefs(), repo)
        assertEquals(UiState.Idle, vm.uiState.value)
        assertTrue(!vm.hasAuth())
    }

    @Test
    fun `refresh con cookie exitosa produce Success`() = runTest {
        val prefs = fakePrefs()
        every { prefs.contains(PrefsKeys.COOKIE) } returns true
        every { prefs.getString(PrefsKeys.COOKIE, null) } returns "aid=abc; __Secure-session=xyz"
        val fetcher = mockk<UsageScraper>()
        every { fetcher.fetchUsage(any()) } returns sampleData()
        val repo = fakeRepository(prefs, fetcher, hasAuth = true)

        val vm = buildVm(prefs, repo)
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("expected Success got $state", state is UiState.Success)
        assertEquals(41.7, (state as UiState.Success).data.weeklyPercent, 0.001)
        verify { fetcher.fetchUsage(any()) }
    }

    @Test
    fun `cookie expirada produce Error tipado CookieExpired`() = runTest {
        val prefs = fakePrefs()
        every { prefs.contains(PrefsKeys.COOKIE) } returns true
        every { prefs.getString(PrefsKeys.COOKIE, null) } returns "cookie"
        val fetcher = mockk<UsageScraper>()
        every { fetcher.fetchUsage(any()) } throws CookieExpiredException()
        val repo = fakeRepository(prefs, fetcher, hasAuth = true)

        val vm = buildVm(prefs, repo)
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals(UsageError.CookieExpired, (state as UiState.Error).error)
    }

    @Test
    fun `error de red produce Error tipado Network`() = runTest {
        val prefs = fakePrefs()
        every { prefs.contains(PrefsKeys.COOKIE) } returns true
        every { prefs.getString(PrefsKeys.COOKIE, null) } returns "cookie"
        val fetcher = mockk<UsageScraper>()
        every { fetcher.fetchUsage(any()) } throws RuntimeException("timeout")
        val repo = fakeRepository(prefs, fetcher, hasAuth = true)

        val vm = buildVm(prefs, repo)
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals(UsageError.Network("timeout"), (state as UiState.Error).error)
    }

    // ─── Banner de cookie tras renovar (issue #78) ───

    @Test
    fun `refresh actualiza el banner de cookie con el status recalculado`() = runTest {
        // Issue #78: tras renovar la cookie, el refresh debe propagar el nuevo
        // status al banner aunque el fetch falle. El repo mockeado devuelve
        // EXPIRED antes de renovar y OK después.
        val prefs = fakePrefs()
        every { prefs.contains(PrefsKeys.COOKIE) } returns true
        every { prefs.getString(PrefsKeys.COOKIE, null) } returns "cookie"
        val fetcher = mockk<UsageScraper>()
        every { fetcher.fetchUsage(any()) } throws CookieExpiredException()
        val repo = fakeRepository(prefs, fetcher, hasAuth = true)
        every { repo.cookieExpiryStatus() } returns CookieExpiry.Result(CookieExpiry.Status.EXPIRED, 0)

        val vm = buildVm(prefs, repo)
        testScheduler.advanceUntilIdle()
        assertEquals(CookieExpiry.Status.EXPIRED, vm.cookieExpiry.value.status)

        // El usuario renueva la cookie: el repo ahora reporta OK.
        every { repo.cookieExpiryStatus() } returns CookieExpiry.Result(CookieExpiry.Status.OK, 7)

        // Un nuevo refresh (aunque falle) debe propagar el status OK al banner.
        vm.refresh()
        testScheduler.advanceUntilIdle()

        assertEquals(CookieExpiry.Status.OK, vm.cookieExpiry.value.status)
        assertTrue(vm.uiState.value is UiState.Error)
    }

    @Test
    fun `refresh normal setea Loading e isRefreshing true y termina false`() = runTest {
        val prefs = fakePrefs()
        every { prefs.contains(PrefsKeys.COOKIE) } returns true
        every { prefs.getString(PrefsKeys.COOKIE, null) } returns "aid=abc; __Secure-session=xyz"
        val fetcher = mockk<UsageScraper>()
        every { fetcher.fetchUsage(any()) } returns sampleData()
        val repo = fakeRepository(prefs, fetcher, hasAuth = true)

        val vm = buildVm(prefs, repo)
        vm.refresh()
        assertTrue("isRefreshing debe estar en true durante el refresh", vm.isRefreshing.value)
        assertTrue("refresh normal debe setear Loading", vm.uiState.value is UiState.Loading)

        testScheduler.advanceUntilIdle()
        assertFalse("isRefreshing debe volver a false al terminar", vm.isRefreshing.value)
        assertTrue(vm.uiState.value is UiState.Success)
    }

    @Test
    fun `refresh silencioso no pisa el contenido visible`() = runTest {
        val prefs = fakePrefs()
        every { prefs.contains(PrefsKeys.COOKIE) } returns true
        every { prefs.getString(PrefsKeys.COOKIE, null) } returns "aid=abc; __Secure-session=xyz"
        val fetcher = mockk<UsageScraper>()
        every { fetcher.fetchUsage(any()) } returns sampleData()
        val repo = fakeRepository(prefs, fetcher, hasAuth = true)

        val vm = buildVm(prefs, repo)
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value is UiState.Success)

        vm.refresh(fromPull = true)
        assertTrue("isRefreshing debe estar en true durante el pull", vm.isRefreshing.value)
        assertTrue(
            "el pull no debe ocultar el contenido: ${vm.uiState.value}",
            vm.uiState.value is UiState.Success,
        )

        testScheduler.advanceUntilIdle()
        assertFalse("isRefreshing debe volver a false al terminar", vm.isRefreshing.value)
        assertTrue(vm.uiState.value is UiState.Success)
    }

    @Test
    fun `refresh silencioso con error muestra Error y no deja isRefreshing pegada`() = runTest {
        val prefs = fakePrefs()
        every { prefs.contains(PrefsKeys.COOKIE) } returns true
        every { prefs.getString(PrefsKeys.COOKIE, null) } returns "aid=abc; __Secure-session=xyz"
        val fetcher = mockk<UsageScraper>()
        every { fetcher.fetchUsage(any()) } throws RuntimeException("timeout")
        val repo = fakeRepository(prefs, fetcher, hasAuth = true)

        val vm = buildVm(prefs, repo)
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value is UiState.Error)

        vm.refresh(fromPull = true)
        assertTrue(vm.isRefreshing.value)
        testScheduler.advanceUntilIdle()
        assertFalse("isRefreshing no debe quedar pegada tras un error", vm.isRefreshing.value)
        assertTrue(vm.uiState.value is UiState.Error)
    }

    @Test
    fun `clearAuth vuelve a Idle`() = runTest {
        val prefs = fakePrefs()
        every { prefs.contains(PrefsKeys.COOKIE) } returns true
        every { prefs.getString(PrefsKeys.COOKIE, null) } returns "cookie"
        val fetcher = mockk<UsageScraper>()
        every { fetcher.fetchUsage(any()) } returns sampleData()
        val repo = fakeRepository(prefs, fetcher, hasAuth = true)

        val vm = buildVm(prefs, repo)
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value is UiState.Success)

        vm.clearAuth()
        assertEquals(UiState.Idle, vm.uiState.value)
    }

    // ─────────── Ambos secretos coexisten ───────────

    @Test
    fun `saveApiKey no borra la cookie guardada`() = runTest {
        val prefs = fakePrefs()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putString(any(), any()) } returns editor
        every { prefs.edit() } returns editor

        val vm = buildVm(prefs, mockk(relaxed = true))
        vm.saveApiKey("sk-test")

        verify { editor.putString(PrefsKeys.API_KEY, "sk-test") }
        verify { editor.putString(PrefsKeys.AUTH_SOURCE, AuthSource.API_KEY.name) }
        verify(exactly = 0) { editor.remove(PrefsKeys.COOKIE) }
    }

    @Test
    fun `saveCookie no borra la api key guardada`() = runTest {
        val prefs = fakePrefs()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putString(any(), any()) } returns editor
        every { prefs.edit() } returns editor

        val vm = buildVm(prefs, mockk(relaxed = true))
        vm.saveCookie("aid=abc; __Secure-session=xyz")

        verify { editor.putString(PrefsKeys.COOKIE, "aid=abc; __Secure-session=xyz") }
        verify { editor.putString(PrefsKeys.AUTH_SOURCE, AuthSource.COOKIE.name) }
        verify(exactly = 0) { editor.remove(PrefsKeys.API_KEY) }
    }

    // ─────────── Captura de cookie vía WebView ───────────

    @Test
    fun `openCookieWebView activa el flag`() = runTest {
        val vm = buildVm(fakePrefs(), mockk(relaxed = true))
        assertFalse(vm.showCookieWebView.value)
        vm.openCookieWebView()
        assertTrue(vm.showCookieWebView.value)
    }

    @Test
    fun `closeCookieWebView apaga el flag sin guardar`() = runTest {
        val vm = buildVm(fakePrefs(), mockk(relaxed = true))
        vm.openCookieWebView()
        vm.closeCookieWebView()
        assertFalse(vm.showCookieWebView.value)
    }

    @Test
    fun `saveCookieFromWebView guarda y cierra el flujo`() = runTest {
        val prefs = fakePrefs()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putString(any(), any()) } returns editor
        every { prefs.edit() } returns editor

        val vm = buildVm(prefs, mockk(relaxed = true))
        vm.openCookieWebView()
        vm.saveCookieFromWebView("aid=abc; __Secure-session=xyz")

        assertFalse(vm.showCookieWebView.value)
        assertFalse(vm.showAuthSetup.value)
        verify { editor.putString(PrefsKeys.COOKIE, "aid=abc; __Secure-session=xyz") }
        verify { editor.putString(PrefsKeys.AUTH_SOURCE, AuthSource.COOKIE.name) }
    }

    @Test
    fun `currentSecret devuelve el secreto guardado del metodo indicado`() = runTest {
        val repo = mockk<UsageRepository>(relaxed = true)
        every { repo.currentSecret(AuthSource.API_KEY) } returns "sk-guardada"
        every { repo.currentSecret(AuthSource.COOKIE) } returns "cookie-guardada"

        val vm = buildVm(fakePrefs(), repo)
        assertEquals("sk-guardada", vm.currentSecret(AuthSource.API_KEY))
        assertEquals("cookie-guardada", vm.currentSecret(AuthSource.COOKIE))
    }

    @Test
    fun `currentSecret devuelve vacio si no hay secreto`() = runTest {
        val repo = mockk<UsageRepository>(relaxed = true)
        every { repo.currentSecret(any()) } returns ""
        val vm = buildVm(fakePrefs(), repo)
        assertEquals("", vm.currentSecret(AuthSource.API_KEY))
        assertEquals("", vm.currentSecret(AuthSource.COOKIE))
    }

    @Test
    fun `checkForUpdateNow con repo que devuelve update marca Available`() = runTest {
        val updateRepo = mockk<UpdateRepository>(relaxed = true)
        every { updateRepo.check() } returns UpdateInfo("9.9.9", "https://example.com/app.apk", null)
        val vm = buildVm(fakePrefs(), mockk(relaxed = true), updateRepo)
        vm.checkForUpdateNow()
        testScheduler.advanceUntilIdle()
        assertEquals(UpdateCheckOutcome.Available(UpdateInfo("9.9.9", "https://example.com/app.apk", null)), vm.checkResult.value)
    }

    @Test
    fun `checkForUpdateNow sin update marca UpToDate`() = runTest {
        val updateRepo = mockk<UpdateRepository>(relaxed = true)
        every { updateRepo.check() } returns null
        val vm = buildVm(fakePrefs(), mockk(relaxed = true), updateRepo)
        vm.checkForUpdateNow()
        testScheduler.advanceUntilIdle()
        assertEquals(UpdateCheckOutcome.UpToDate, vm.checkResult.value)
    }

    // ─────────── Configuración de alertas ───────────

    @Test
    fun `settings por defecto son 80 y 95`() = runTest {
        val vm = buildVm(fakePrefs(), mockk(relaxed = true))
        val s = vm.settings.value
        assertEquals(80, s.weeklyAlert)
        assertEquals(95, s.weeklyCritical)
        assertEquals(80, s.sessionAlert)
        assertEquals(95, s.sessionCritical)
        assertTrue(s.notificationsEnabled)
        assertTrue(s.persistentEnabled)
        assertEquals(60, s.refreshIntervalMinutes)
        assertEquals(ResetDisplayMode.COUNTDOWN, s.resetDisplayMode)
    }

    @Test
    fun `updateSettings persiste y actualiza el estado`() = runTest {
        val prefs = fakePrefs()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        // Encadenamiento: cada put devuelve el mismo editor.
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { prefs.edit() } returns editor

        val vm = buildVm(prefs, mockk(relaxed = true))
        vm.updateSettings(
            AlertSettings(
                notificationsEnabled = false,
                weeklyAlert = 70,
                weeklyCritical = 90,
                sessionAlert = 60,
                sessionCritical = 85,
                persistentEnabled = false,
                refreshIntervalMinutes = 30,
                resetDisplayMode = ResetDisplayMode.DATE,
            )
        )

        val s = vm.settings.value
        assertFalse(s.notificationsEnabled)
        assertEquals(70, s.weeklyAlert)
        assertEquals(90, s.weeklyCritical)
        assertEquals(60, s.sessionAlert)
        assertEquals(85, s.sessionCritical)
        assertFalse(s.persistentEnabled)
        assertEquals(30, s.refreshIntervalMinutes)
        assertEquals(ResetDisplayMode.DATE, s.resetDisplayMode)

        verify { editor.putBoolean(PrefsKeys.NOTIF_ENABLED, false) }
        verify { editor.putInt(PrefsKeys.WEEKLY_ALERT, 70) }
        verify { editor.putInt(PrefsKeys.WEEKLY_CRITICAL, 90) }
        verify { editor.putInt(PrefsKeys.SESSION_ALERT, 60) }
        verify { editor.putInt(PrefsKeys.SESSION_CRITICAL, 85) }
        verify { editor.putBoolean(PrefsKeys.PERSISTENT_ENABLED, false) }
        verify { editor.putInt(PrefsKeys.REFRESH_INTERVAL, 30) }
        verify { editor.putString(PrefsKeys.RESET_DISPLAY, "DATE") }
    }

    @Test
    fun `cambiar frecuencia de refresco reprograma el worker`() = runTest {
        val prefs = fakePrefs()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { prefs.edit() } returns editor

        var rescheduled = -1
        val vm = UsageViewModel(
            prefs = prefs,
            repository = mockk(relaxed = true),
            updateRepository = mockk(relaxed = true),
            ioDispatcher = StandardTestDispatcher(testScheduler),
            reschedule = { rescheduled = it },
            historyStoreProvider = { mockk(relaxed = true) },
            apiKeyValidator = mockk(relaxed = true),
        )
        vm.updateSettings(vm.settings.value.copy(refreshIntervalMinutes = 30))

        assertEquals(30, rescheduled)
    }

    @Test
    fun `settings cargan valores guardados`() = runTest {
        val prefs = fakePrefs()
        every { prefs.getBoolean(PrefsKeys.NOTIF_ENABLED, true) } returns false
        every { prefs.getInt(PrefsKeys.WEEKLY_ALERT, 80) } returns 65
        every { prefs.getInt(PrefsKeys.WEEKLY_CRITICAL, 95) } returns 88
        every { prefs.getInt(PrefsKeys.SESSION_ALERT, 80) } returns 55
        every { prefs.getInt(PrefsKeys.SESSION_CRITICAL, 95) } returns 82

        val vm = buildVm(prefs, mockk(relaxed = true))
        val s = vm.settings.value
        assertFalse(s.notificationsEnabled)
        assertEquals(65, s.weeklyAlert)
        assertEquals(88, s.weeklyCritical)
        assertEquals(55, s.sessionAlert)
        assertEquals(82, s.sessionCritical)
    }

    @Test
    fun `modo de reset guardado se carga al iniciar`() = runTest {
        val prefs = fakePrefs()
        every { prefs.getString(PrefsKeys.RESET_DISPLAY, null) } returns "DATE"

        val vm = buildVm(prefs, mockk(relaxed = true))
        assertEquals(ResetDisplayMode.DATE, vm.settings.value.resetDisplayMode)
    }

    @Test
    fun `modo de reset invalido cae a COUNTDOWN`() = runTest {
        val prefs = fakePrefs()
        every { prefs.getString(PrefsKeys.RESET_DISPLAY, null) } returns "NoExiste"

        val vm = buildVm(prefs, mockk(relaxed = true))
        assertEquals(ResetDisplayMode.COUNTDOWN, vm.settings.value.resetDisplayMode)
    }

    // ─────────── Temas ───────────

    @Test
    fun `tema por defecto es Sistema`() = runTest {
        val vm = buildVm(fakePrefs(), mockk(relaxed = true))
        assertEquals(AppTheme.System, vm.theme.value)
    }

    @Test
    fun `updateTheme persiste y actualiza`() = runTest {
        val prefs = fakePrefs()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putString(any(), any()) } returns editor
        every { prefs.edit() } returns editor

        val vm = buildVm(prefs, mockk(relaxed = true))
        vm.updateTheme(AppTheme.Emerald)

        assertEquals(AppTheme.Emerald, vm.theme.value)
        verify { editor.putString(PrefsKeys.THEME, "Emerald") }
    }

    @Test
    fun `tema guardado se carga al iniciar`() = runTest {
        val prefs = fakePrefs()
        every { prefs.getString(PrefsKeys.THEME, null) } returns "Rose"

        val vm = buildVm(prefs, mockk(relaxed = true))
        assertEquals(AppTheme.Rose, vm.theme.value)
    }

    @Test
    fun `tema invalido en prefs cae a Sistema`() = runTest {
        val prefs = fakePrefs()
        every { prefs.getString(PrefsKeys.THEME, null) } returns "NoExiste"

        val vm = buildVm(prefs, mockk(relaxed = true))
        assertEquals(AppTheme.System, vm.theme.value)
    }

    @Test
    fun `modo oscuro por defecto es Sistema`() = runTest {
        val vm = buildVm(fakePrefs(), mockk(relaxed = true))
        assertEquals(AppDarkMode.System, vm.darkMode.value)
    }

    @Test
    fun `updateDarkMode persiste y actualiza`() = runTest {
        val prefs = fakePrefs()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putString(any(), any()) } returns editor
        every { prefs.edit() } returns editor

        val vm = buildVm(prefs, mockk(relaxed = true))
        vm.updateDarkMode(AppDarkMode.Dark)

        assertEquals(AppDarkMode.Dark, vm.darkMode.value)
        verify { editor.putString(PrefsKeys.DARK_MODE, "Dark") }
    }

    @Test
    fun `modo oscuro guardado se carga al iniciar`() = runTest {
        val prefs = fakePrefs()
        every { prefs.getString(PrefsKeys.DARK_MODE, null) } returns "Light"

        val vm = buildVm(prefs, mockk(relaxed = true))
        assertEquals(AppDarkMode.Light, vm.darkMode.value)
    }

    @Test
    fun `modo oscuro invalido en prefs cae a Sistema`() = runTest {
        val prefs = fakePrefs()
        every { prefs.getString(PrefsKeys.DARK_MODE, null) } returns "NoExiste"

        val vm = buildVm(prefs, mockk(relaxed = true))
        assertEquals(AppDarkMode.System, vm.darkMode.value)
    }

    @Test
    fun `idioma por defecto es Sistema`() = runTest {
        val vm = buildVm(fakePrefs(), mockk(relaxed = true))
        assertEquals(AppLanguage.System, vm.language.value)
    }

    @Test
    fun `updateLanguage persiste, actualiza y aplica el locale via callback`() = runTest {
        val prefs = fakePrefs()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putString(any(), any()) } returns editor
        every { prefs.edit() } returns editor

        var applied: AppLanguage? = null
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val vm = UsageViewModel(
            prefs = prefs,
            repository = mockk(relaxed = true),
            updateRepository = mockk(relaxed = true),
            ioDispatcher = dispatcher,
            onLanguageChange = { applied = it },
            historyStoreProvider = { mockk(relaxed = true) },
            apiKeyValidator = mockk(relaxed = true),
        )
        vm.updateLanguage(AppLanguage.English)

        assertEquals(AppLanguage.English, vm.language.value)
        assertEquals(AppLanguage.English, applied)
        verify { editor.putString(PrefsKeys.LANGUAGE, "English") }
    }

    @Test
    fun `idioma guardado se carga al iniciar`() = runTest {
        val prefs = fakePrefs()
        every { prefs.getString(PrefsKeys.LANGUAGE, null) } returns "Spanish"

        val vm = buildVm(prefs, mockk(relaxed = true))
        assertEquals(AppLanguage.Spanish, vm.language.value)
    }

    // ─────────── Proyeccion de agotamiento (issue #54) ───────────

    @Test
    fun `Success expone la proyeccion semanal cuando hay suficientes snapshots`() = runTest {
        val prefs = fakePrefs()
        every { prefs.contains(PrefsKeys.COOKIE) } returns true
        every { prefs.getString(PrefsKeys.COOKIE, null) } returns "aid=abc; __Secure-session=xyz"
        val fetcher = mockk<UsageScraper>()
        every { fetcher.fetchUsage(any()) } returns sampleData()
        val repo = fakeRepository(prefs, fetcher, hasAuth = true)
        // 3 snapshots crecientes -> proyeccion RISING.
        val snapshots = listOf(
            UsageSnapshot(1_000_000_000L, sessionPercent = 10.0, weeklyPercent = 10.0),
            UsageSnapshot(1_003_600_000L, sessionPercent = 20.0, weeklyPercent = 20.0),
            UsageSnapshot(1_007_200_000L, sessionPercent = 30.0, weeklyPercent = 30.0),
        )
        every { repo.historySnapshots() } returns snapshots

        val vm = buildVm(prefs, repo)
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("expected Success got $state", state is UiState.Success)
        val proj = (state as UiState.Success).weeklyProjection
        assertTrue("weeklyProjection no debe ser null", proj != null)
        assertEquals(ProjectionEngine.Trend.RISING, proj!!.trend)
    }

    @Test
    fun `Success con datos insuficientes no expone proyeccion`() = runTest {
        val prefs = fakePrefs()
        every { prefs.contains(PrefsKeys.COOKIE) } returns true
        every { prefs.getString(PrefsKeys.COOKIE, null) } returns "aid=abc; __Secure-session=xyz"
        val fetcher = mockk<UsageScraper>()
        every { fetcher.fetchUsage(any()) } returns sampleData()
        val repo = fakeRepository(prefs, fetcher, hasAuth = true)
        every { repo.historySnapshots() } returns emptyList()

        val vm = buildVm(prefs, repo)
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("expected Success got $state", state is UiState.Success)
        assertTrue((state as UiState.Success).weeklyProjection == null)
        assertTrue(state.sessionProjection == null)
    }

    // ── Validacion en vivo de API key (issue #63) ─────────────────────

    @Test
    fun `validateApiKey con key valida expone Valid`() = runTest {
        val validator = mockk<OllamaApiKeyValidator>()
        coEvery { validator.validate("good-key") } returns OllamaApiKeyValidator.Result.Valid
        val vm = buildVm(fakePrefs(), mockk(relaxed = true), apiKeyValidator = validator)
        vm.validateApiKey("good-key")
        testScheduler.advanceUntilIdle()
        assertEquals(ApiKeyValidation.Valid, vm.apiKeyValidation.value)
    }

    @Test
    fun `validateApiKey con 401 expone Invalid`() = runTest {
        val validator = mockk<OllamaApiKeyValidator>()
        coEvery { validator.validate("bad-key") } returns OllamaApiKeyValidator.Result.Invalid
        val vm = buildVm(fakePrefs(), mockk(relaxed = true), apiKeyValidator = validator)
        vm.validateApiKey("bad-key")
        testScheduler.advanceUntilIdle()
        assertEquals(ApiKeyValidation.Invalid, vm.apiKeyValidation.value)
    }

    @Test
    fun `validateApiKey con 500 expone Inconclusive (offline graceful)`() = runTest {
        val validator = mockk<OllamaApiKeyValidator>()
        coEvery { validator.validate("k") } returns OllamaApiKeyValidator.Result.Inconclusive
        val vm = buildVm(fakePrefs(), mockk(relaxed = true), apiKeyValidator = validator)
        vm.validateApiKey("k")
        testScheduler.advanceUntilIdle()
        assertEquals(ApiKeyValidation.Inconclusive, vm.apiKeyValidation.value)
    }

    @Test
    fun `validateApiKey con key en blanco queda Idle sin llamar al validator`() = runTest {
        val validator = mockk<OllamaApiKeyValidator>()
        coEvery { validator.validate(any()) } returns OllamaApiKeyValidator.Result.Valid
        val vm = buildVm(fakePrefs(), mockk(relaxed = true), apiKeyValidator = validator)
        vm.validateApiKey("   ")
        testScheduler.advanceUntilIdle()
        assertEquals(ApiKeyValidation.Idle, vm.apiKeyValidation.value)
        coVerify(exactly = 0) { validator.validate(any()) }
    }

    @Test
    fun `validateApiKey transiciona a InProgress mientras valida`() = runTest {
        val validator = mockk<OllamaApiKeyValidator>()
        coEvery { validator.validate("k") } coAnswers {
            kotlinx.coroutines.delay(1000)
            OllamaApiKeyValidator.Result.Valid
        }
        val vm = buildVm(fakePrefs(), mockk(relaxed = true), apiKeyValidator = validator)
        vm.validateApiKey("k")
        assertEquals(ApiKeyValidation.InProgress, vm.apiKeyValidation.value)
        testScheduler.advanceUntilIdle()
        assertEquals(ApiKeyValidation.Valid, vm.apiKeyValidation.value)
    }

    @Test
    fun `resetApiKeyValidation cancela y vuelve a Idle`() = runTest {
        val validator = mockk<OllamaApiKeyValidator>()
        coEvery { validator.validate("k") } coAnswers {
            kotlinx.coroutines.delay(1000)
            OllamaApiKeyValidator.Result.Valid
        }
        val vm = buildVm(fakePrefs(), mockk(relaxed = true), apiKeyValidator = validator)
        vm.validateApiKey("k")
        assertEquals(ApiKeyValidation.InProgress, vm.apiKeyValidation.value)
        vm.resetApiKeyValidation()
        testScheduler.advanceUntilIdle()
        assertEquals(ApiKeyValidation.Idle, vm.apiKeyValidation.value)
    }

    // --- factory inyecta el httpClient compartido en el validator (issue #81) ---

    @Test
    fun `factory construye el validator con el httpClient compartido`() = runTest {
        // Issue #81: UsageViewModel NO debe defaultear a OllamaApiKeyValidator()
        // (que crearia su propio OkHttpClient sin interceptors). El factory debe
        // pasar siempre container.httpClient. Verificamos que el validator se
        // construya con la MISMA instancia de OkHttpClient que AppContainer.

        mockkObject(com.jpyunism.ollamacloudusage.di.AppContainer)
        val container = mockk<com.jpyunism.ollamacloudusage.di.AppContainer>(relaxed = true)
        val sharedHttpClient = mockk<OkHttpClient>(relaxed = true)
        val prefs = fakePrefs()
        val usageRepository = mockk<UsageRepository>(relaxed = true)
        val updateRepository = mockk<UpdateRepository>(relaxed = true)
        val historyStore = mockk<UsageHistoryStore>(relaxed = true)
        every { container.prefs } returns prefs
        every { container.usageRepository } returns usageRepository
        every { container.updateRepository } returns updateRepository
        every { container.historyStore } returns historyStore
        every { container.httpClient } returns sharedHttpClient
        every { com.jpyunism.ollamacloudusage.di.AppContainer.get(any()) } returns container

        // Mockea el constructor del validator. Confirmamos que el factory lo
        // construye con la MISMA instancia de OkHttpClient compartido: si el
        // default `OllamaApiKeyValidator()` (propio OkHttpClient) volviera, el
        // validate() con la instancia compartida devolveria Inconclusive y el
        // assert final fallaria.
        mockkConstructor(OllamaApiKeyValidator::class)
        coEvery {
            constructedWith<OllamaApiKeyValidator>(
                SameInstanceMatcher(sharedHttpClient),
                AnyTypedMatcher(Any::class),
                AnyTypedMatcher(Any::class),
                AnyTypedMatcher(Any::class),
            ).validate("sk-test")
        } returns OllamaApiKeyValidator.Result.Valid
        val app = mockk<Context>(relaxed = true)
        every { app.applicationContext } returns app
        // viewModelScope corre en Main: ligarlo al scheduler del test.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val factory = UsageViewModel.factory(app)
        val vm: UsageViewModel = factory.create(UsageViewModel::class.java)

        vm.validateApiKey("sk-test")
        testScheduler.advanceUntilIdle()

        assertEquals(
            "el validator construido por el factory debe validar con el " +
                "httpClient compartido (issue #81)",
            ApiKeyValidation.Valid,
            vm.apiKeyValidation.value,
        )

        unmockkConstructor(OllamaApiKeyValidator::class)
        unmockkObject(com.jpyunism.ollamacloudusage.di.AppContainer)
    }

    @Test
    fun `una excepcion inesperada del repository no deja la UI en Loading`() = runTest {
        val repo = mockk<UsageRepository>(relaxed = true)
        every { repo.hasAuth() } returns true
        // El repo LANZA en vez de devolver Result.failure: este era el bug del
        // spinner infinito. El VM debe atraparlo y pasar a Error.
        coEvery { repo.refreshAndPropagate() } throws IllegalStateException("boom inesperado")

        val vm = buildVm(fakePrefs(), repo)
        testScheduler.advanceUntilIdle()

        assertTrue("la UI no debe quedar colgada en Loading", vm.uiState.value !is UiState.Loading)
        assertTrue("debe mapear a Error", vm.uiState.value is UiState.Error)
        assertFalse("el flag de refreshing debe resetearse", vm.isRefreshing.value)
    }
}
