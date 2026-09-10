package com.jpyunism.ollamacloudusage

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jpyunism.ollamacloudusage.PrefsKeys
import com.jpyunism.ollamacloudusage.UpdateRepository
import com.jpyunism.ollamacloudusage.UsageRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

sealed interface UiState {
    data object Idle : UiState
    data object Loading : UiState
    data class Success(
        val data: UsageData,
        val cookieStored: Boolean,
        val lastUpdated: Long? = null,
        /** Proyeccion de agotamiento semanal (issue #54); null si hay datos insuficientes. */
        val weeklyProjection: ProjectionEngine.Result? = null,
        /** Proyeccion de agotamiento de sesion (issue #54); null si hay datos insuficientes. */
        val sessionProjection: ProjectionEngine.Result? = null,
    ) : UiState

    /** Error tipado; la UI lo mapea a un string con resources. */
    data class Error(val error: UsageError) : UiState
}

/**
 * Estado de la validacion en vivo de una API key (issue #63).
 * La UI lo mapea a strings/colores; [Inconclusive] degrada graceful offline.
 */
sealed interface ApiKeyValidation {
    data object Idle : ApiKeyValidation
    data object InProgress : ApiKeyValidation
    data object Valid : ApiKeyValidation
    data object Invalid : ApiKeyValidation
    data object Inconclusive : ApiKeyValidation
}

/** Configuración de alertas y pantalla de bloqueo. */
data class AlertSettings(
    val notificationsEnabled: Boolean = true,
    val weeklyAlert: Int = 80,
    val weeklyCritical: Int = 95,
    val sessionAlert: Int = 80,
    val sessionCritical: Int = 95,
    val persistentEnabled: Boolean = true,
    val refreshIntervalMinutes: Int = PrefsKeys.DEFAULT_REFRESH_MINUTES,
    val resetDisplayMode: ResetDisplayMode = ResetDisplayMode.COUNTDOWN,
    // Resumen diario programado (Feature B lote 2)
    val dailySummaryEnabled: Boolean = false,
    val dailySummaryHour: Int = 21,
    val dailySummaryMinute: Int = 0,
) {
    companion object {
        const val MIN_THRESHOLD = 50
        const val MAX_THRESHOLD = 99
    }
}

/** Histórico de consumo acumulado localmente + resets conocidos. */
data class HistoryState(
    val snapshots: List<UsageSnapshot> = emptyList(),
    val weeklyResetAt: Instant? = null,
    val sessionResetAt: Instant? = null,
)

/**
 * ViewModel de la pantalla principal. Delgado y sin Context: no contiene
 * lógica de negocio — delega el refresh en [UsageRepository], el
 * update-check en [UpdateRepository] y la persistencia en prefs. El mapeo de
 * errores a strings vive en la UI ([UsageError] → stringResource).
 */
class UsageViewModel(
    private val prefs: SharedPreferences,
    private val repository: UsageRepository,
    private val updateRepository: UpdateRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val reschedule: (Int) -> Unit = {},
    private val startUpdateDownload: (UpdateInfo) -> Unit = {},
    private val onLanguageChange: (AppLanguage) -> Unit = {},
    private val onDailySummaryChanged: () -> Unit = {},
    private val historyStoreProvider: () -> UsageHistoryStore,
    private val apiKeyValidator: OllamaApiKeyValidator = OllamaApiKeyValidator(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    /** Indica un refresh en curso (pull-to-refresh/botón) sin ocultar contenido. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _authSource = MutableStateFlow(repository.authSource())
    val authSource: StateFlow<AuthSource> = _authSource

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AlertSettings> = _settings

    private val _theme = MutableStateFlow(loadTheme())
    val theme: StateFlow<AppTheme> = _theme

    private val _darkMode = MutableStateFlow(loadDarkMode())
    val darkMode: StateFlow<AppDarkMode> = _darkMode

    private val _language = MutableStateFlow(loadLanguage())
    val language: StateFlow<AppLanguage> = _language

    private val _update = MutableStateFlow<UpdateInfo?>(null)
    val update: StateFlow<UpdateInfo?> = _update

    private val _checkingUpdate = MutableStateFlow(false)
    val checkingUpdate: StateFlow<Boolean> = _checkingUpdate

    private val _checkResult = MutableStateFlow<UpdateCheckOutcome?>(null)
    val checkResult: StateFlow<UpdateCheckOutcome?> = _checkResult

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download: StateFlow<DownloadState> = _download

    /** Histórico de consumo acumulado localmente (snapshots + reset semanal). */
    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<HistoryState> = _history

    private val _showAuthSetup = MutableStateFlow(false)
    val showAuthSetup: StateFlow<Boolean> = _showAuthSetup

    private val _showCookieWebView = MutableStateFlow(false)
    val showCookieWebView: StateFlow<Boolean> = _showCookieWebView

    /** Estado de la validacion en vivo de la API key pegada (issue #63). */
    private val _apiKeyValidation = MutableStateFlow<ApiKeyValidation>(ApiKeyValidation.Idle)
    val apiKeyValidation: StateFlow<ApiKeyValidation> = _apiKeyValidation

    private var apiKeyValidationJob: Job? = null

    /** Estado de expiración de la cookie (issue #62), para el banner. */
    private val _cookieExpiry = MutableStateFlow(repository.cookieExpiryStatus())
    val cookieExpiry: StateFlow<CookieExpiry.Result> = _cookieExpiry

    // ── Multi-cuenta (Feature A lote 2, issue #25) ──
    private val accountStore = AccountStore(prefs)

    /** Lista de cuentas (API keys); vacía si el usuario no usa multi-cuenta. */
    private val _accounts = MutableStateFlow(accountStore.list())
    val accounts: StateFlow<List<Account>> = _accounts

    /** Id de la cuenta activa; null si no hay lista de cuentas. */
    private val _activeAccountId = MutableStateFlow(accountStore.activeId())
    val activeAccountId: StateFlow<String?> = _activeAccountId

    private var refreshJob: Job? = null

    init {
        if (repository.hasAuth()) refresh()
        checkForUpdate()
    }

    /** Guarda la cookie de sesión como método de autenticación. */
    fun saveCookie(cookie: String) {
        prefs.edit()
            .putString(PrefsKeys.COOKIE, cookie.trim())
            .putString(PrefsKeys.AUTH_SOURCE, AuthSource.COOKIE.name)
            .apply()
        // Issue #62: registrar la renovación para el recordatorio proactivo.
        repository.recordCookieRenewal()
        _cookieExpiry.value = repository.cookieExpiryStatus()
        _authSource.value = AuthSource.COOKIE
        _showAuthSetup.value = false
        refresh()
    }

    /** Abre el WebView de login de ollama.com para capturar la cookie. */
    fun openCookieWebView() {
        _showCookieWebView.value = true
    }

    /** CTA "Renovar ahora" (issue #62): abre el WebView de login directo. */
    fun renewCookie() {
        _showCookieWebView.value = true
    }

    /** Cierra el WebView sin guardar nada. */
    fun closeCookieWebView() {
        _showCookieWebView.value = false
    }

    /** Guarda la cookie capturada desde el WebView y cierra el flujo. */
    fun saveCookieFromWebView(cookie: String) {
        _showCookieWebView.value = false
        saveCookie(cookie)
    }

    /** Guarda la API key de Ollama Cloud como método de autenticación. */
    fun saveApiKey(apiKey: String) {
        prefs.edit()
            .putString(PrefsKeys.API_KEY, apiKey.trim())
            .putString(PrefsKeys.AUTH_SOURCE, AuthSource.API_KEY.name)
            .apply()
        _authSource.value = AuthSource.API_KEY
        _showAuthSetup.value = false
        refresh()
    }

    /** Valor del secreto guardado para el método indicado (vacío si no existe). */
    fun currentSecret(source: AuthSource): String = repository.currentSecret(source)

    /**
     * Valida en vivo una API key contra ollama.com (issue #63) ANTES de
     * guardarla. Cancela la validacion previa si el usuario sigue pegando.
     * Degrada a [ApiKeyValidation.Inconclusive] offline (timeout/red).
     */
    fun validateApiKey(apiKey: String) {
        apiKeyValidationJob?.cancel()
        if (apiKey.isBlank()) {
            _apiKeyValidation.value = ApiKeyValidation.Idle
            return
        }
        _apiKeyValidation.value = ApiKeyValidation.InProgress
        apiKeyValidationJob = viewModelScope.launch {
            val result = apiKeyValidator.validate(apiKey)
            _apiKeyValidation.value = when (result) {
                OllamaApiKeyValidator.Result.Valid -> ApiKeyValidation.Valid
                OllamaApiKeyValidator.Result.Invalid -> ApiKeyValidation.Invalid
                OllamaApiKeyValidator.Result.Inconclusive -> ApiKeyValidation.Inconclusive
            }
        }
    }

    /** Resetea la validacion en vivo (p. ej. al cerrar el setup). */
    fun resetApiKeyValidation() {
        apiKeyValidationJob?.cancel()
        _apiKeyValidation.value = ApiKeyValidation.Idle
    }

    /** Abre la pantalla de cambio de acceso sin tocar las credenciales guardadas. */
    fun openAuthSetup() {
        _showAuthSetup.value = true
    }

    /** Vuelve al estado anterior sin guardar nada. */
    fun closeAuthSetup() {
        _showAuthSetup.value = false
    }

    fun clearAuth() {
        prefs.edit()
            .remove(PrefsKeys.COOKIE)
            .remove(PrefsKeys.API_KEY)
            .apply()
        _uiState.value = UiState.Idle
    }

    fun hasAuth(): Boolean = repository.hasAuth()

    /**
     * Refresca el consumo delegando en [UsageRepository]. Cancela el job
     * anterior para que refrescos rápidos no se pisen (REQ-018).
     *
     * Con [fromPull] = true (pull-to-refresh / botón Actualizar) el contenido
     * visible no se reemplaza por [UiState.Loading]: la UI muestra el
     * indicador de pull vía [isRefreshing].
     */
    fun refresh(fromPull: Boolean = false) {
        if (!repository.hasAuth()) {
            _uiState.value = UiState.Idle
            return
        }
        refreshJob?.cancel()
        if (!fromPull) {
            _uiState.value = UiState.Loading
        }
        _isRefreshing.value = true
        refreshJob = viewModelScope.launch {
            try {
                val result = repository.refreshAndPropagate()
                _uiState.value = result.fold(
                    onSuccess = { data ->
                        // El pipeline ya guardó widget, notif e histórico; la UI
                        // recarga los snapshots desde el store.
                        val history = HistoryState(
                            snapshots = repository.historySnapshots(),
                            weeklyResetAt = data.weeklyResetAt
                                ?: repository.detectedWeeklyAnchor()?.let(Instant::ofEpochMilli),
                            sessionResetAt = data.sessionResetAt,
                        )
                        _history.value = history
                        val (weekly, session) = computeProjections(history)
                        UiState.Success(
                            data,
                            cookieStored = true,
                            lastUpdated = repository.lastUpdated(),
                            weeklyProjection = weekly,
                            sessionProjection = session,
                        )
                    },
                    onFailure = { e -> UiState.Error(e as? UsageError ?: UsageError.Network(e.message ?: "")) },
                )
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun updateSettings(s: AlertSettings) {
        val previous = _settings.value
        _settings.value = s
        prefs.edit()
            .putBoolean(PrefsKeys.NOTIF_ENABLED, s.notificationsEnabled)
            .putInt(PrefsKeys.WEEKLY_ALERT, s.weeklyAlert)
            .putInt(PrefsKeys.WEEKLY_CRITICAL, s.weeklyCritical)
            .putInt(PrefsKeys.SESSION_ALERT, s.sessionAlert)
            .putInt(PrefsKeys.SESSION_CRITICAL, s.sessionCritical)
            .putBoolean(PrefsKeys.PERSISTENT_ENABLED, s.persistentEnabled)
            .putInt(PrefsKeys.REFRESH_INTERVAL, s.refreshIntervalMinutes)
            .putString(PrefsKeys.RESET_DISPLAY, s.resetDisplayMode.name)
            .putBoolean(PrefsKeys.DAILY_SUMMARY_ENABLED, s.dailySummaryEnabled)
            .putInt(PrefsKeys.DAILY_SUMMARY_HOUR, s.dailySummaryHour)
            .putInt(PrefsKeys.DAILY_SUMMARY_MINUTE, s.dailySummaryMinute)
            .apply()
        // Si cambió la frecuencia, reprograma el worker en segundo plano.
        if (s.refreshIntervalMinutes != previous.refreshIntervalMinutes) {
            reschedule(s.refreshIntervalMinutes)
        }
    }

    /**
     * Actualiza el resumen diario (Feature B lote 2): persiste y programa el
     * worker (callback inyectado para no referenciar Context en el VM).
     */
    fun updateDailySummary(enabled: Boolean, hour: Int? = null, minute: Int? = null) {
        val current = _settings.value
        val new = current.copy(
            dailySummaryEnabled = enabled,
            dailySummaryHour = hour ?: current.dailySummaryHour,
            dailySummaryMinute = minute ?: current.dailySummaryMinute,
        )
        _settings.value = new
        prefs.edit()
            .putBoolean(PrefsKeys.DAILY_SUMMARY_ENABLED, new.dailySummaryEnabled)
            .putInt(PrefsKeys.DAILY_SUMMARY_HOUR, new.dailySummaryHour)
            .putInt(PrefsKeys.DAILY_SUMMARY_MINUTE, new.dailySummaryMinute)
            .apply()
        onDailySummaryChanged()
    }

    fun updateTheme(theme: AppTheme) {
        _theme.value = theme
        prefs.edit().putString(PrefsKeys.THEME, theme.name).apply()
    }

    /** Cambia el modo claro/oscuro: aplica al instante y lo guarda. */
    fun updateDarkMode(mode: AppDarkMode) {
        _darkMode.value = mode
        prefs.edit().putString(PrefsKeys.DARK_MODE, mode.name).apply()
    }

    /** Cambia el idioma de la UI: aplica al instante y lo guarda. */
    fun updateLanguage(language: AppLanguage) {
        _language.value = language
        prefs.edit().putString(PrefsKeys.LANGUAGE, language.name).apply()
        // Aplicar el locale es responsabilidad de la capa Android (callback
        // inyectado por el factory con el contexto de app): el VM no
        // referencia Context.
        onLanguageChange(language)
    }

    /**
     * Importa snapshots desde un JSON de backup (Feature B) y refresca el
     * StateFlow del histórico. Devuelve el resultado (null = archivo
     * corrupto/inválido, historial sin cambios).
     */
    suspend fun importSnapshots(json: String): Boolean {
        val imported = UsageHistoryStore.parseSnapshots(json)
        if (imported.isEmpty()) return false
        val store = historyStoreProvider()
        val merged = kotlinx.coroutines.withContext(ioDispatcher) {
            store.mergeSnapshots(imported)
        }
        _history.value = _history.value.copy(snapshots = merged)
        return true
    }

    /** JSON de exportación del historial actual (Feature B). */
    fun exportSnapshots(): String = UsageHistoryStore.encodeSnapshots(repository.historySnapshots())

    // ── Multi-cuenta (Feature A lote 2, issue #25) ──

    /**
     * Cambia la cuenta activa: persiste el id y refresca inmediatamente
     * (REQ-102). No-op si el id no existe.
     */
    fun switchAccount(id: String) {
        val store = AccountStore(prefs)
        val before = store.activeId()
        store.setActive(id)
        if (store.activeId() == before) return
        _activeAccountId.value = id
        refresh()
    }

    /** Agrega una cuenta API key; queda activa y dispara refresh (REQ-102/106). */
    fun addAccount(label: String, apiKey: String): Account {
        val created = AccountStore(prefs).add(label, apiKey)
        _accounts.value = AccountStore(prefs).list()
        _activeAccountId.value = created.id
        refresh()
        return created
    }

    /** Renombra una cuenta sin tocar la credencial. */
    fun renameAccount(id: String, label: String) {
        AccountStore(prefs).rename(id, label)
        _accounts.value = AccountStore(prefs).list()
    }

    /** Elimina una cuenta; si era la activa, reasigna y refresca. */
    fun removeAccount(id: String) {
        AccountStore(prefs).remove(id)
        val store = AccountStore(prefs)
        _accounts.value = store.list()
        _activeAccountId.value = store.activeId()
        if (store.activeId() != null) refresh()
    }

    /** Chequea una vez por día si hay release más nuevo (silencioso). */
    fun checkForUpdate() {
        if (!updateRepository.shouldCheck()) return
        viewModelScope.launch {
            val info = withContext(ioDispatcher) { updateRepository.check() }
            updateRepository.markChecked()
            if (info != null) _update.value = info
        }
    }

    /** Descarga e instala la actualización (servicio en primer plano con progreso). */
    fun startUpdateDownload(info: UpdateInfo) {
        _download.value = DownloadState.Downloading(0)
        startUpdateDownload(info)
        viewModelScope.launch {
            UpdaterService.state.collect { state ->
                _download.value = state
            }
        }
    }

    /** Revisa de nuevo aunque no haya pasado el intervalo (botón manual). */
    fun checkForUpdateNow() {
        if (_checkingUpdate.value) return
        _checkingUpdate.value = true
        _checkResult.value = null
        viewModelScope.launch {
            val info = withContext(ioDispatcher) { updateRepository.check() }
            _checkingUpdate.value = false
            _checkResult.value = if (info != null) {
                _update.value = info
                UpdateCheckOutcome.Available(info)
            } else {
                UpdateCheckOutcome.UpToDate
            }
        }
    }

    private fun loadSettings(): AlertSettings = AlertSettings(
        notificationsEnabled = prefs.getBoolean(PrefsKeys.NOTIF_ENABLED, true),
        weeklyAlert = prefs.getInt(PrefsKeys.WEEKLY_ALERT, 80),
        weeklyCritical = prefs.getInt(PrefsKeys.WEEKLY_CRITICAL, 95),
        sessionAlert = prefs.getInt(PrefsKeys.SESSION_ALERT, 80),
        sessionCritical = prefs.getInt(PrefsKeys.SESSION_CRITICAL, 95),
        persistentEnabled = prefs.getBoolean(PrefsKeys.PERSISTENT_ENABLED, true),
        refreshIntervalMinutes = prefs.getInt(PrefsKeys.REFRESH_INTERVAL, PrefsKeys.DEFAULT_REFRESH_MINUTES),
        resetDisplayMode = prefs.getString(PrefsKeys.RESET_DISPLAY, null)
            ?.let { name -> ResetDisplayMode.entries.firstOrNull { it.name == name } }
            ?: ResetDisplayMode.COUNTDOWN,
        dailySummaryEnabled = prefs.getBoolean(PrefsKeys.DAILY_SUMMARY_ENABLED, false),
        dailySummaryHour = prefs.getInt(PrefsKeys.DAILY_SUMMARY_HOUR, 21),
        dailySummaryMinute = prefs.getInt(PrefsKeys.DAILY_SUMMARY_MINUTE, 0),
    )

    private fun loadTheme(): AppTheme =
        prefs.getString(PrefsKeys.THEME, null)
            ?.let { name -> AppTheme.entries.firstOrNull { it.name == name } }
            ?: AppTheme.System

    private fun loadDarkMode(): AppDarkMode =
        prefs.getString(PrefsKeys.DARK_MODE, null)
            ?.let { name -> AppDarkMode.entries.firstOrNull { it.name == name } }
            ?: AppDarkMode.System

    private fun loadLanguage(): AppLanguage =
        prefs.getString(PrefsKeys.LANGUAGE, null)
            ?.let { name -> AppLanguage.entries.firstOrNull { it.name == name } }
            ?: AppLanguage.System

    private fun loadHistory(): HistoryState = HistoryState(
        snapshots = repository.historySnapshots(),
        weeklyResetAt = null,
    )

    /**
     * Calcula las proyecciones de agotamiento (issue #54) para semana y
     * sesion a partir del historico. Devuelve null si hay datos insuficientes
     * (<3 snapshots) o no se puede calcular el ritmo.
     */
    private fun computeProjections(
        history: HistoryState,
    ): Pair<ProjectionEngine.Result?, ProjectionEngine.Result?> {
        val now = Instant.now()
        val weekly = ProjectionEngine.project(
            snapshots = history.snapshots,
            resetAt = history.weeklyResetAt,
            now = now,
            selector = { it.weeklyPercent },
        )
        val session = ProjectionEngine.project(
            snapshots = history.snapshots,
            resetAt = history.sessionResetAt,
            now = now,
            selector = { it.sessionPercent },
        )
        return weekly to session
    }

    /** Versión instalada de la app (para mostrarla en Configuración). */
    val appVersion: String = updateRepository.currentVersion()

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val app = context.applicationContext
                    val container = com.jpyunism.ollamacloudusage.di.AppContainer.get(app)
                    return UsageViewModel(
                        prefs = container.prefs,
                        repository = container.usageRepository,
                        updateRepository = container.updateRepository,
                        reschedule = { UsageScheduler.schedule(app, it) },
                        startUpdateDownload = { info -> UpdaterService.start(app, info.downloadUrl, info.sha256) },
                        onLanguageChange = { LocaleHelper.apply(app, it) },
                        onDailySummaryChanged = { com.jpyunism.ollamacloudusage.DailySummaryWorker.schedule(app) },
                        historyStoreProvider = { container.historyStore },
                        apiKeyValidator = OllamaApiKeyValidator(client = container.httpClient),
                    ) as T
                }
            }
    }
}
