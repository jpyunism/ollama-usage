package com.jpyunism.ollamacloudusage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jpyunism.ollamacloudusage.di.AppContainer

/**
 * Revisa el consumo en segundo plano (periódico) y notifica si el plan
 * superó los umbrales configurados por el usuario.
 *
 * Toda la lógica de negocio vive en [UsageRepository.refreshAndPropagate];
 * este worker solo decide el Result de WorkManager.
 */
class UsageWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appContext = applicationContext
        val repository = AppContainer.get(appContext).usageRepository

        val result = repository.refreshAndPropagate()

        // Check de nueva versión (una vez por día, silencioso si no hay).
        if (UpdateChecker.shouldCheck(appContext)) {
            val info = runCatching { UpdateChecker.check(appContext) }.getOrNull()
            UpdateChecker.markChecked(appContext)
            if (info != null) {
                // Notifica al usuario que hay una versión nueva disponible.
                UsageNotifier.notifyUpdateAvailable(appContext, info.versionName)
            }
        }

        return when (result.exceptionOrNull()) {
            // Success: pipeline completo ya se propagó.
            null -> Result.success()
            // Errores de credenciales: no martillar — reintentar no arregla nada.
            is UsageError.CookieExpired, is UsageError.InvalidApiKey, is UsageError.NoAuth ->
                Result.success()
            // Fallo de red/transitorio: WorkManager reintenta con backoff.
            is UsageError.Network -> Result.retry()
            else -> Result.retry()
        }
    }
}
