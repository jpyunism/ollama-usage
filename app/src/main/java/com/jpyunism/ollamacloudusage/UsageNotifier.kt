package com.jpyunism.ollamacloudusage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
            context.getString(R.string.channel_alerts_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alerts_description)
        }
        manager.createNotificationChannel(alerts)

        // Canal silencioso para el consumo permanente en pantalla de bloqueo.
        val persistent = NotificationChannel(
            CHANNEL_PERSISTENT_ID,
            context.getString(R.string.channel_persistent_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_persistent_description)
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
            .setContentIntent(openApp(context))
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    /** Construye la notificación permanente (ongoing) con el consumo actual. */
    fun buildPersistent(context: Context, data: UsageData?): Notification {
        val title = if (data != null) {
            context.getString(R.string.persistent_title_weekly, data.weeklyPercent.toString())
        } else {
            context.getString(R.string.persistent_title_updating)
        }
        val text = if (data != null) {
            context.getString(R.string.persistent_text_session, data.sessionPercent.toString(), data.plan)
        } else {
            context.getString(R.string.checking_usage)
        }
        val body = if (data != null) {
            val time = Instant.ofEpochMilli(System.currentTimeMillis())
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"))
            val mode = resetDisplayMode(context)
            buildString {
                append(context.getString(R.string.persistent_body_week_session, data.weeklyPercent.toString(), data.sessionPercent.toString()))
                formatReset(data.weeklyResetAt, mode, locale = context.resources.configuration.locales[0])?.let {
                    append(context.getString(R.string.persistent_body_week_reset, it))
                }
                formatReset(data.sessionResetAt, mode, locale = context.resources.configuration.locales[0])?.let {
                    append(context.getString(R.string.persistent_body_session_reset, it))
                }
                append(context.getString(R.string.persistent_body_plan_updated, data.plan, time))
            }
        } else {
            context.getString(R.string.checking_usage)
        }

        // Samsung Live Notifications / Now Bar (best-effort: requiere whitelist
        // en One UI 7, o Live Updates habilitado en opciones de desarrollador en One UI 8).
        val extras = Bundle().apply {
            putInt("android.ongoingActivityNoti.style", 1)
            putString("android.ongoingActivityNoti.primaryInfo", title)
            putString("android.ongoingActivityNoti.secondaryInfo", text)
            putString(
                "android.ongoingActivityNoti.chipExpandedText",
                if (data != null) context.getString(R.string.chip_weekly, data.weeklyPercent.toString()) else "Ollama",
            )
            if (data != null) {
                putInt("android.ongoingActivityNoti.progress", data.weeklyPercent.toInt().coerceIn(0, 100))
                putInt("android.ongoingActivityNoti.progressMax", 100)
                putString("android.ongoingActivityNoti.nowbarPrimaryInfo", context.getString(R.string.chip_weekly, data.weeklyPercent.toString()))
                putString("android.ongoingActivityNoti.nowbarSecondaryInfo", context.getString(R.string.nowbar_session, data.sessionPercent.toString()))
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
            .setContentIntent(openApp(context))
            .setExtras(extras)
            .build()
    }

    /** Tap en la notificación abre la app. */
    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private const val UPDATE_NOTIFICATION_ID = 1003

    /** Avisa que hay una versión nueva publicada; el tap abre la app. */
    @SuppressLint("MissingPermission") // canNotify() verifica el permiso antes
    fun notifyUpdateAvailable(context: Context, versionName: String) {
        if (!canNotify(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.update_available, versionName))
            .setContentText(context.getString(R.string.update_install))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(UPDATE_NOTIFICATION_ID, notification)
        }
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
