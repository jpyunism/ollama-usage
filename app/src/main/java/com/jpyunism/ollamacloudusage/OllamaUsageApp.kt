package com.jpyunism.ollamacloudusage

import android.app.Application

class OllamaUsageApp : Application() {

    override fun onCreate() {
        super.onCreate()
        UsageNotifier.ensureChannels(this)
        // Programa con el intervalo guardado (o el default 60 min).
        val prefs = getSharedPreferences("ollama_usage", MODE_PRIVATE)
        val interval = prefs.getInt(UsageViewModel.KEY_REFRESH_INTERVAL, UsageViewModel.DEFAULT_REFRESH_MINUTES)
        UsageScheduler.schedule(this, interval)
    }
}
