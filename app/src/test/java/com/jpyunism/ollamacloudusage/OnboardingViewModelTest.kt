package com.jpyunism.ollamacloudusage

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del VM de onboarding (issue #61).
 * Cubre: navegacion entre pasos, seleccion de metodo, validacion de API key
 * (3 outcomes), persistencia del flag, skip.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakePrefs(): FakePrefs = FakePrefs()

    private fun validatorFor(vararg results: OllamaApiKeyValidator.Result): OllamaApiKeyValidator {
        val v = mockk<OllamaApiKeyValidator>()
        coEvery { v.validate(any()) } returnsMany results.toList()
        return v
    }

    // ── Estado inicial ────────────────────────────────────────────────

    @Test
    fun `estado inicial es Welcome sin metodo seleccionado`() {
        val vm = OnboardingViewModel(fakePrefs(), validatorFor())
        val s = vm.state.value
        assertEquals(OnboardingViewModel.Step.Welcome, s.step)
        assertEquals(OnboardingViewModel.Method.None, s.method)
        assertEquals("", s.apiKeyInput)
        assertTrue(s.validation is OnboardingViewModel.ValidationState.Idle)
        assertFalse(s.completed)
    }

    @Test
    fun `init detecta onboarding ya completado y sale del flujo`() {
        val prefs = fakePrefs().also {
            it.edit().putBoolean(PrefsKeys.ONBOARDING_COMPLETED, true).commit()
        }
        val vm = OnboardingViewModel(prefs, validatorFor())
        assertTrue(vm.state.value.completed)
    }

    // ── Navegacion entre pasos ────────────────────────────────────────

    @Test
    fun `next avanza Welcome a Method y Method a Validate`() {
        val vm = OnboardingViewModel(fakePrefs(), validatorFor())
        vm.next()
        assertEquals(OnboardingViewModel.Step.Method, vm.state.value.step)
        vm.next()
        assertEquals(OnboardingViewModel.Step.Validate, vm.state.value.step)
        // No avanza mas alla de Validate.
        vm.next()
        assertEquals(OnboardingViewModel.Step.Validate, vm.state.value.step)
    }

    @Test
    fun `back retrocede Method a Welcome y Validate a Method`() {
        val vm = OnboardingViewModel(fakePrefs(), validatorFor())
        vm.goToStep(OnboardingViewModel.Step.Validate)
        vm.back()
        assertEquals(OnboardingViewModel.Step.Method, vm.state.value.step)
        vm.back()
        assertEquals(OnboardingViewModel.Step.Welcome, vm.state.value.step)
        // No retrocede mas alla de Welcome.
        vm.back()
        assertEquals(OnboardingViewModel.Step.Welcome, vm.state.value.step)
    }

    @Test
    fun `goToStep cambia directamente al paso solicitado`() {
        val vm = OnboardingViewModel(fakePrefs(), validatorFor())
        vm.goToStep(OnboardingViewModel.Step.Validate)
        assertEquals(OnboardingViewModel.Step.Validate, vm.state.value.step)
    }

    // ── Seleccion de metodo ───────────────────────────────────────────

    @Test
    fun `pickMethod WebView registra el metodo elegido`() {
        val vm = OnboardingViewModel(fakePrefs(), validatorFor())
        vm.pickMethod(OnboardingViewModel.Method.WebView)
        assertEquals(OnboardingViewModel.Method.WebView, vm.state.value.method)
    }

    @Test
    fun `pickMethod ApiKey registra el metodo y permite tipear`() {
        val vm = OnboardingViewModel(fakePrefs(), validatorFor())
        vm.pickMethod(OnboardingViewModel.Method.ApiKey)
        vm.updateApiKeyInput("abc-123")
        val s = vm.state.value
        assertEquals(OnboardingViewModel.Method.ApiKey, s.method)
        assertEquals("abc-123", s.apiKeyInput)
    }

    // ── Validacion de API key ─────────────────────────────────────────

    @Test
    fun `validateApiKey con key vacia marca Failed Invalid sin llamar al validator`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val validator = mockk<OllamaApiKeyValidator>()
        coEvery { validator.validate(any()) } returns OllamaApiKeyValidator.Result.Valid
        val vm = OnboardingViewModel(fakePrefs(), validator)
        vm.pickMethod(OnboardingViewModel.Method.ApiKey)
        vm.updateApiKeyInput("   ") // solo espacios -> blank

        vm.validateApiKey()

        val v = vm.state.value.validation
        assertTrue(v is OnboardingViewModel.ValidationState.Failed)
        assertEquals(OnboardingViewModel.FailureReason.Invalid, (v as OnboardingViewModel.ValidationState.Failed).reason)
        // Validar que NO se llamo al validator.
        io.mockk.coVerify(exactly = 0) { validator.validate(any()) }
    }

    @Test
    fun `validateApiKey con key no blank transiciona a InProgress y luego Success`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val validator = mockk<OllamaApiKeyValidator>()
        coEvery { validator.validate("good-key") } returns OllamaApiKeyValidator.Result.Valid
        val vm = OnboardingViewModel(fakePrefs(), validator)
        vm.pickMethod(OnboardingViewModel.Method.ApiKey)
        vm.updateApiKeyInput("good-key")

        vm.validateApiKey()

        val v = vm.state.value.validation
        assertTrue("expected Success, got $v", v is OnboardingViewModel.ValidationState.Success)
    }

    @Test
    fun `validateApiKey con 401 transiciona a Failed Invalid`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val validator = mockk<OllamaApiKeyValidator>()
        coEvery { validator.validate("bad-key") } returns OllamaApiKeyValidator.Result.Invalid
        val vm = OnboardingViewModel(fakePrefs(), validator)
        vm.pickMethod(OnboardingViewModel.Method.ApiKey)
        vm.updateApiKeyInput("bad-key")

        vm.validateApiKey()

        val v = vm.state.value.validation
        assertTrue(v is OnboardingViewModel.ValidationState.Failed)
        assertEquals(
            OnboardingViewModel.FailureReason.Invalid,
            (v as OnboardingViewModel.ValidationState.Failed).reason,
        )
    }

    @Test
    fun `validateApiKey con 500 transiciona a Failed Inconclusive`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val validator = mockk<OllamaApiKeyValidator>()
        coEvery { validator.validate("k") } returns OllamaApiKeyValidator.Result.Inconclusive
        val vm = OnboardingViewModel(fakePrefs(), validator)
        vm.pickMethod(OnboardingViewModel.Method.ApiKey)
        vm.updateApiKeyInput("k")

        vm.validateApiKey()

        val v = vm.state.value.validation
        assertTrue(v is OnboardingViewModel.ValidationState.Failed)
        assertEquals(
            OnboardingViewModel.FailureReason.Inconclusive,
            (v as OnboardingViewModel.ValidationState.Failed).reason,
        )
    }

    @Test
    fun `validateApiKey sin haber elegido ApiKey no hace nada`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val validator = mockk<OllamaApiKeyValidator>()
        coEvery { validator.validate(any()) } returns OllamaApiKeyValidator.Result.Valid
        val vm = OnboardingViewModel(fakePrefs(), validator)
        vm.pickMethod(OnboardingViewModel.Method.WebView)
        vm.updateApiKeyInput("any")

        vm.validateApiKey()

        // Sigue Idle: no llamo al validator y no cambio la validacion.
        assertTrue(vm.state.value.validation is OnboardingViewModel.ValidationState.Idle)
        io.mockk.coVerify(exactly = 0) { validator.validate(any()) }
    }

    // ── Persistencia al completar ─────────────────────────────────────

    @Test
    fun `saveApiKeyAndComplete persiste la key con auth source API_KEY y completa`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val prefs = fakePrefs()
        val validator = validatorFor(OllamaApiKeyValidator.Result.Valid)
        val vm = OnboardingViewModel(prefs, validator)
        vm.pickMethod(OnboardingViewModel.Method.ApiKey)
        vm.updateApiKeyInput("real-key-abc")

        vm.validateApiKey()
        vm.saveApiKeyAndComplete()

        assertEquals("real-key-abc", prefs.map[PrefsKeys.API_KEY])
        assertEquals(AuthSource.API_KEY.name, prefs.map[PrefsKeys.AUTH_SOURCE])
        assertEquals(true, prefs.map[PrefsKeys.ONBOARDING_COMPLETED])
        assertTrue(vm.state.value.completed)
    }

    @Test
    fun `saveApiKeyAndComplete sin validacion exitosa no persiste nada`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val prefs = fakePrefs()
        val validator = validatorFor(OllamaApiKeyValidator.Result.Invalid)
        val vm = OnboardingViewModel(prefs, validator)
        vm.pickMethod(OnboardingViewModel.Method.ApiKey)
        vm.updateApiKeyInput("bad-key")

        vm.validateApiKey()
        vm.saveApiKeyAndComplete() // no-op porque validation != Success

        assertNull(prefs.map[PrefsKeys.API_KEY])
        assertFalse(vm.state.value.completed)
    }

    @Test
    fun `saveCookieAndComplete persiste la cookie con auth source COOKIE y completa`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val prefs = fakePrefs()
        val vm = OnboardingViewModel(prefs, validatorFor())
        vm.pickMethod(OnboardingViewModel.Method.WebView)

        vm.saveCookieAndComplete("aid=abc; __Secure-session=xyz")

        assertEquals(
            "aid=abc; __Secure-session=xyz",
            prefs.map[PrefsKeys.COOKIE],
        )
        assertEquals(AuthSource.COOKIE.name, prefs.map[PrefsKeys.AUTH_SOURCE])
        assertEquals(true, prefs.map[PrefsKeys.ONBOARDING_COMPLETED])
        assertTrue(vm.state.value.completed)
    }

    @Test
    fun `skipAndComplete marca completado sin persistir credenciales`() {
        val prefs = fakePrefs()
        val vm = OnboardingViewModel(prefs, validatorFor())

        vm.skipAndComplete()

        assertEquals(true, prefs.map[PrefsKeys.ONBOARDING_COMPLETED])
        assertNull(prefs.map[PrefsKeys.API_KEY])
        assertNull(prefs.map[PrefsKeys.COOKIE])
        assertNull(prefs.map[PrefsKeys.AUTH_SOURCE])
        assertTrue(vm.state.value.completed)
    }

    // ── Reset de validacion al cambiar input ──────────────────────────

    @Test
    fun `updateApiKeyInput resetea la validacion a Idle`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val validator = mockk<OllamaApiKeyValidator>()
        coEvery { validator.validate("k1") } returns OllamaApiKeyValidator.Result.Invalid
        val vm = OnboardingViewModel(fakePrefs(), validator)
        vm.pickMethod(OnboardingViewModel.Method.ApiKey)
        vm.updateApiKeyInput("k1")
        vm.validateApiKey()
        assertTrue(vm.state.value.validation is OnboardingViewModel.ValidationState.Failed)

        vm.updateApiKeyInput("k2")
        assertTrue(vm.state.value.validation is OnboardingViewModel.ValidationState.Idle)
    }

    // ── FakePrefs (igual patron que AccountStoreTest) ─────────────────

    private class FakePrefs : android.content.SharedPreferences {
        val map = mutableMapOf<String, Any?>()

        override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValue: MutableSet<String>?): MutableSet<String>? =
            map[key] as? MutableSet<String> ?: defValue
        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun edit(): android.content.SharedPreferences.Editor = object : android.content.SharedPreferences.Editor {
            override fun putString(key: String, value: String?): android.content.SharedPreferences.Editor {
                map[key] = value; return this
            }
            override fun putInt(key: String, value: Int): android.content.SharedPreferences.Editor { map[key] = value; return this }
            override fun putLong(key: String, value: Long): android.content.SharedPreferences.Editor { map[key] = value; return this }
            override fun putBoolean(key: String, value: Boolean): android.content.SharedPreferences.Editor {
                map[key] = value; return this
            }
            override fun putFloat(key: String, value: Float): android.content.SharedPreferences.Editor { map[key] = value; return this }
            override fun putStringSet(key: String, values: MutableSet<String>?): android.content.SharedPreferences.Editor {
                map[key] = values; return this
            }
            override fun remove(key: String): android.content.SharedPreferences.Editor {
                map.remove(key); return this
            }
            override fun clear(): android.content.SharedPreferences.Editor {
                map.clear(); return this
            }
            override fun commit(): Boolean = true
            override fun apply() {}
        }
    }
}
