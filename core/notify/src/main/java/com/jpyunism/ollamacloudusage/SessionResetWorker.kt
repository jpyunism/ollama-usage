package com.jpyunism.ollamacloudusage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jpyunism.ollamacloudusage.di.AppContainer
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Notificacion proactiva de reset de sesion (issue #57): avisa al usuario
 * 1h antes de que se resetee la sesion, solo si el consumo supera el 70%.
 *
 * Se programa en cada refresh que actualice el resetAt (via
 * [schedule]); un OneTimeWorkRequest con delay = resetAt - 1h. Al disparar,
 * re-verifica el consumo con el ultimo snapshot del historico: si bajo del
 * 70% antes del aviso, no notifica (criterio de cancelacion).
 */
class SessionResetWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appContext = applicationContext
        val container = AppContainer.get(appContext)
        val last = container.historyStore.load().lastOrNull() ?: return Result.success()
        if (SessionResetScheduler.shouldAlert(last.sessionPercent)) {
            UsageNotifier.notifySessionResetSoon(appContext, last.sessionPercent)
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "session_reset_alert"

        /**
         * Programa (o reprograma) el aviso para el resetAt dado. No programa
         * si no hay reset, si el aviso ya deberia haber ocurrido, o si el
         * consumo no supera el 70%. Se cancela el trabajo previo (REPLACE).
         */
        fun schedule(context: Context, resetAt: Instant?, percent: Double) {
            if (!SessionResetScheduler.shouldAlert(percent)) {
                cancel(context)
                return
            }
            val delay = SessionResetScheduler.delayUntilAlert(resetAt) ?: run {
                cancel(context)
                return
            }
            val request = OneTimeWorkRequestBuilder<SessionResetWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
