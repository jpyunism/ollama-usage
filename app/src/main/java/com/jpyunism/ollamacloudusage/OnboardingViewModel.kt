package com.jpyunism.ollamacloudusage

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del flujo de onboarding guiado (issue #61, spec 01).
 *
 * Mantiene el estado del flujo de 3 pasos (welcome / method / validate) y
 * delega en [OllamaApiKeyValidator] la verificacion de la API key.
 *
 * No toca Context directamente: recibe las dependencias por constructor
 * (factory inyectable). Cuando completa el flujo (exitoso o salteado)
 * persiste el flag `onboarding_completed` para que `MainActivity` deje
 * de mostrar la pantalla de onboarding en el siguiente arranque.
 *
 * Decisiones:
 *  - El estado es un unico [State] inmutable con todos los campos (no
 *    varios StateFlows); la spec describe "estado" como un solo snapshot.
 *  - La navegacion entre pasos la maneja la UI (HorizontalPager); el VM
 *    expone `step` que la UI observa.
 *  - Cuando el usuario completa el flujo (skip o exito), el VM expone
 *    `completed=true` y la UI navega al MainScreen.
 */
class OnboardingViewModel(
    private val prefs: SharedPreferences,
    private val validator: OllamaApiKeyValidator,
    private val onboardingPrefs: OnboardingPrefs = OnboardingPrefs(prefs),
) : ViewModel() {

    enum class Step { Welcome, Method, Validate }

    /** Metodo elegido por el usuario en el paso 2. */
    enum class Method { None, WebView, ApiKey }

    /** Estado de validacion cuando se eligio API key. */
    sealed interface ValidationState {
        data object Idle : ValidationState
        data object InProgress : ValidationState
        data object Success : ValidationState
        data class Failed(val reason: FailureReason) : ValidationState
    }

    enum class FailureReason { Invalid, Inconclusive }

    data class State(
        val step: Step = Step.Welcome,
        val method: Method = Method.None,
        val apiKeyInput: String = "",
        val validation: ValidationState = ValidationState.Idle,
        val completed: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    init {
        // Si por algun motivo ya estaba completado (rotacion tras cerrar el
        // flujo), salimos al Main sin volver a mostrar las pantallas.
        if (onboardingPrefs.isCompleted()) {
            _state.update { it.copy(completed = true) }
        }
    }

    // ── Navegacion entre pasos ──────────────────────────────────────────

    fun goToStep(step: Step) {
        _state.update { it.copy(step = step, validation = ValidationState.Idle) }
    }

    fun next() {
        val target = when (_state.value.step) {
            Step.Welcome -> Step.Method
            Step.Method -> Step.Validate
            Step.Validate -> Step.Validate
        }
        _state.update { it.copy(step = target, validation = ValidationState.Idle) }
    }

    fun back() {
        val target = when (_state.value.step) {
            Step.Welcome -> Step.Welcome
            Step.Method -> Step.Welcome
            Step.Validate -> Step.Method
        }
        _state.update { it.copy(step = target, validation = ValidationState.Idle) }
    }

    // ── Seleccion de metodo (paso 2) ────────────────────────────────────

    fun pickMethod(method: Method) {
        _state.update { it.copy(method = method, validation = ValidationState.Idle) }
    }

    fun updateApiKeyInput(value: String) {
        _state.update { it.copy(apiKeyInput = value, validation = ValidationState.Idle) }
    }

    // ── Validacion de API key (paso 3 con API key) ──────────────────────

    fun validateApiKey() {
        val current = _state.value
        if (current.method != Method.ApiKey) return
        if (current.apiKeyInput.isBlank()) {
            _state.update { it.copy(validation = ValidationState.Failed(FailureReason.Invalid)) }
            return
        }
        _state.update { it.copy(validation = ValidationState.InProgress) }
        viewModelScope.launch {
            val result = validator.validate(current.apiKeyInput)
            val newValidation = when (result) {
                OllamaApiKeyValidator.Result.Valid -> ValidationState.Success
                OllamaApiKeyValidator.Result.Invalid -> ValidationState.Failed(FailureReason.Invalid)
                OllamaApiKeyValidator.Result.Inconclusive -> ValidationState.Failed(FailureReason.Inconclusive)
            }
            _state.update { it.copy(validation = newValidation) }
        }
    }

    /**
     * Persiste la API key en [PrefsKeys.API_KEY] y marca el auth source.
     * Usado por el boton "Continuar" del paso 3 cuando la validacion es OK.
     */
    fun saveApiKeyAndComplete() {
        val current = _state.value
        if (current.method != Method.ApiKey) return
        if (current.validation !is ValidationState.Success) return
        prefs.edit()
            .putString(PrefsKeys.API_KEY, current.apiKeyInput.trim())
            .putString(PrefsKeys.AUTH_SOURCE, AuthSource.API_KEY.name)
            .apply()
        completeOnboarding()
    }

    /**
     * Persiste la cookie capturada por el WebView y marca el auth source.
     * La cookie ya viene validada por [CookieExtractor].
     */
    fun saveCookieAndComplete(cookie: String) {
        prefs.edit()
            .putString(PrefsKeys.COOKIE, cookie.trim())
            .putString(PrefsKeys.AUTH_SOURCE, AuthSource.COOKIE.name)
            .apply()
        completeOnboarding()
    }

    /**
     * Salta el onboarding sin guardar credencial: el usuario llegara a la
     * pantalla principal sin auth configurada y debera usar Configuracion.
     */
    fun skipAndComplete() {
        completeOnboarding()
    }

    private fun completeOnboarding() {
        onboardingPrefs.markCompleted()
        _state.update { it.copy(completed = true) }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val app = context.applicationContext
                    val container = com.jpyunism.ollamacloudusage.di.AppContainer.get(app)
                    return OnboardingViewModel(
                        prefs = container.prefs,
                        validator = OllamaApiKeyValidator(client = container.httpClient),
                    ) as T
                }
            }
    }
}
