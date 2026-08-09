package com.jpyunism.ollamacloudusage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Revisa el consumo en segundo plano (periódico) y notifica si el plan
 * superó los umbrales configurados por el usuario.
 */
class UsageWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appContext = applicationContext
        val prefs = SecurePrefs.get(appContext)

        val cookie = prefs.getString(UsageViewModel.KEY_COOKIE, null) ?: return Result.success()

        val data = withContext(Dispatchers.IO) {
            runCatching { OllamaUsageScraper().fetchUsage(cookie) }.getOrNull()
        } ?: return Result.retry()

        // Widget del home screen: guarda el último consumo y lo re-renderiza.
        UsageWidgetProvider.saveData(appContext, data)
        UsageWidgetProvider.updateAll(appContext)

        // Notificación permanente (pantalla de bloqueo) — independiente de las alertas.
        if (prefs.getBoolean(UsageViewModel.KEY_PERSISTENT_ENABLED, true)) {
            UsageNotifier.showPersistent(appContext, data)
        } else {
            UsageNotifier.hidePersistent(appContext)
        }

        // Alertas de umbral — solo si el usuario las activó.
        if (!prefs.getBoolean(UsageViewModel.KEY_NOTIF_ENABLED, true)) return Result.success()

        val settings = AlertSettings(
            notificationsEnabled = true,
            weeklyAlert = prefs.getInt(UsageViewModel.KEY_WEEKLY_ALERT, 80),
            weeklyCritical = prefs.getInt(UsageViewModel.KEY_WEEKLY_CRITICAL, 95),
            sessionAlert = prefs.getInt(UsageViewModel.KEY_SESSION_ALERT, 80),
            sessionCritical = prefs.getInt(UsageViewModel.KEY_SESSION_CRITICAL, 95),
        )

        checkThreshold(
            prefs = prefs,
            percent = data.weeklyPercent,
            alert = settings.weeklyAlert,
            critical = settings.weeklyCritical,
            lastKey = KEY_LAST_NOTIFIED_WEEKLY,
        ) { pct, level ->
            UsageNotifier.notifyLimit(
                appContext,
                if (level == CRITICAL) {
                    appContext.getString(R.string.weekly_critical_title)
                } else {
                    appContext.getString(R.string.weekly_alert_title, pct)
                },
                appContext.getString(R.string.weekly_alert_message, pct),
            )
        }

        checkThreshold(
            prefs = prefs,
            percent = data.sessionPercent,
            alert = settings.sessionAlert,
            critical = settings.sessionCritical,
            lastKey = KEY_LAST_NOTIFIED_SESSION,
        ) { pct, _ ->
            UsageNotifier.notifyLimit(
                appContext,
                appContext.getString(R.string.session_alert_title, pct),
                appContext.getString(R.string.session_alert_message, pct),
            )
        }

        return Result.success()
    }

    /**
     * Notifica cuando el consumo cruza el umbral de alerta o crítico.
     * No repite la misma notificación hasta que el consumo baje del umbral de alerta.
     */
    private fun checkThreshold(
        prefs: android.content.SharedPreferences,
        percent: Double,
        alert: Int,
        critical: Int,
        lastKey: String,
        notify: (Int, Int) -> Unit,
    ) {
        val lastNotified = prefs.getInt(lastKey, -1)
        val pct = percent.toInt()

        when (nextLevel(pct, alert, critical, lastNotified)) {
            CRITICAL -> {
                notify(pct, CRITICAL)
                prefs.edit().putInt(lastKey, critical).apply()
            }
            ALERT -> {
                notify(pct, ALERT)
                prefs.edit().putInt(lastKey, alert).apply()
            }
            else -> {
                // Reset: permite volver a notificar cuando vuelva a cruzar el umbral.
                prefs.edit().putInt(lastKey, -1).apply()
            }
        }
    }

    /**
     * Lógica pura de umbrales: devuelve el nivel a notificar o -1 si no corresponde.
     * - CRITICAL si pct >= critical y aún no se notificó el nivel crítico.
     * - ALERT si pct >= alert y aún no se notificó el nivel de alerta.
     * - -1 si pct < alert (reset) o ya se notificó ese nivel.
     */
    companion object {
        const val KEY_LAST_NOTIFIED_WEEKLY = "last_notified_weekly"
        const val KEY_LAST_NOTIFIED_SESSION = "last_notified_session"
        private const val ALERT = 0
        private const val CRITICAL = 1

        internal fun nextLevel(
            pct: Int,
            alert: Int,
            critical: Int,
            lastNotified: Int,
        ): Int = when {
            pct >= critical && lastNotified < critical -> CRITICAL
            pct >= alert && lastNotified < alert -> ALERT
            else -> -1
        }
    }
}
