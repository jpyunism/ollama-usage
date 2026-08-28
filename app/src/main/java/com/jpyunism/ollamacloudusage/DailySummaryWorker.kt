package com.jpyunism.ollamacloudusage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Resumen diario programado (Feature B lote 2, issue #19): a la hora
 * configurada, refresca el consumo y notifica un resumen (% semana/sesión +
 * proyección). Un OneTimeWorkRequest con delay hasta la próxima ocurrencia;
 * al terminar se reprograma para el día siguiente (cadena única).
 *
 * Si el refresh falla, usa el último snapshot del histórico (REQ-113); sin
 * datos no notifica.
 */
class DailySummaryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appContext = applicationContext
        val container = com.jpyunism.ollamacloudusage.di.AppContainer.get(appContext)
        val repository = container.usageRepository

        val result = repository.refreshAndPropagate()
        val data = result.getOrNull()

        // Proyección semanal del período actual (con ancla real o fallback).
        val snapshots = repository.historySnapshots()
        val now = System.currentTimeMillis()
        val anchor = data?.weeklyResetAt?.toEpochMilli()
            ?: repository.detectedWeeklyAnchor()
            ?: fallbackResetAnchor(HistoryPeriod.WEEK, now)
        val projection = anchor?.let {
            currentPeriod(snapshots, HistoryPeriod.WEEK, it, now) { s -> s.weeklyPercent }?.projection
        }

        val text = if (data != null) {
            dailySummaryText(data, projection?.toPercent)
        } else {
            // REQ-113: con refresh fallido, último snapshot del histórico.
            snapshots.lastOrNull()?.let { last ->
                val fallback = UsageData(
                    sessionPercent = last.sessionPercent,
                    weeklyPercent = last.weeklyPercent,
                    sessionResetAt = null,
                    sessionModels = emptyList(),
                    weeklyModels = emptyList(),
                    plan = "cloud",
                )
                dailySummaryText(fallback, projection?.toPercent)
            }
        }

        if (text != null) {
            UsageNotifier.notifyDailySummary(appContext, text)
        }

        // Reprograma para el día siguiente (cadena de OneTime works).
        schedule(appContext)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "daily_summary"

        /** Programa (o reprograma) el resumen diario según prefs. */
        fun schedule(context: Context) {
            val prefs = com.jpyunism.ollamacloudusage.SecurePrefs.get(context)
            if (!prefs.getBoolean(PrefsKeys.DAILY_SUMMARY_ENABLED, false)) {
                cancel(context)
                return
            }
            val hour = prefs.getInt(PrefsKeys.DAILY_SUMMARY_HOUR, 21)
            val minute = prefs.getInt(PrefsKeys.DAILY_SUMMARY_MINUTE, 0)
            val now = System.currentTimeMillis()
            val runAt = nextDailyRunMillis(hour, minute, now, java.time.ZoneId.systemDefault())
            val delay = Duration.ofMillis(runAt - now)
            val request = OneTimeWorkRequestBuilder<DailySummaryWorker>()
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
            prefs.edit().putLong(NEXT_DAILY_RUN_KEY, runAt).apply()
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}