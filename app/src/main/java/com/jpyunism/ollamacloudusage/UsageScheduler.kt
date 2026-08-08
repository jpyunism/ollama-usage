package com.jpyunism.ollamacloudusage

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Programa el check periódico de consumo con el intervalo configurado. */
object UsageScheduler {

    const val WORK_NAME = "usage_periodic_check"

    /** Mínimo permitido por WorkManager para trabajo periódico. */
    const val MIN_INTERVAL_MINUTES = 15

    /** Opciones de intervalo de refresco (minutos). */
    val REFRESH_INTERVALS = listOf(15, 30, 60, 120, 240, 360, 720)

    fun schedule(context: Context, intervalMinutes: Int) {
        val safe = intervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES)
        val request = PeriodicWorkRequestBuilder<UsageWorker>(safe.toLong(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
