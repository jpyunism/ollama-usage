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
    const val MIN_PERIODIC_MINUTES = 15

    /** Rango del slider de refresco (minutos). */
    const val MIN_REFRESH_MINUTES = 1
    const val MAX_REFRESH_MINUTES = 720

    /**
     * Programa el refresco. Para intervalos >= 15 min usa WorkManager;
     * para intervalos menores usa el servicio en primer plano (más preciso).
     */
    fun schedule(context: Context, intervalMinutes: Int) {
        val safe = intervalMinutes.coerceIn(MIN_REFRESH_MINUTES, MAX_REFRESH_MINUTES)
        if (safe < MIN_PERIODIC_MINUTES) {
            // Frecuencia alta: servicio en primer plano con su propia notificación.
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            UsageMonitorService.start(context, safe)
        } else {
            // Frecuencia normal: WorkManager periódico.
            UsageMonitorService.stop(context)
            val request = PeriodicWorkRequestBuilder<UsageWorker>(safe.toLong(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
