package com.jpyunism.ollamacloudusage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Revisa el consumo en segundo plano (periódico) y notifica si el plan
 * superó los umbrales configurados.
 */
class UsageWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("ollama_usage", Context.MODE_PRIVATE)
        val cookie = prefs.getString(UsageViewModel.KEY_COOKIE, null) ?: return Result.success()

        val data = withContext(Dispatchers.IO) {
            runCatching { OllamaUsageScraper().fetchUsage(cookie) }.getOrNull()
        } ?: return Result.retry()

        val weekly = data.weeklyPercent
        val session = data.sessionPercent

        // Umbrales: avisar a 80% y 95%, no repetir hasta que baje de 75%.
        val lastNotifiedWeekly = prefs.getFloat(KEY_LAST_NOTIFIED_WEEKLY, -1f)
        if (weekly >= 95 && lastNotifiedWeekly < 95) {
            UsageNotifier.notifyLimit(
                applicationContext,
                "¡Límite semanal casi agotado!",
                "Ollama Cloud está al ${weekly}% del plan semanal. Considera pausar modelos pesados.",
            )
            prefs.edit().putFloat(KEY_LAST_NOTIFIED_WEEKLY, 95f).apply()
        } else if (weekly >= 80 && lastNotifiedWeekly < 80) {
            UsageNotifier.notifyLimit(
                applicationContext,
                "Consumo semanal al ${weekly}%",
                "Tu plan de Ollama Cloud está al ${weekly}% esta semana.",
            )
            prefs.edit().putFloat(KEY_LAST_NOTIFIED_WEEKLY, 80f).apply()
        } else if (weekly < 75) {
            prefs.edit().putFloat(KEY_LAST_NOTIFIED_WEEKLY, -1f).apply()
        }

        val lastNotifiedSession = prefs.getFloat(KEY_LAST_NOTIFIED_SESSION, -1f)
        if (session >= 95 && lastNotifiedSession < 95) {
            UsageNotifier.notifyLimit(
                applicationContext,
                "Sesión al ${session}%",
                "La sesión de Ollama Cloud está al ${session}%. Se resetea pronto.",
            )
            prefs.edit().putFloat(KEY_LAST_NOTIFIED_SESSION, 95f).apply()
        } else if (session >= 80 && lastNotifiedSession < 80) {
            UsageNotifier.notifyLimit(
                applicationContext,
                "Sesión al ${session}%",
                "La sesión actual está al ${session}% de uso.",
            )
            prefs.edit().putFloat(KEY_LAST_NOTIFIED_SESSION, 80f).apply()
        } else if (session < 75) {
            prefs.edit().putFloat(KEY_LAST_NOTIFIED_SESSION, -1f).apply()
        }

        return Result.success()
    }

    companion object {
        const val KEY_LAST_NOTIFIED_WEEKLY = "last_notified_weekly"
        const val KEY_LAST_NOTIFIED_SESSION = "last_notified_session"
    }
}
