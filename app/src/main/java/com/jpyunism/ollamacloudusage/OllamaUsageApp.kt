package com.jpyunism.ollamacloudusage

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.jpyunism.ollamacloudusage.PrefsKeys
import com.jpyunism.ollamacloudusage.di.AppContainer

class OllamaUsageApp : Application() {

    /**
     * Instala el CrashReporter lo antes posible (antes de onCreate), para
     * capturar cualquier excepción del arranque y mostrarla en
     * CrashActivity en vez de cerrar la app en silencio.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashReporter.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        val container = AppContainer.get(this)
        val prefs = container.prefs
        // Aplica el idioma guardado (o el del sistema) antes de crear la UI.
        val language = prefs.getString(PrefsKeys.LANGUAGE, null)
            ?.let { name -> AppLanguage.entries.firstOrNull { it.name == name } }
            ?: AppLanguage.System
        LocaleHelper.apply(this, language)
        // Elimina archivos de formatos anteriores (cookie en claro / prefs viejas).
        SecurePrefs.purgeLegacy(this)
        // Crea los canales de notificación y programa el refresh de fondo.
        val interval = prefs.getInt(PrefsKeys.REFRESH_INTERVAL, PrefsKeys.DEFAULT_REFRESH_MINUTES)
        Handler(Looper.getMainLooper()).post {
            UsageNotifier.ensureChannels(this)
            UsageScheduler.schedule(this, interval)
        }
    }
}
