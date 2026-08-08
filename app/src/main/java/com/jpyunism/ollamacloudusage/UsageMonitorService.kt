package com.jpyunism.ollamacloudusage

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
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
    private var intervalMinutes = UsageScheduler.MIN_PERIODIC_MINUTES

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intervalMinutes = intent?.getIntExtra(EXTRA_INTERVAL, -1)?.takeIf { it > 0 }
            ?: prefs().getInt(UsageViewModel.KEY_REFRESH_INTERVAL, UsageScheduler.MIN_PERIODIC_MINUTES)
        startForeground(UsageNotifier.PERSISTENT_ID, UsageNotifier.buildPersistent(this, null))
        startLoop()
        return START_STICKY
    }

    private fun prefs() = getSharedPreferences("ollama_usage", Context.MODE_PRIVATE)

    private fun startLoop() {
        scope.launch {
            while (isActive) {
                refreshOnce()
                delay(intervalMinutes * 60_000L)
            }
        }
    }

    private suspend fun refreshOnce() {
        val cookie = prefs().getString(UsageViewModel.KEY_COOKIE, null) ?: return
        val data = runCatching { OllamaUsageScraper().fetchUsage(cookie) }.getOrNull() ?: return
        UsageNotifier.showPersistent(this, data)
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
