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
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AlertSettings> = _settings

    private val _theme = MutableStateFlow(loadTheme())
    val theme: StateFlow<AppTheme> = _theme

    init {
        if (prefs.contains(KEY_COOKIE)) refresh()
    }

    fun saveCookie(cookie: String) {
        prefs.edit().putString(KEY_COOKIE, cookie.trim()).apply()
        refresh()
    }

    fun clearCookie() {
        prefs.edit().remove(KEY_COOKIE).apply()
        _uiState.value = UiState.Idle
    }

    fun hasCookie(): Boolean = prefs.contains(KEY_COOKIE)

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

    fun refresh() {
        val cookie = prefs.getString(KEY_COOKIE, null) ?: run {
            _uiState.value = UiState.Idle
            return
        }
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { scraper.fetchUsage(cookie) }
            }
            _uiState.value = result.fold(
                onSuccess = {
                    val now = System.currentTimeMillis()
                    prefs.edit().putLong(KEY_LAST_UPDATED, now).apply()
                    UiState.Success(it, cookieStored = true, lastUpdated = now)
                },
                onFailure = { e ->
                    val msg = when (e) {
                        is CookieExpiredException -> e.message ?: "Cookie expirada"
                        else -> "Error de red: ${e.message}"
                    }
                    UiState.Error(msg)
                },
            )
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
    )

    private fun loadTheme(): AppTheme =
        prefs.getString(KEY_THEME, null)
            ?.let { name -> AppTheme.entries.firstOrNull { it.name == name } }
            ?: AppTheme.System

    companion object {
        const val KEY_COOKIE = "session_cookie"
        const val KEY_LAST_UPDATED = "last_updated"
        const val KEY_NOTIF_ENABLED = "notif_enabled"
        const val KEY_WEEKLY_ALERT = "weekly_alert"
        const val KEY_WEEKLY_CRITICAL = "weekly_critical"
        const val KEY_SESSION_ALERT = "session_alert"
        const val KEY_SESSION_CRITICAL = "session_critical"
        const val KEY_THEME = "theme"
        const val KEY_PERSISTENT_ENABLED = "persistent_enabled"
        const val KEY_REFRESH_INTERVAL = "refresh_interval"
        const val DEFAULT_REFRESH_MINUTES = 60

        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val app = context.applicationContext
                    val prefs = app.getSharedPreferences("ollama_usage", Context.MODE_PRIVATE)
                    return UsageViewModel(
                        prefs,
                        OllamaUsageScraper(),
                        reschedule = { UsageScheduler.schedule(app, it) },
                    ) as T
                }
            }
    }
}
