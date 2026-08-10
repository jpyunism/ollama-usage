package com.jpyunism.ollamacloudusage

import android.app.Application
import android.content.Context

class OllamaUsageApp : Application() {

    /**
     * Instala el CrashReporter lo antes posible (antes de onCreate), para
     * capturar cualquier excepción del arranque — incluidas las de
     * EncryptedSharedPreferences/Keystore — y mostrarla en CrashActivity en
     * vez de cerrar la app en silencio.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashReporter.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        // Aplica el idioma guardado (o el del sistema) antes de crear la UI.
        val prefs = SecurePrefs.get(this)
        val language = prefs.getString(UsageViewModel.KEY_LANGUAGE, null)
            ?.let { name -> AppLanguage.entries.firstOrNull { it.name == name } }
            ?: AppLanguage.System
        LocaleHelper.apply(this, language)
        // Elimina el archivo legacy con la cookie en claro (una sola vez).
        SecurePrefs.purgeLegacy(this)
        UsageNotifier.ensureChannels(this)
        // Programa con el intervalo guardado (o el default 60 min).
        val interval = prefs.getInt(UsageViewModel.KEY_REFRESH_INTERVAL, UsageViewModel.DEFAULT_REFRESH_MINUTES)
        UsageScheduler.schedule(this, interval)
    }
}
