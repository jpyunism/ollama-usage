package com.jpyunism.ollamacloudusage

import android.app.Application

class OllamaUsageApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Elimina el archivo legacy con la cookie en claro (una sola vez).
        SecurePrefs.purgeLegacy(this)
        UsageNotifier.ensureChannels(this)
        // Programa con el intervalo guardado (o el default 60 min).
        val prefs = SecurePrefs.get(this)
        val interval = prefs.getInt(UsageViewModel.KEY_REFRESH_INTERVAL, UsageViewModel.DEFAULT_REFRESH_MINUTES)
        UsageScheduler.schedule(this, interval)
    }
}
