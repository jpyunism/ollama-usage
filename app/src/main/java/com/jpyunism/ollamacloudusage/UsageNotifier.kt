package com.jpyunism.ollamacloudusage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object UsageNotifier {

    const val CHANNEL_ID = "usage_alerts"
    const val CHANNEL_PERSISTENT_ID = "usage_persistent"
    private const val NOTIFICATION_ID = 1001
    private const val PERSISTENT_ID = 1002

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val alerts = NotificationChannel(
            CHANNEL_ID,
            "Alertas de consumo",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Avisa cuando el plan de Ollama Cloud se acerca al límite"
        }
        manager.createNotificationChannel(alerts)

        // Canal silencioso para el consumo permanente en pantalla de bloqueo.
        val persistent = NotificationChannel(
            CHANNEL_PERSISTENT_ID,
            "Consumo en pantalla de bloqueo",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Muestra el consumo de Ollama Cloud siempre visible"
            setShowBadge(false)
        }
        manager.createNotificationChannel(persistent)
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

    /** Notificación permanente (ongoing) con el consumo actual, visible en pantalla de bloqueo. */
    fun showPersistent(context: Context, data: UsageData) {
        val time = Instant.ofEpochMilli(System.currentTimeMillis())
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))

        val body = buildString {
            append("Semana: ${data.weeklyPercent}% · Sesión: ${data.sessionPercent}%\n")
            append("Plan ${data.plan} · Actualizado $time")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_PERSISTENT_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Ollama Cloud — ${data.weeklyPercent}% semanal")
            .setContentText("Sesión ${data.sessionPercent}% · Plan ${data.plan}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(PERSISTENT_ID, notification)
        }
    }

    fun hidePersistent(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(PERSISTENT_ID)
        }
    }
}
