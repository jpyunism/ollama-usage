package com.jpyunism.ollamacloudusage

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class UsageRepositoryTest {

    private fun prefsWith(
        authSource: String = AuthSource.API_KEY.name,
        apiKey: String? = "sk-test",
        cookie: String? = null,
    ): SharedPreferences {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getString(PrefsKeys.AUTH_SOURCE, null) } returns authSource
        every { prefs.getString(PrefsKeys.API_KEY, null) } returns apiKey
        every { prefs.getString(PrefsKeys.COOKIE, null) } returns cookie
        every { prefs.getBoolean(PrefsKeys.NOTIF_ENABLED, true) } returns true
        every { prefs.getBoolean(PrefsKeys.PERSISTENT_ENABLED, true) } returns true
        every { prefs.getInt(PrefsKeys.WEEKLY_ALERT, 80) } returns 80
        every { prefs.getInt(PrefsKeys.WEEKLY_CRITICAL, 95) } returns 95
        every { prefs.getInt(PrefsKeys.SESSION_ALERT, 80) } returns 80
        every { prefs.getInt(PrefsKeys.SESSION_CRITICAL, 95) } returns 95
        every { prefs.getInt(PrefsKeys.LAST_NOTIFIED_WEEKLY, -1) } returns -1
        every { prefs.getInt(PrefsKeys.LAST_NOTIFIED_SESSION, -1) } returns -1
        every { prefs.edit() } returns mockk(relaxed = true)
        return prefs
    }

    private fun sampleData() = UsageData(
        sessionPercent = 85.0,
        weeklyPercent = 92.0,
        sessionResetAt = Instant.parse("2026-08-08T18:00:00Z"),
        weeklyResetAt = Instant.parse("2026-08-09T21:00:00Z"),
        sessionModels = listOf(ModelUsage("deepseek-v4-flash:0731", 78, 100.0)),
        weeklyModels = listOf(ModelUsage("qwen3.5:397b", 116, 6.5)),
        plan = "pro",
    )

    /** Repo con sinks fake: registra los side-effects en [calls] en vez de tocar Android. */
    private fun buildRepo(
        prefs: SharedPreferences,
        fetcher: UsageScraper,
        calls: MutableList<String>,
        history: UsageHistoryStore = mockk(relaxed = true),
    ): UsageRepository {
        every { history.load() } returns emptyList()
        return UsageRepository(
            context = mockk<Context>(relaxed = true),
            prefs = prefs,
            scraper = fetcher,
            apiScraper = fetcher,
            historyStore = history,
            widgetSaver = { _, _ -> calls += "widget" },
            widgetUpdater = { _ -> calls += "widgetUpdate" },
            persistentShower = { _, _ -> calls += "persistent" },
            persistentHider = { _ -> calls += "persistentHide" },
            alertNotifier = { _, _, _ -> calls += "alert" },
        )
    }

    @Test
    fun `refresh exitoso con API key cruza umbral y notifica alertas`() = runTest {
        val prefs = prefsWith()
        val fetcher = mockk<UsageScraper>()
        every { fetcher.fetchUsage("sk-test") } returns sampleData()
        val calls = mutableListOf<String>()
        val repo = buildRepo(prefs, fetcher, calls)

        val result = repo.refreshAndPropagate()

        assertTrue(result.isSuccess)
        assertEquals(92.0, result.getOrNull()!!.weeklyPercent, 0.001)
        // REQ-003: el pipeline completo corre siempre (widget + persistente + alertas).
        assertTrue("widget" in calls)
        assertTrue("persistent" in calls)
        assertTrue("alert" in calls)
    }

    @Test
    fun `refresh bajo umbral no notifica alertas pero si widget y persistente`() = runTest {
        val prefs = prefsWith()
        val fetcher = mockk<UsageScraper>()
        every { fetcher.fetchUsage("sk-test") } returns sampleData().copy(
            sessionPercent = 12.0,
            weeklyPercent = 30.0,
        )
        val calls = mutableListOf<String>()
        val repo = buildRepo(prefs, fetcher, calls)

        val result = repo.refreshAndPropagate()

        assertTrue(result.isSuccess)
        assertTrue("widget" in calls)
        assertTrue("persistent" in calls)
        assertFalse("alert" in calls)
    }

    @Test
    fun `cookie expirada produce UsageError CookieExpired sin side-effects`() = runTest {
        val prefs = prefsWith(authSource = AuthSource.COOKIE.name, apiKey = null, cookie = "cookie")
        val fetcher = mockk<UsageScraper>()
        every { fetcher.fetchUsage("cookie") } throws CookieExpiredException()
        val calls = mutableListOf<String>()
        val repo = buildRepo(prefs, fetcher, calls)

        val result = repo.refreshAndPropagate()

        assertEquals(UsageError.CookieExpired, result.exceptionOrNull())
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `registra historico y last_updated en refresh exitoso`() = runTest {
        val prefs = prefsWith()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { prefs.edit() } returns editor
        val fetcher = mockk<UsageScraper>()
        every { fetcher.fetchUsage("sk-test") } returns sampleData()
        val history = mockk<UsageHistoryStore>(relaxed = true)
        every { history.load() } returns emptyList()
        val calls = mutableListOf<String>()
        val repo = buildRepo(prefs, fetcher, calls, history)

        repo.refreshAndPropagate()

        verify { history.record(85.0, 92.0) }
        verify { editor.putLong(PrefsKeys.LAST_UPDATED, any()) }
    }

    @Test
    fun `sin auth produce NoAuth sin side-effects`() = runTest {
        val prefs = prefsWith(apiKey = null, cookie = null)
        val fetcher = mockk<UsageScraper>()
        val calls = mutableListOf<String>()
        val repo = buildRepo(prefs, fetcher, calls)

        val result = repo.refreshAndPropagate()

        assertEquals(UsageError.NoAuth, result.exceptionOrNull())
        assertTrue(calls.isEmpty())
    }
}
