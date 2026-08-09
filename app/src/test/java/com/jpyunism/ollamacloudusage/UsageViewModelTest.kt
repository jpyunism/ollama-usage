package com.jpyunism.ollamacloudusage

import android.content.Context
import android.content.SharedPreferences
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
        every { prefs.getString(UsageViewModel.KEY_COOKIE, null) } returns null
        every { prefs.contains(UsageViewModel.KEY_COOKIE) } returns false
        every { prefs.getBoolean(UsageViewModel.KEY_NOTIF_ENABLED, true) } returns true
        every { prefs.getBoolean(UsageViewModel.KEY_PERSISTENT_ENABLED, true) } returns true
        every { prefs.getInt(UsageViewModel.KEY_WEEKLY_ALERT, 80) } returns 80
        every { prefs.getInt(UsageViewModel.KEY_WEEKLY_CRITICAL, 95) } returns 95
        every { prefs.getInt(UsageViewModel.KEY_SESSION_ALERT, 80) } returns 80
        every { prefs.getInt(UsageViewModel.KEY_SESSION_CRITICAL, 95) } returns 95
        every { prefs.getInt(UsageViewModel.KEY_REFRESH_INTERVAL, 60) } returns 60
        every { prefs.getString(UsageViewModel.KEY_RESET_DISPLAY, null) } returns null
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

    /** Crea un VM cuyo Main y dispatcher de IO comparten el scheduler del test. */
    private fun TestScope.buildVm(
        prefs: SharedPreferences,
        scraper: UsageScraper,
    ): UsageViewModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        return UsageViewModel(prefs, scraper, dispatcher)
    }

    @Test
    fun `sin auth queda en Idle`() = runTest {
        val vm = buildVm(fakePrefs(), mockk(relaxed = true))
        assertEquals(UiState.Idle, vm.uiState.value)
        assertTrue(!vm.hasAuth())
    }

    @Test
    fun `refresh con cookie exitosa produce Success`() = runTest {
        val scraper = mockk<UsageScraper>()
        every { scraper.fetchUsage(any()) } returns sampleData()

        val prefs = fakePrefs()
        every { prefs.contains(UsageViewModel.KEY_COOKIE) } returns true
        every { prefs.getString(UsageViewModel.KEY_COOKIE, null) } returns "aid=abc; __Secure-session=xyz"

        val vm = buildVm(prefs, scraper)
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("expected Success got $state", state is UiState.Success)
        assertEquals(41.7, (state as UiState.Success).data.weeklyPercent, 0.001)
        verify { scraper.fetchUsage("aid=abc; __Secure-session=xyz") }
    }

    @Test
    fun `cookie expirada produce Error`() = runTest {
        val scraper = mockk<UsageScraper>()
        every { scraper.fetchUsage(any()) } throws CookieExpiredException()

        val prefs = fakePrefs()
        every { prefs.contains(UsageViewModel.KEY_COOKIE) } returns true
        every { prefs.getString(UsageViewModel.KEY_COOKIE, null) } returns "cookie"

        val vm = buildVm(prefs, scraper)
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is UiState.Error)
        assertTrue((state as UiState.Error).message.contains("expiró"))
    }

    @Test
    fun `error de red produce Error`() = runTest {
        val scraper = mockk<UsageScraper>()
        every { scraper.fetchUsage(any()) } throws RuntimeException("timeout")

        val prefs = fakePrefs()
        every { prefs.contains(UsageViewModel.KEY_COOKIE) } returns true
        every { prefs.getString(UsageViewModel.KEY_COOKIE, null) } returns "cookie"

        val vm = buildVm(prefs, scraper)
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is UiState.Error)
        assertTrue((state as UiState.Error).message.contains("Error de red"))
    }

    @Test
    fun `clearAuth vuelve a Idle`() = runTest {
        val scraper = mockk<UsageScraper>()
        every { scraper.fetchUsage(any()) } returns sampleData()

        val prefs = fakePrefs()
        every { prefs.contains(UsageViewModel.KEY_COOKIE) } returns true
        every { prefs.getString(UsageViewModel.KEY_COOKIE, null) } returns "cookie"

        val vm = buildVm(prefs, scraper)
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

        verify { editor.putString(UsageViewModel.KEY_API_KEY, "sk-test") }
        verify { editor.putString(UsageViewModel.KEY_AUTH_SOURCE, AuthSource.API_KEY.name) }
        verify(exactly = 0) { editor.remove(UsageViewModel.KEY_COOKIE) }
    }

    @Test
    fun `saveCookie no borra la api key guardada`() = runTest {
        val prefs = fakePrefs()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putString(any(), any()) } returns editor
        every { prefs.edit() } returns editor

        val vm = buildVm(prefs, mockk(relaxed = true))
        vm.saveCookie("aid=abc; __Secure-session=xyz")

        verify { editor.putString(UsageViewModel.KEY_COOKIE, "aid=abc; __Secure-session=xyz") }
        verify { editor.putString(UsageViewModel.KEY_AUTH_SOURCE, AuthSource.COOKIE.name) }
        verify(exactly = 0) { editor.remove(UsageViewModel.KEY_API_KEY) }
    }

    @Test
    fun `currentSecret devuelve el secreto guardado del metodo indicado`() = runTest {
        val prefs = fakePrefs()
        every { prefs.getString(UsageViewModel.KEY_API_KEY, null) } returns "sk-guardada"
        every { prefs.getString(UsageViewModel.KEY_COOKIE, null) } returns "cookie-guardada"

        val vm = buildVm(prefs, mockk(relaxed = true))
        assertEquals("sk-guardada", vm.currentSecret(AuthSource.API_KEY))
        assertEquals("cookie-guardada", vm.currentSecret(AuthSource.COOKIE))
    }

    @Test
    fun `currentSecret devuelve vacio si no hay secreto`() = runTest {
        val vm = buildVm(fakePrefs(), mockk(relaxed = true))
        assertEquals("", vm.currentSecret(AuthSource.API_KEY))
        assertEquals("", vm.currentSecret(AuthSource.COOKIE))
    }

    @Test
    fun `checkForUpdateNow sin contexto falla con Failed`() = runTest {
        val vm = buildVm(fakePrefs(), mockk(relaxed = true))
        vm.checkForUpdateNow()
        testScheduler.advanceUntilIdle()
        assertEquals(UpdateCheckOutcome.Failed, vm.checkResult.value)
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

        verify { editor.putBoolean(UsageViewModel.KEY_NOTIF_ENABLED, false) }
        verify { editor.putInt(UsageViewModel.KEY_WEEKLY_ALERT, 70) }
        verify { editor.putInt(UsageViewModel.KEY_WEEKLY_CRITICAL, 90) }
        verify { editor.putInt(UsageViewModel.KEY_SESSION_ALERT, 60) }
        verify { editor.putInt(UsageViewModel.KEY_SESSION_CRITICAL, 85) }
        verify { editor.putBoolean(UsageViewModel.KEY_PERSISTENT_ENABLED, false) }
        verify { editor.putInt(UsageViewModel.KEY_REFRESH_INTERVAL, 30) }
        verify { editor.putString(UsageViewModel.KEY_RESET_DISPLAY, "DATE") }
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
            prefs,
            mockk(relaxed = true),
            ioDispatcher = StandardTestDispatcher(testScheduler),
            reschedule = { rescheduled = it },
        )
        vm.updateSettings(vm.settings.value.copy(refreshIntervalMinutes = 30))

        assertEquals(30, rescheduled)
    }

    @Test
    fun `settings cargan valores guardados`() = runTest {
        val prefs = fakePrefs()
        every { prefs.getBoolean(UsageViewModel.KEY_NOTIF_ENABLED, true) } returns false
        every { prefs.getInt(UsageViewModel.KEY_WEEKLY_ALERT, 80) } returns 65
        every { prefs.getInt(UsageViewModel.KEY_WEEKLY_CRITICAL, 95) } returns 88
        every { prefs.getInt(UsageViewModel.KEY_SESSION_ALERT, 80) } returns 55
        every { prefs.getInt(UsageViewModel.KEY_SESSION_CRITICAL, 95) } returns 82

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
        every { prefs.getString(UsageViewModel.KEY_RESET_DISPLAY, null) } returns "DATE"

        val vm = buildVm(prefs, mockk(relaxed = true))
        assertEquals(ResetDisplayMode.DATE, vm.settings.value.resetDisplayMode)
    }

    @Test
    fun `modo de reset invalido cae a COUNTDOWN`() = runTest {
        val prefs = fakePrefs()
        every { prefs.getString(UsageViewModel.KEY_RESET_DISPLAY, null) } returns "NoExiste"

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
        verify { editor.putString(UsageViewModel.KEY_THEME, "Emerald") }
    }

    @Test
    fun `tema guardado se carga al iniciar`() = runTest {
        val prefs = fakePrefs()
        every { prefs.getString(UsageViewModel.KEY_THEME, null) } returns "Rose"

        val vm = buildVm(prefs, mockk(relaxed = true))
        assertEquals(AppTheme.Rose, vm.theme.value)
    }

    @Test
    fun `tema invalido en prefs cae a Sistema`() = runTest {
        val prefs = fakePrefs()
        every { prefs.getString(UsageViewModel.KEY_THEME, null) } returns "NoExiste"

        val vm = buildVm(prefs, mockk(relaxed = true))
        assertEquals(AppTheme.System, vm.theme.value)
    }

    @Test
    fun `idioma por defecto es Sistema`() = runTest {
        val vm = buildVm(fakePrefs(), mockk(relaxed = true))
        assertEquals(AppLanguage.System, vm.language.value)
    }

    @Test
    fun `updateLanguage persiste y actualiza`() = runTest {
        val prefs = fakePrefs()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putString(any(), any()) } returns editor
        every { prefs.edit() } returns editor

        val vm = buildVm(prefs, mockk(relaxed = true))
        vm.updateLanguage(AppLanguage.English)

        assertEquals(AppLanguage.English, vm.language.value)
        verify { editor.putString(UsageViewModel.KEY_LANGUAGE, "English") }
    }

    @Test
    fun `idioma guardado se carga al iniciar`() = runTest {
        val prefs = fakePrefs()
        every { prefs.getString(UsageViewModel.KEY_LANGUAGE, null) } returns "Spanish"

        val vm = buildVm(prefs, mockk(relaxed = true))
        assertEquals(AppLanguage.Spanish, vm.language.value)
    }
}
