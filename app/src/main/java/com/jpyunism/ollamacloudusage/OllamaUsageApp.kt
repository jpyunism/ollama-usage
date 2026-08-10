package com.jpyunism.ollamacloudusage

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper

class OllamaUsageApp : Application() {

    /**
     * Instala el CrashReporter lo antes posible (antes de onCreate), para
     * capturar cualquier excepción del arranque y mostrarla en
     * CrashActivity en vez de cerrar la app en silencio.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashReporter.install(this)
        // Arranca la inicialización de preferencias cifradas en background:
        // en OEMs con Keystore lento/roto, hacerlo en el hilo principal
        // congela la app con el splash blanco. get() nunca bloquea >3s.
        SecurePrefs.startInit(this)
    }

    override fun onCreate() {
        super.onCreate()
        val prefs = SecurePrefs.get(this)
        // Aplica el idioma guardado (o el del sistema) antes de crear la UI.
        val language = prefs.getString(UsageViewModel.KEY_LANGUAGE, null)
            ?.let { name -> AppLanguage.entries.firstOrNull { it.name == name } }
            ?: AppLanguage.System
        LocaleHelper.apply(this, language)
        // Elimina el archivo legacy con la cookie en claro (una sola vez).
        SecurePrefs.purgeLegacy(this)
        // Crea los canales de notificación y programa el refresh de fondo
        // sin bloquear: si las prefs aún no están listas, reintenta al rato.
        val interval = prefs.getInt(UsageViewModel.KEY_REFRESH_INTERVAL, UsageViewModel.DEFAULT_REFRESH_MINUTES)
        Handler(Looper.getMainLooper()).post {
            UsageNotifier.ensureChannels(this)
            UsageScheduler.schedule(this, interval)
        }
    }
}
