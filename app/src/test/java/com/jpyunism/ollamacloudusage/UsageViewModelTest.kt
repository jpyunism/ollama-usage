package com.jpyunism.ollamacloudusage

import android.content.SharedPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
    ): UsageViewModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        return UsageViewModel(
            prefs = prefs,
            repository = repository,
            updateRepository = updateRepository,
            ioDispatcher = dispatcher,
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
}
