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
        if (!history.toString().contains("relaxed")) {
            // no-op
        }
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

        verify { history.record(85.0, 92.0, any()) }
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

    // ─── Alerta temprana de ritmo (Feature A, REQ-001..003) ───

    /** Editor mockk que registra los valores escritos, para verificar el guard por período. */
    private fun editorRecording(written: MutableMap<String, Any?>): SharedPreferences.Editor {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putLong(any(), any()) } answers {
            written[firstArg()] = secondArg()
            editor
        }
        return editor
    }

    // ─── Multi-cuenta (Feature A lote 2, REQ-101..107) ───

    /** Prefs con una lista de cuentas JSON y la activa. */
    private fun prefsWithAccounts(
        accountsJson: String,
        activeId: String?,
        legacyApiKey: String? = null,
    ): SharedPreferences {
        val prefs = prefsWith(authSource = AuthSource.API_KEY.name, apiKey = legacyApiKey)
        every { prefs.getString(AccountStore.KEY_ACCOUNTS, null) } returns accountsJson
        every { prefs.getString(AccountStore.KEY_ACTIVE_ID, null) } returns activeId
        return prefs
    }

    private fun accountsJson(vararg triples: Triple<String, String, String>): String {
        val arr = org.json.JSONArray()
        triples.forEach { (id, label, key) ->
            arr.put(org.json.JSONObject().put("id", id).put("label", label).put("apiKey", key))
        }
        return arr.toString()
    }

    @Test
    fun `usa la api key de la cuenta activa si hay lista de cuentas`() = runTest {
        val prefs = prefsWithAccounts(
            accountsJson(Triple("a1", "Personal", "key-a1"), Triple("a2", "Trabajo", "key-a2")),
            activeId = "a2",
        )
        val fetcher = mockk<UsageScraper>()
        every { fetcher.fetchUsage("key-a2") } returns sampleData()
        val calls = mutableListOf<String>()
        val repo = buildRepo(prefs, fetcher, calls)

        val result = repo.refreshAndPropagate()

        assertTrue(result.isSuccess)
        verify { fetcher.fetchUsage("key-a2") }
    }

    @Test
    fun `sin lista de cuentas usa la api key legacy`() = runTest {
        val prefs = prefsWithAccounts("[]", activeId = null, legacyApiKey = "sk-legacy")
        val fetcher = mockk<UsageScraper>()
        every { fetcher.fetchUsage("sk-legacy") } returns sampleData()
        val calls = mutableListOf<String>()
        val repo = buildRepo(prefs, fetcher, calls)

        val result = repo.refreshAndPropagate()

        assertTrue(result.isSuccess)
        verify { fetcher.fetchUsage("sk-legacy") }
    }

    @Test
    fun `sin cuenta activa ni legacy produce NoAuth`() = runTest {
        val prefs = prefsWithAccounts("[]", activeId = null, legacyApiKey = null)
        val fetcher = mockk<UsageScraper>()
        val calls = mutableListOf<String>()
        val repo = buildRepo(prefs, fetcher, calls)

        val result = repo.refreshAndPropagate()

        assertEquals(UsageError.NoAuth, result.exceptionOrNull())
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `el historico se escribe en el store de la cuenta activa`() = runTest {
        val prefs = prefsWithAccounts(
            accountsJson(Triple("a1", "Personal", "key-a1")),
            activeId = "a1",
        )
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { prefs.edit() } returns editor
        val fetcher = mockk<UsageScraper>()
        every { fetcher.fetchUsage("key-a1") } returns sampleData()
        // El repo registra el snapshot en el store inyectado (resuelto por
        // cuenta activa en el VM/capa de DI).
        val historyMock = mockk<UsageHistoryStore>(relaxed = true)
        every { historyMock.load() } returns emptyList()
        val calls = mutableListOf<String>()
        val repo = buildRepo(prefs, fetcher, calls, historyMock)

        repo.refreshAndPropagate()

        verify { historyMock.record(any(), any(), any()) }
    }

    @Test
    fun `proyeccion sobre 100 dispara alerta de ritmo una vez por periodo`() = runTest {
        val prefs = prefsWith()
        // Ancla semanal real que usará el repo (fallback: próximo domingo 21:00 CLT).
        val nowReal = System.currentTimeMillis()
        val anchor = fallbackResetAnchor(HistoryPeriod.WEEK, nowReal)!!
        val start = anchor - HistoryPeriod.WEEK.durationMillis
        // Subida fuerte dentro del período: proyecta > 100 al fin del período.
        val snaps = listOf(
            UsageSnapshot(start, sessionPercent = 0.0, weeklyPercent = 0.0),
            UsageSnapshot(start + 1000, sessionPercent = 90.0, weeklyPercent = 90.0),
        )
        val history = mockk<UsageHistoryStore>()
        every { history.load() } returns snaps
        every { history.record(any(), any(), any()) } returns snaps
        every { history.clear() } returns Unit
        val written = mutableMapOf<String, Any?>()
        every { prefs.edit() } returns editorRecording(written)

        val fetcher = mockk<UsageScraper>()
        every { fetcher.fetchUsage("sk-test") } returns sampleData().copy(
            sessionPercent = 10.0,
            weeklyPercent = 10.0,
        )
        val calls = mutableListOf<String>()
        val repo = buildRepo(prefs, fetcher, calls, history)

        val result = repo.refreshAndPropagate()

        assertTrue(result.isSuccess)
        assertTrue("alert" in calls)
        // Guard: guarda el start del período semanal notificado.
        assertEquals(start, written[PrefsKeys.LAST_PACE_PERIOD_WEEK])
    }

    // ─── Recordatorio proactivo de cookie (issue #62) ───

    @Test
    fun `cookieExpiryStatus con API key devuelve OK sin molestar`() = runTest {
        val prefs = prefsWith() // authSource = API_KEY
        val repo = buildRepo(prefs, mockk<UsageScraper>(), mutableListOf())
        val status = repo.cookieExpiryStatus()
        assertEquals(CookieExpiry.Status.OK, status.status)
    }

    @Test
    fun `cookieExpiryStatus sin renovacion conocida devuelve EXPIRED`() = runTest {
        val prefs = prefsWith(authSource = AuthSource.COOKIE.name, apiKey = null, cookie = "cookie")
        every { prefs.getLong(PrefsKeys.COOKIE_RENEWED_AT, 0L) } returns 0L
        val repo = buildRepo(prefs, mockk<UsageScraper>(), mutableListOf())
        val status = repo.cookieExpiryStatus()
        assertEquals(CookieExpiry.Status.EXPIRED, status.status)
    }

    @Test
    fun `cookieExpiryStatus con renovacion reciente devuelve OK`() = runTest {
        val prefs = prefsWith(authSource = AuthSource.COOKIE.name, apiKey = null, cookie = "cookie")
        every { prefs.getLong(PrefsKeys.COOKIE_RENEWED_AT, 0L) } returns System.currentTimeMillis()
        val repo = buildRepo(prefs, mockk<UsageScraper>(), mutableListOf())
        val status = repo.cookieExpiryStatus()
        assertEquals(CookieExpiry.Status.OK, status.status)
    }

    @Test
    fun `recordCookieRenewal persiste el timestamp`() = runTest {
        val prefs = prefsWith()
        val written = mutableMapOf<String, Any?>()
        every { prefs.edit() } returns editorRecording(written)
        val repo = buildRepo(prefs, mockk<UsageScraper>(), mutableListOf())

        repo.recordCookieRenewal()

        assertTrue(written.containsKey(PrefsKeys.COOKIE_RENEWED_AT))
    }

    // ─── Notificación proactiva de reset de sesión (issue #57) ───

    @Test
    fun `refresh reprograma el aviso de reset de sesion con resetAt y percent`() = runTest {
        val prefs = prefsWith()
        val fetcher = mockk<UsageScraper>()
        val data = sampleData() // sessionPercent 85, sessionResetAt 2026-08-08T18:00:00Z
        every { fetcher.fetchUsage("sk-test") } returns data
        val calls = mutableListOf<String>()
        val repo = buildRepo(prefs, fetcher, calls)
        val scheduled = mutableListOf<Pair<Instant?, Double>>()
        repo.connectSessionResetScheduler { _, resetAt, percent -> scheduled += resetAt to percent }

        repo.refreshAndPropagate()

        assertEquals(1, scheduled.size)
        assertEquals(data.sessionResetAt, scheduled[0].first)
        assertEquals(85.0, scheduled[0].second, 0.001)
    }
}
