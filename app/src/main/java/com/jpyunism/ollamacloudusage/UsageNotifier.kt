package com.jpyunism.ollamacloudusage

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object UsageNotifier {

    const val CHANNEL_ID = "usage_alerts"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val channel = android.app.NotificationChannel(
            CHANNEL_ID,
            "Alertas de consumo",
            android.app.NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Avisa cuando el plan de Ollama Cloud se acerca al límite"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun notifyLimit(context: Context, title: String, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
