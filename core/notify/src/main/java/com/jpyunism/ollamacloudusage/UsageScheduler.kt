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

    /** Intervalo que quedó pendiente de arrancar como FGS (app en background). */
    const val KEY_PENDING_FGS = "pending_fgs_interval"

    /**
     * Motor que ejecuta el refresco para [intervalMinutes]. La UI lo muestra
     * para no prometer una precisión que el motor no puede dar.
     */
    fun engineFor(intervalMinutes: Int): RefreshEngine {
        val safe = intervalMinutes.coerceIn(MIN_REFRESH_MINUTES, MAX_REFRESH_MINUTES)
        return if (safe < MIN_PERIODIC_MINUTES) RefreshEngine.FOREGROUND_SERVICE else RefreshEngine.WORK_MANAGER
    }

    /**
     * Red de seguridad cuando el FGS ya no puede sostener el refresco (p. ej.
     * Android 15 agotó el tope diario de 6 h de los FGS `dataSync`): deja
     * programado el WorkManager de 15 min para que el consumo no se congele en
     * silencio, y guarda el intervalo pedido en [KEY_PENDING_FGS] para
     * restaurarlo con [retryPending] cuando la app vuelva a foreground.
     */
    fun fallbackToPeriodic(context: Context, pendingInterval: Int? = null) {
        val prefs = SecurePrefs.get(context)
        pendingInterval?.let { prefs.edit().putInt(KEY_PENDING_FGS, it).apply() }
        val request = PeriodicWorkRequestBuilder<UsageWorker>(
            MIN_PERIODIC_MINUTES.toLong(),
            TimeUnit.MINUTES,
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /**
     * Programa el refresco. Para intervalos >= 15 min usa WorkManager;
     * para intervalos menores usa el servicio en primer plano (más preciso).
     *
     * Android 12+ prohíbe arrancar foreground services desde background
     * (ForegroundServiceStartNotAllowedException). Si el arranque ocurre
     * cuando la app no está visible (p.ej. el post de Application.onCreate
     * corre tras cerrar la app), NO crasheamos: dejamos el intervalo
     * pendiente ([KEY_PENDING_FGS]) y programamos WorkManager a 15 min como
     * red de seguridad; [retryPending] lo restaura en el próximo arranque
     * en foreground.
     */
    fun schedule(context: Context, intervalMinutes: Int) {
        val safe = intervalMinutes.coerceIn(MIN_REFRESH_MINUTES, MAX_REFRESH_MINUTES)
        val prefs = SecurePrefs.get(context)
        if (safe < MIN_PERIODIC_MINUTES) {
            // Frecuencia alta: servicio en primer plano con su propia notificación.
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            if (tryStartMonitor(context, safe)) {
                prefs.edit().remove(KEY_PENDING_FGS).apply()
            } else {
                prefs.edit().putInt(KEY_PENDING_FGS, safe).apply()
                fallbackToPeriodic(context)
            }
        } else {
            // Frecuencia normal: WorkManager periódico.
            UsageMonitorService.stop(context)
            prefs.edit().remove(KEY_PENDING_FGS).apply()
            val request = PeriodicWorkRequestBuilder<UsageWorker>(safe.toLong(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }

    /**
     * Reintenta el FGS pendiente. Llamar solo desde foreground (p.ej.
     * MainActivity.onResume): si Android lo vuelve a denegar, el flag queda
     * guardado para el próximo intento.
     */
    fun retryPending(context: Context) {
        val prefs = SecurePrefs.get(context)
        val pending = prefs.getInt(KEY_PENDING_FGS, -1)
        if (pending > 0) {
            prefs.edit().remove(KEY_PENDING_FGS).apply()
            schedule(context, pending)
        }
    }

    /** true si el FGS arrancó; false si Android lo denegó (app en background). */
    private fun tryStartMonitor(context: Context, intervalMinutes: Int): Boolean = try {
        UsageMonitorService.start(context, intervalMinutes)
        true
    } catch (_: IllegalStateException) {
        // ForegroundServiceStartNotAllowedException extiende IllegalStateException.
        false
    }
}
