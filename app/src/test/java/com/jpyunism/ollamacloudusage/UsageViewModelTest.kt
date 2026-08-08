package com.jpyunism.ollamacloudusage

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
    fun `sin cookie queda en Idle`() = runTest {
        val vm = buildVm(fakePrefs(), mockk(relaxed = true))
        assertEquals(UiState.Idle, vm.uiState.value)
        assertTrue(!vm.hasCookie())
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
    fun `clearCookie vuelve a Idle`() = runTest {
        val scraper = mockk<UsageScraper>()
        every { scraper.fetchUsage(any()) } returns sampleData()

        val prefs = fakePrefs()
        every { prefs.contains(UsageViewModel.KEY_COOKIE) } returns true
        every { prefs.getString(UsageViewModel.KEY_COOKIE, null) } returns "cookie"

        val vm = buildVm(prefs, scraper)
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value is UiState.Success)

        vm.clearCookie()
        assertEquals(UiState.Idle, vm.uiState.value)
    }
}
