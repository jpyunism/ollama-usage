package com.jpyunism.ollamacloudusage

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.jpyunism.ollamacloudusage.PrefsKeys
import com.jpyunism.ollamacloudusage.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano para frecuencias de refresco < 15 min
 * (WorkManager no permite periódicos más cortos). Muestra la notificación
 * permanente de pantalla de bloqueo y la actualiza en cada ciclo.
 */
class UsageMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var intervalMinutes = PrefsKeys.DEFAULT_REFRESH_MINUTES
    private var consecutiveFailures = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intervalMinutes = intent?.getIntExtra(EXTRA_INTERVAL, -1)?.takeIf { it > 0 }
            ?: SecurePrefs.get(this).getInt(PrefsKeys.REFRESH_INTERVAL, PrefsKeys.DEFAULT_REFRESH_MINUTES)
        try {
            startForeground(UsageNotifier.PERSISTENT_ID, UsageNotifier.buildPersistent(this, null))
        } catch (_: IllegalStateException) {
            // Android 12+ deniega startForeground() cuando la app está en
            // background (p.ej. restart sticky del sistema con intent=null).
            // ForegroundServiceStartNotAllowedException extiende
            // IllegalStateException. No crashear: dejar el intervalo pendiente
            // para que UsageScheduler.retryPending lo restaure en el próximo
            // arranque en foreground, y parar el servicio.
            SecurePrefs.get(this).edit()
                .putInt(UsageScheduler.KEY_PENDING_FGS, intervalMinutes)
                .apply()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startLoop()
        return START_STICKY
    }

    private fun prefs() = SecurePrefs.get(this)

    private fun startLoop() {
        scope.launch {
            while (isActive) {
                val ok = refreshOnce()
                if (ok) {
                    consecutiveFailures = 0
                    delay(intervalMinutes * 60_000L)
                } else {
                    // Backoff exponencial: si el fetch falla en bucle (red caída,
                    // cookie inválida), no martillear ollama.com. 1, 2, 4... 30 min máx.
                    consecutiveFailures = (consecutiveFailures + 1).coerceAtMost(6)
                    val backoffSeconds = (60L shl (consecutiveFailures - 1)).coerceAtMost(30 * 60L)
                    delay(backoffSeconds * 1_000L)
                }
            }
        }
    }

    /**
     * true si el refresco fue exitoso; false si falló (activa backoff).
     * Usa el pipeline único [UsageRepository.refreshAndPropagate] (REQ-003):
     * las alertas de umbral corren también en frecuencias < 15 min.
     */
    private suspend fun refreshOnce(): Boolean {
        val repo = AppContainer.get(this).usageRepository
        val result = repo.refreshAndPropagate()
        return result.exceptionOrNull() == null
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_INTERVAL = "interval_minutes"

        fun start(context: Context, intervalMinutes: Int) {
            val intent = Intent(context, UsageMonitorService::class.java)
                .putExtra(EXTRA_INTERVAL, intervalMinutes)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, UsageMonitorService::class.java))
        }
    }
}
