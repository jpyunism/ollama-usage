package com.jpyunism.ollamacloudusage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState {
    data object Idle : UiState
    data object Loading : UiState
    data class Success(
        val data: UsageData,
        val cookieStored: Boolean,
        val lastUpdated: Long? = null,
    ) : UiState

    data class Error(val message: String) : UiState
}

/** Configuración de alertas y pantalla de bloqueo. */
data class AlertSettings(
    val notificationsEnabled: Boolean = true,
    val weeklyAlert: Int = 80,
    val weeklyCritical: Int = 95,
    val sessionAlert: Int = 80,
    val sessionCritical: Int = 95,
    val persistentEnabled: Boolean = true,
    val refreshIntervalMinutes: Int = 60,
    val resetDisplayMode: ResetDisplayMode = ResetDisplayMode.COUNTDOWN,
) {
    companion object {
        const val MIN_THRESHOLD = 50
        const val MAX_THRESHOLD = 99
    }
}

class UsageViewModel(
    private val prefs: android.content.SharedPreferences,
    private val scraper: UsageScraper,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val reschedule: (Int) -> Unit = {},
    private val context: android.content.Context? = null,
    private val apiScraper: UsageScraper = OllamaApiUsage(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    private val _authSource = MutableStateFlow(loadAuthSource())
    val authSource: StateFlow<AuthSource> = _authSource

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AlertSettings> = _settings

    private val _theme = MutableStateFlow(loadTheme())
    val theme: StateFlow<AppTheme> = _theme

    private val _update = MutableStateFlow<UpdateInfo?>(null)
    val update: StateFlow<UpdateInfo?> = _update

    private val _checkingUpdate = MutableStateFlow(false)
    val checkingUpdate: StateFlow<Boolean> = _checkingUpdate

    private val _checkResult = MutableStateFlow<UpdateCheckOutcome?>(null)
    val checkResult: StateFlow<UpdateCheckOutcome?> = _checkResult

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download: StateFlow<DownloadState> = _download

    private val _showAuthSetup = MutableStateFlow(false)
    val showAuthSetup: StateFlow<Boolean> = _showAuthSetup

    init {
        if (hasAuth()) refresh()
        checkForUpdate()
    }

    /** Guarda la cookie de sesión como método de autenticación. */
    fun saveCookie(cookie: String) {
        prefs.edit()
            .putString(KEY_COOKIE, cookie.trim())
            .putString(KEY_AUTH_SOURCE, AuthSource.COOKIE.name)
            .apply()
        _authSource.value = AuthSource.COOKIE
        _showAuthSetup.value = false
        refresh()
    }

    /** Guarda la API key de Ollama Cloud como método de autenticación. */
    fun saveApiKey(apiKey: String) {
        prefs.edit()
            .putString(KEY_API_KEY, apiKey.trim())
            .putString(KEY_AUTH_SOURCE, AuthSource.API_KEY.name)
            .apply()
        _authSource.value = AuthSource.API_KEY
        _showAuthSetup.value = false
        refresh()
    }

    /** Valor del secreto guardado para el método indicado (vacío si no existe). */
    fun currentSecret(source: AuthSource): String = when (source) {
        AuthSource.COOKIE -> prefs.getString(KEY_COOKIE, null).orEmpty()
        AuthSource.API_KEY -> prefs.getString(KEY_API_KEY, null).orEmpty()
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
            .remove(KEY_COOKIE)
            .remove(KEY_API_KEY)
            .apply()
        _uiState.value = UiState.Idle
    }

    fun hasAuth(): Boolean = when (loadAuthSource()) {
        AuthSource.COOKIE -> prefs.contains(KEY_COOKIE)
        AuthSource.API_KEY -> prefs.contains(KEY_API_KEY)
    }

    fun refresh() {
        val source = loadAuthSource()
        val credential = when (source) {
            AuthSource.COOKIE -> prefs.getString(KEY_COOKIE, null)
            AuthSource.API_KEY -> prefs.getString(KEY_API_KEY, null)
        } ?: run {
            _uiState.value = UiState.Idle
            return
        }
        val fetcher = if (source == AuthSource.API_KEY) apiScraper else scraper
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { fetcher.fetchUsage(credential) }
            }
            _uiState.value = result.fold(
                onSuccess = {
                    val now = System.currentTimeMillis()
                    prefs.edit().putLong(KEY_LAST_UPDATED, now).apply()
                    // Widget del home screen: refleja el refresh manual.
                    context?.let { ctx -> UsageWidgetProvider.saveData(ctx, it) }
                    UiState.Success(it, cookieStored = true, lastUpdated = now)
                },
                onFailure = { e ->
                    val msg = when (e) {
                        is CookieExpiredException ->
                            context?.getString(R.string.cookie_expired) ?: (e.message ?: "Cookie expirada")
                        is InvalidApiKeyException ->
                            context?.getString(R.string.api_key_invalid) ?: (e.message ?: "API key inválida")
                        else ->
                            context?.getString(R.string.network_error, e.message) ?: "Error de red: ${e.message}"
                    }
                    UiState.Error(msg)
                },
            )
        }
    }
    fun updateSettings(s: AlertSettings) {
        val previous = _settings.value
        _settings.value = s
        prefs.edit()
            .putBoolean(KEY_NOTIF_ENABLED, s.notificationsEnabled)
            .putInt(KEY_WEEKLY_ALERT, s.weeklyAlert)
            .putInt(KEY_WEEKLY_CRITICAL, s.weeklyCritical)
            .putInt(KEY_SESSION_ALERT, s.sessionAlert)
            .putInt(KEY_SESSION_CRITICAL, s.sessionCritical)
            .putBoolean(KEY_PERSISTENT_ENABLED, s.persistentEnabled)
            .putInt(KEY_REFRESH_INTERVAL, s.refreshIntervalMinutes)
            .putString(KEY_RESET_DISPLAY, s.resetDisplayMode.name)
            .apply()
        // Si cambió la frecuencia, reprograma el worker en segundo plano.
        if (s.refreshIntervalMinutes != previous.refreshIntervalMinutes) {
            reschedule(s.refreshIntervalMinutes)
        }
    }

    fun updateTheme(theme: AppTheme) {
        _theme.value = theme
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }

    /**
     * Chequea una vez por día si hay release más nuevo en GitHub.
     * Silencioso: si no hay update o falla la red, no molesta.
     */
    fun checkForUpdate() {
        val ctx = context ?: return
        if (!UpdateChecker.shouldCheck(ctx)) return
        viewModelScope.launch {
            val info = withContext(ioDispatcher) {
                runCatching { UpdateChecker.check(ctx) }.getOrNull()
            }
            UpdateChecker.markChecked(ctx)
            if (info != null) _update.value = info
        }
    }

    /** Descarga e instala la actualización (servicio en primer plano con progreso). */
    fun startUpdateDownload(info: UpdateInfo) {
        val ctx = context ?: return
        _download.value = DownloadState.Downloading(0)
        UpdaterService.start(ctx, info.downloadUrl)
        viewModelScope.launch {
            UpdaterService.state.collect { state ->
                _download.value = state
                if (state is DownloadState.Ready || state is DownloadState.Failed) {
                    // El servicio se detiene solo; el estado Ready/Failed queda visible
                    // hasta que el usuario vuelva a la app o se reinicie el flujo.
                }
            }
        }
    }

    /** Revisa de nuevo aunque no haya pasado el intervalo (botón manual). */
    fun checkForUpdateNow() {
        val ctx = context ?: run {
            _checkResult.value = UpdateCheckOutcome.Failed
            return
        }
        if (_checkingUpdate.value) return
        _checkingUpdate.value = true
        _checkResult.value = null
        viewModelScope.launch {
            val info = withContext(ioDispatcher) {
                runCatching { UpdateChecker.check(ctx) }.getOrNull()
            }
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
        notificationsEnabled = prefs.getBoolean(KEY_NOTIF_ENABLED, true),
        weeklyAlert = prefs.getInt(KEY_WEEKLY_ALERT, 80),
        weeklyCritical = prefs.getInt(KEY_WEEKLY_CRITICAL, 95),
        sessionAlert = prefs.getInt(KEY_SESSION_ALERT, 80),
        sessionCritical = prefs.getInt(KEY_SESSION_CRITICAL, 95),
        persistentEnabled = prefs.getBoolean(KEY_PERSISTENT_ENABLED, true),
        refreshIntervalMinutes = prefs.getInt(KEY_REFRESH_INTERVAL, DEFAULT_REFRESH_MINUTES),
        resetDisplayMode = prefs.getString(KEY_RESET_DISPLAY, null)
            ?.let { name -> ResetDisplayMode.entries.firstOrNull { it.name == name } }
            ?: ResetDisplayMode.COUNTDOWN,
    )

    private fun loadTheme(): AppTheme =
        prefs.getString(KEY_THEME, null)
            ?.let { name -> AppTheme.entries.firstOrNull { it.name == name } }
            ?: AppTheme.System

    private fun loadAuthSource(): AuthSource =
        prefs.getString(KEY_AUTH_SOURCE, null)
            ?.let { name -> AuthSource.entries.firstOrNull { it.name == name } }
            ?: AuthSource.COOKIE

    /** Versión instalada de la app (para mostrarla en Configuración). */
    val appVersion: String
        get() = context?.let(UpdateChecker::currentVersion) ?: ""

    companion object {
        const val KEY_COOKIE = "session_cookie"
        const val KEY_API_KEY = "api_key"
        const val KEY_AUTH_SOURCE = "auth_source"
        const val KEY_LAST_UPDATED = "last_updated"
        const val KEY_NOTIF_ENABLED = "notif_enabled"
        const val KEY_WEEKLY_ALERT = "weekly_alert"
        const val KEY_WEEKLY_CRITICAL = "weekly_critical"
        const val KEY_SESSION_ALERT = "session_alert"
        const val KEY_SESSION_CRITICAL = "session_critical"
        const val KEY_THEME = "theme"
        const val KEY_PERSISTENT_ENABLED = "persistent_enabled"
        const val KEY_REFRESH_INTERVAL = "refresh_interval"
        const val KEY_RESET_DISPLAY = "reset_display"
        const val DEFAULT_REFRESH_MINUTES = 60

        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val app = context.applicationContext
                    val prefs = SecurePrefs.get(app)
                    return UsageViewModel(
                        prefs,
                        OllamaUsageScraper(),
                        reschedule = { UsageScheduler.schedule(app, it) },
                        context = app,
                    ) as T
                }
            }
    }
}
