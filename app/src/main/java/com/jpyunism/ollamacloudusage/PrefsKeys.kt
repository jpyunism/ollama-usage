package com.jpyunism.ollamacloudusage

/**
 * Claves de SharedPreferences centralizadas. Los secretos (cookie/API key)
 * se cifran en reposo vía [SecurePrefs]; el resto se guarda en claro.
 * Única fuente de verdad: ningún otro archivo define keys propias.
 */
object PrefsKeys {
    // Auth
    const val COOKIE = "session_cookie"
    const val API_KEY = "api_key"
    const val AUTH_SOURCE = "auth_source"
    const val LAST_UPDATED = "last_updated"

    // Settings
    const val NOTIF_ENABLED = "notif_enabled"
    const val WEEKLY_ALERT = "weekly_alert"
    const val WEEKLY_CRITICAL = "weekly_critical"
    const val SESSION_ALERT = "session_alert"
    const val SESSION_CRITICAL = "session_critical"
    const val THEME = "theme"
    const val DARK_MODE = "dark_mode"
    const val LANGUAGE = "language"
    const val PERSISTENT_ENABLED = "persistent_enabled"
    const val REFRESH_INTERVAL = "refresh_interval"
    const val RESET_DISPLAY = "reset_display"

    // Threshold notifications (AlertEngine)
    const val LAST_NOTIFIED_WEEKLY = "last_notified_weekly"
    const val LAST_NOTIFIED_SESSION = "last_notified_session"

    // Defaults
    const val DEFAULT_REFRESH_MINUTES = 60
}
