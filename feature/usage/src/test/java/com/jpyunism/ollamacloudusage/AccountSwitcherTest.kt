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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Tests del switcher de cuentas (Feature A lote 2, REQ-102/106).
 * El VM persiste la cuenta activa, refresca al cambiar y expone la lista.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountSwitcherTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakePrefs(): SharedPreferences {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getString(PrefsKeys.COOKIE, null) } returns null
        every { prefs.getBoolean(PrefsKeys.NOTIF_ENABLED, true) } returns true
        every { prefs.getBoolean(PrefsKeys.PERSISTENT_ENABLED, true) } returns true
        every { prefs.getInt(PrefsKeys.WEEKLY_ALERT, 80) } returns 80
        every { prefs.getInt(PrefsKeys.WEEKLY_CRITICAL, 95) } returns 95
        every { prefs.getInt(PrefsKeys.SESSION_ALERT, 80) } returns 80
        every { prefs.getInt(PrefsKeys.SESSION_CRITICAL, 95) } returns 95
        every { prefs.getInt(PrefsKeys.REFRESH_INTERVAL, 60) } returns 60
        every { prefs.getString(PrefsKeys.RESET_DISPLAY, null) } returns null
        return prefs
    }

    /**
     * Prefs mock con backing map real para las claves del AccountStore:
     * los writes del store (edit().putString) deben ser visibles en los
     * reads posteriores dentro del mismo test.
     */
    private fun prefsWithAccounts(): SharedPreferences = prefsWithBacking().first

    /** Prefs + mapa de backing para asertar sobre writes reales. */
    private fun prefsWithBacking(): Pair<SharedPreferences, MutableMap<String, String?>> {
        val prefs = fakePrefs()
        val backing = mutableMapOf<String, String?>()
        val arr = org.json.JSONArray()
        arr.put(org.json.JSONObject().put("id", "a1").put("label", "Personal").put("apiKey", "key-1"))
        arr.put(org.json.JSONObject().put("id", "a2").put("label", "Trabajo").put("apiKey", "key-2"))
        backing[AccountStore.KEY_ACCOUNTS] = arr.toString()
        backing[AccountStore.KEY_ACTIVE_ID] = "a1"
        every { prefs.getString(any(), any()) } answers { backing[firstArg()] ?: secondArg() }
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putString(any(), any()) } answers { backing[firstArg()] = secondArg(); editor }
        every { editor.remove(any()) } answers { backing[firstArg()] = null; editor }
        every { prefs.edit() } returns editor
        return prefs to backing
    }

    private fun buildVm(
        prefs: SharedPreferences,
        repository: UsageRepository = mockk(relaxed = true),
        scheduler: kotlinx.coroutines.test.TestCoroutineScheduler,
    ): UsageViewModel {
        // Comparte el scheduler del runTest: si no, los jobs lanzados en Main
        // nunca corren con advanceUntilIdle() (scheduler distinto).
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

    /**
     * Repo mock con refresh exitoso: un mock relaxed devuelve Result(Object)
     * y el fold del VM crashea con ClassCastException al hacer advanceUntilIdle.
     */
    private fun refreshingRepo(): UsageRepository {
        val repo = mockk<UsageRepository>(relaxed = true)
        every { repo.hasAuth() } returns true
        val data = UsageData(
            sessionPercent = 0.0,
            weeklyPercent = 0.0,
            sessionResetAt = null,
            sessionModels = emptyList(),
            weeklyModels = emptyList(),
            plan = "pro",
        )
        coEvery { repo.refreshAndPropagate() } returns Result.success(data)
        return repo
    }

    @Test
    fun `accounts expone la lista y la activa`() = runTest {
        val vm = buildVm(prefsWithAccounts(), scheduler = testScheduler)
        val accounts = vm.accounts.value
        assertEquals(2, accounts.size)
        assertEquals("a1", vm.activeAccountId.value)
    }

    @Test
    fun `switchAccount persiste el id activo`() = runTest {
        val (prefs, backing) = prefsWithBacking()
        val vm = buildVm(prefs, scheduler = testScheduler)

        vm.switchAccount("a2")

        assertEquals("a2", vm.activeAccountId.value)
        assertEquals("a2", backing[AccountStore.KEY_ACTIVE_ID])
    }

    @Test
    fun `switchAccount dispara refresh inmediato`() = runTest {
        val repo = refreshingRepo()
        val vm = buildVm(prefsWithAccounts(), repo, testScheduler)

        vm.switchAccount("a2")
        testScheduler.advanceUntilIdle()

        coVerify(atLeast = 1) { repo.refreshAndPropagate() }
    }

    @Test
    fun `switchAccount con id inexistente es no-op y no refresca`() = runTest {
        val repo = refreshingRepo()
        val vm = buildVm(prefsWithAccounts(), repo, testScheduler)

        vm.switchAccount("no-existe")

        assertEquals("a1", vm.activeAccountId.value)
        coVerify(exactly = 0) { repo.refreshAndPropagate() }
    }

    @Test
    fun `addAccount agrega y activa la nueva cuenta con refresh`() = runTest {
        val prefs = prefsWithAccounts()
        val repo = refreshingRepo()
        val vm = buildVm(prefs, repo, testScheduler)

        val created = vm.addAccount("Nueva", "key-3")
        testScheduler.advanceUntilIdle()

        assertEquals("key-3", created.apiKey)
        assertEquals(created.id, vm.activeAccountId.value)
        assertEquals(3, vm.accounts.value.size)
        coVerify(atLeast = 1) { repo.refreshAndPropagate() }
    }

    @Test
    fun `renameAccount cambia el label`() = runTest {
        val prefs = prefsWithAccounts()
        val vm = buildVm(prefs, scheduler = testScheduler)

        vm.renameAccount("a1", "Personal 2")

        assertEquals("Personal 2", vm.accounts.value.first { it.id == "a1" }.label)
    }

    @Test
    fun `removeAccount elimina y reasigna la activa`() = runTest {
        val prefs = prefsWithAccounts()
        val vm = buildVm(prefs, scheduler = testScheduler)

        vm.removeAccount("a1")

        assertEquals(1, vm.accounts.value.size)
        assertEquals("a2", vm.activeAccountId.value)
        assertTrue(vm.accounts.value.none { it.id == "a1" })
    }
}