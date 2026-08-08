package com.jpyunism.ollamacloudusage

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class OllamaUsageApp : Application() {

    override fun onCreate() {
        super.onCreate()
        UsageNotifier.ensureChannel(this)
        schedulePeriodicCheck()
    }

    private fun schedulePeriodicCheck() {
        val request = PeriodicWorkRequestBuilder<UsageWorker>(4, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "usage_periodic_check",
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
