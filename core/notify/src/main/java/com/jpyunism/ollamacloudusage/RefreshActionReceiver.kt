package com.jpyunism.ollamacloudusage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Accion "Refrescar ahora" de la notificacion persistente (issue #94).
 *
 * El PendingIntent de la notificacion ([UsageNotifier.buildPersistent]) apunta
 * a este receiver, declarado en el manifest de `:app` con
 * `android:exported="false"`. Al recibir la accion encola un
 * OneTimeWorkRequest de [UsageWorker], que corre el pipeline completo
 * ([UsageRepository.refreshAndPropagate]): fetch, widget, notificacion
 * persistente e historico. Nunca abre la app.
 *
 * Degrada en silencio: sin auth configurada el pipeline devuelve
 * [UsageError.NoAuth] y [UsageWorker] lo resuelve como `Result.success()`
 * (no hay reintentos ni crash); el enqueue va ademas dentro de `runCatching`.
 */
class RefreshActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (!shouldHandle(intent?.action)) return
        val appContext = context.applicationContext
        runCatching {
            // KEEP: si ya hay un refresco manual en curso, no lo cancela
            // (un tap extra es un no-op, no interrumpe el fetch en vuelo).
            val request = OneTimeWorkRequestBuilder<UsageWorker>().build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }

    companion object {
        /** Nombre del trabajo unico del refresco manual. */
        const val WORK_NAME = "usage_manual_refresh"

        /** true solo para la accion "Refrescar ahora"; ignora cualquier otra. */
        fun shouldHandle(action: String?): Boolean = action == UsageNotifier.ACTION_REFRESH_NOW
    }
}
