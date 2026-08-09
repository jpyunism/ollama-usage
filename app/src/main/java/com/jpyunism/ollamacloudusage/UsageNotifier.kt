package com.jpyunism.ollamacloudusage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object UsageNotifier {

    const val CHANNEL_ID = "usage_alerts"
    const val CHANNEL_PERSISTENT_ID = "usage_persistent"
    private const val NOTIFICATION_ID = 1001
    const val PERSISTENT_ID = 1002

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

    @SuppressLint("MissingPermission") // canNotify() verifica el permiso antes
    fun notifyLimit(context: Context, title: String, message: String) {
        if (!canNotify(context)) return
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

    /** Construye la notificación permanente (ongoing) con el consumo actual. */
    fun buildPersistent(context: Context, data: UsageData?): Notification {
        val title = if (data != null) {
            "Ollama Cloud — ${data.weeklyPercent}% semanal"
        } else {
            "Ollama Cloud — actualizando…"
        }
        val text = if (data != null) {
            "Sesión ${data.sessionPercent}% · Plan ${data.plan}"
        } else {
            "Consultando consumo…"
        }
        val body = if (data != null) {
            val time = Instant.ofEpochMilli(System.currentTimeMillis())
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"))
            val mode = resetDisplayMode(context)
            buildString {
                append("Semana: ${data.weeklyPercent}% · Sesión: ${data.sessionPercent}%\n")
                formatReset(data.weeklyResetAt, mode)?.let { append("Semana $it\n") }
                formatReset(data.sessionResetAt, mode)?.let { append("Sesión $it\n") }
                append("Plan ${data.plan} · Actualizado $time")
            }
        } else {
            "Consultando consumo…"
        }

        // Samsung Live Notifications / Now Bar (best-effort: requiere whitelist
        // en One UI 7, o Live Updates habilitado en opciones de desarrollador en One UI 8).
        val extras = Bundle().apply {
            putInt("android.ongoingActivityNoti.style", 1)
            putString("android.ongoingActivityNoti.primaryInfo", title)
            putString("android.ongoingActivityNoti.secondaryInfo", text)
            putString(
                "android.ongoingActivityNoti.chipExpandedText",
                if (data != null) "Ollama ${data.weeklyPercent}%" else "Ollama",
            )
            if (data != null) {
                putInt("android.ongoingActivityNoti.progress", data.weeklyPercent.toInt().coerceIn(0, 100))
                putInt("android.ongoingActivityNoti.progressMax", 100)
                putString("android.ongoingActivityNoti.nowbarPrimaryInfo", "Ollama ${data.weeklyPercent}%")
                putString("android.ongoingActivityNoti.nowbarSecondaryInfo", "Sesión ${data.sessionPercent}%")
            }
        }

        return NotificationCompat.Builder(context, CHANNEL_PERSISTENT_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setExtras(extras)
            .build()
    }

    /** Notificación permanente (ongoing) con el consumo actual, visible en pantalla de bloqueo. */
    @SuppressLint("MissingPermission") // canNotify() verifica el permiso antes
    fun showPersistent(context: Context, data: UsageData) {
        if (!canNotify(context)) return
        runCatching {
            NotificationManagerCompat.from(context).notify(PERSISTENT_ID, buildPersistent(context, data))
        }
    }

    fun hidePersistent(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(PERSISTENT_ID)
        }
    }

    /** true si la app tiene permiso de notificaciones (Android 13+) o no lo requiere. */
    @SuppressLint("MissingPermission")
    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Modo de visualización del reset configurado por el usuario. */
    private fun resetDisplayMode(context: Context): ResetDisplayMode =
        runCatching {
            SecurePrefs.get(context).getString(UsageViewModel.KEY_RESET_DISPLAY, null)
                ?.let { name -> ResetDisplayMode.entries.firstOrNull { it.name == name } }
        }.getOrNull() ?: ResetDisplayMode.COUNTDOWN
}
