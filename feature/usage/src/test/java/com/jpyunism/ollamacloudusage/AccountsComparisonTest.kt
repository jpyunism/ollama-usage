package com.jpyunism.ollamacloudusage

import android.content.SharedPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la comparativa entre cuentas en el VM (issue #93): fetch por cuenta
 * con errores aislados, publicacion de las filas y no-op con menos de 2 cuentas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountsComparisonTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun prefsWithBacking(): Pair<SharedPreferences, MutableMap<String, String?>> {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        val backing = mutableMapOf<String, String?>()
        val arr = org.json.JSONArray()
        arr.put(org.json.JSONObject().put("id", "a1").put("label", "Personal").put("apiKey", "key-1"))
        arr.put(org.json.JSONObject().put("id", "a2").put("label", "Trabajo").put("apiKey", "key-2"))
        backing[AccountStore.KEY_ACCOUNTS] = arr.toString()
        backing[AccountStore.KEY_ACTIVE_ID] = "a1"
        every { prefs.getString(any(), any()) } answers { backing[firstArg()] ?: secondArg() }
        every { prefs.getBoolean(PrefsKeys.NOTIF_ENABLED, true) } returns true
        every { prefs.getBoolean(PrefsKeys.PERSISTENT_ENABLED, true) } returns true
        every { prefs.getInt(PrefsKeys.WEEKLY_ALERT, 80) } returns 80
        every { prefs.getInt(PrefsKeys.WEEKLY_CRITICAL, 95) } returns 95
        every { prefs.getInt(PrefsKeys.SESSION_ALERT, 80) } returns 80
        every { prefs.getInt(PrefsKeys.SESSION_CRITICAL, 95) } returns 95
        every { prefs.getInt(PrefsKeys.REFRESH_INTERVAL, 60) } returns 60
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putString(any(), any()) } answers { backing[firstArg()] = secondArg(); editor }
        every { editor.remove(any()) } answers { backing[firstArg()] = null; editor }
        every { prefs.edit() } returns editor
        return prefs to backing
    }

    private fun singleAccountPrefs(): SharedPreferences {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        val arr = org.json.JSONArray()
        arr.put(org.json.JSONObject().put("id", "a1").put("label", "Personal").put("apiKey", "key-1"))
        every { prefs.getString(AccountStore.KEY_ACCOUNTS, null) } returns arr.toString()
        every { prefs.getString(AccountStore.KEY_ACTIVE_ID, null) } returns "a1"
        every { prefs.getString(any(), any()) } answers { secondArg() }
        every { prefs.edit() } returns mockk(relaxed = true)
        return prefs
    }

    private fun buildVm(
        prefs: SharedPreferences,
        repository: UsageRepository,
        scheduler: kotlinx.coroutines.test.TestCoroutineScheduler,
    ): UsageViewModel {
        val dispatcher = StandardTestDispatcher(scheduler)
        Dispatchers.setMain(dispatcher)
        return UsageViewModel(
            prefs = prefs,
            repository = repository,
            updateRepository = mockk(relaxed = true),
            ioDispatcher = dispatcher,
            historyStoreProvider = { mockk(relaxed = true) },
            apiKeyValidator = mockk(relaxed = true),
        )
    }

    private fun data(weekly: Double, session: Double) = UsageData(
        sessionPercent = session,
        weeklyPercent = weekly,
        sessionResetAt = null,
        sessionModels = emptyList(),
        weeklyModels = emptyList(),
        plan = "cloud",
    )

    private fun repoWith(
        a1: Result<UsageData>,
        a2: Result<UsageData>,
    ): UsageRepository = mockk<UsageRepository>(relaxed = true).apply {
        every { hasAuth() } returns true
        coEvery { refreshAndPropagate() } returns Result.success(data(0.0, 0.0))
        coEvery { fetchUsageForAccount("a1") } returns a1
        coEvery { fetchUsageForAccount("a2") } returns a2
    }

    @Test
    fun `publica una fila por cuenta con su consumo`() = runTest {
        val repo = repoWith(
            a1 = Result.success(data(41.5, 12.0)),
            a2 = Result.success(data(90.0, 50.0)),
        )
        val vm = buildVm(prefsWithBacking().first, repo, testScheduler)

        testScheduler.advanceUntilIdle()

        val rows = vm.accountUsages.value
        assertEquals(2, rows.size)
        assertEquals(
            AccountUsageState.Success(41.5, 12.0),
            rows.first { it.accountId == "a1" }.state,
        )
        assertEquals(
            AccountUsageState.Success(90.0, 50.0),
            rows.first { it.accountId == "a2" }.state,
        )
        assertTrue(rows.first { it.accountId == "a1" }.isActive)
    }

    @Test
    fun `una key invalida no rompe la comparativa de las demas`() = runTest {
        val repo = repoWith(
            a1 = Result.failure(UsageError.InvalidApiKey),
            a2 = Result.success(data(30.0, 10.0)),
        )
        val vm = buildVm(prefsWithBacking().first, repo, testScheduler)

        testScheduler.advanceUntilIdle()

        val rows = vm.accountUsages.value
        assertEquals(2, rows.size)
        // La cuenta con error queda en Failure; la otra conserva sus datos.
        assertEquals(
            AccountUsageState.Failure(UsageError.InvalidApiKey),
            rows.first { it.accountId == "a1" }.state,
        )
        assertEquals(
            AccountUsageState.Success(30.0, 10.0),
            rows.first { it.accountId == "a2" }.state,
        )
    }

    @Test
    fun `con una sola cuenta la comparativa queda vacia`() = runTest {
        val repo = repoWith(Result.success(data(10.0, 5.0)), Result.success(data(10.0, 5.0)))
        val vm = buildVm(singleAccountPrefs(), repo, testScheduler)

        testScheduler.advanceUntilIdle()

        assertTrue(vm.accountUsages.value.isEmpty())
    }

    @Test
    fun `mientras corre el fetch las filas estan en Loading`() = runTest {
        val repo = repoWith(
            a1 = Result.success(data(41.5, 12.0)),
            a2 = Result.success(data(90.0, 50.0)),
        )
        val vm = buildVm(prefsWithBacking().first, repo, testScheduler)

        // Antes de avanzar el scheduler el fetch no corrio: Loading.
        val rows = vm.accountUsages.value
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.state == AccountUsageState.Loading })

        testScheduler.advanceUntilIdle()
        assertTrue(vm.accountUsages.value.all { it.state !is AccountUsageState.Loading })
    }

    @Test
    fun `switchAccount refresca la comparativa con la nueva activa`() = runTest {
        val repo = repoWith(
            a1 = Result.success(data(41.5, 12.0)),
            a2 = Result.success(data(90.0, 50.0)),
        )
        val vm = buildVm(prefsWithBacking().first, repo, testScheduler)
        testScheduler.advanceUntilIdle()

        vm.switchAccount("a2")
        testScheduler.advanceUntilIdle()

        assertTrue(vm.accountUsages.value.first { it.accountId == "a2" }.isActive)
    }
}
