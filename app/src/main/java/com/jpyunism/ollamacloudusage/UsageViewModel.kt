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
    ) : UiState

    /** Error tipado; la UI lo mapea a un string con resources. */
    data class Error(val error: UsageError) : UiState
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
) {
    companion object {
        const val MIN_THRESHOLD = 50
        const val MAX_THRESHOLD = 99
    }
}

/** Histórico de consumo acumulado localmente + reset semanal conocido. */
data class HistoryState(
    val snapshots: List<UsageSnapshot> = emptyList(),
    val weeklyResetAt: Instant? = null,
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
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

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
        _authSource.value = AuthSource.COOKIE
        _showAuthSetup.value = false
        refresh()
    }

    /** Abre el WebView de login de ollama.com para capturar la cookie. */
    fun openCookieWebView() {
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
     */
    fun refresh() {
        if (!repository.hasAuth()) {
            _uiState.value = UiState.Idle
            return
        }
        refreshJob?.cancel()
        _uiState.value = UiState.Loading
        refreshJob = viewModelScope.launch {
            val result = repository.refreshAndPropagate()
            _uiState.value = result.fold(
                onSuccess = { data ->
                    // El pipeline ya guardó widget, notif e histórico; la UI
                    // recarga los snapshots desde el store.
                    _history.value = HistoryState(
                        snapshots = repository.historySnapshots(),
                        weeklyResetAt = data.weeklyResetAt,
                    )
                    UiState.Success(data, cookieStored = true, lastUpdated = repository.lastUpdated())
                },
                onFailure = { e -> UiState.Error(e as? UsageError ?: UsageError.Network(e.message ?: "")) },
            )
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
            .apply()
        // Si cambió la frecuencia, reprograma el worker en segundo plano.
        if (s.refreshIntervalMinutes != previous.refreshIntervalMinutes) {
            reschedule(s.refreshIntervalMinutes)
        }
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
                        startUpdateDownload = { info -> UpdaterService.start(app, info.downloadUrl) },
                        onLanguageChange = { LocaleHelper.apply(app, it) },
                    ) as T
                }
            }
    }
}
