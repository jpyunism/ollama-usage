package com.jpyunism.ollamacloudusage

import com.jpyunism.ollamacloudusage.core.ui.R
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
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object UsageNotifier {

    const val CHANNEL_ID = "usage_alerts"
    const val CHANNEL_PERSISTENT_ID = "usage_persistent"
    private const val NOTIFICATION_ID = 1001
    const val PERSISTENT_ID = 1002

    // IDs por tipo de alerta de umbral (issue #73): cada tipo tiene su propio
    // ID para que dos alertas disparadas en el mismo ciclo no se sobreescriban.
    const val WEEKLY_ALERT_ID = 1007
    const val SESSION_ALERT_ID = 1008
    const val PACE_WEEKLY_ID = 1009
    const val PACE_SESSION_ID = 1012

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
    fun notifyLimit(context: Context, title: String, message: String, notificationId: Int = NOTIFICATION_ID) {
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
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    /** Construye la notificación permanente (ongoing) con el consumo actual. */
    fun buildPersistent(context: Context, data: UsageData?): Notification {
        val title = if (data != null) {
            context.getString(R.string.persistent_title_weekly, formatPercent(data.weeklyPercent))
        } else {
            context.getString(R.string.persistent_title_updating)
        }
        val text = if (data != null) {
            context.getString(R.string.persistent_text_session, formatPercent(data.sessionPercent), data.plan)
        } else {
            context.getString(R.string.checking_usage)
        }
        val body = if (data != null) {
            val time = Instant.ofEpochMilli(System.currentTimeMillis())
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"))
            val mode = resetDisplayMode(context)
            val locale = context.resources.configuration.locales[0]
            val deficit = context.getString(R.string.balance_deficit)
            val surplus = context.getString(R.string.balance_surplus)
            val now = Instant.now()
            buildString {
                append(context.getString(R.string.persistent_body_week_session, formatPercent(data.weeklyPercent), formatPercent(data.sessionPercent)))
                formatReset(data.weeklyResetAt, mode, strings = resetStrings(context), locale = locale)?.let {
                    append(context.getString(R.string.persistent_body_week_reset, it + balanceSuffix(data.weeklyPercent, data.weeklyResetAt, HistoryPeriod.WEEK.duration, now, deficit, surplus)))
                }
                formatReset(data.sessionResetAt, mode, strings = resetStrings(context), locale = locale)?.let {
                    append(context.getString(R.string.persistent_body_session_reset, it + balanceSuffix(data.sessionPercent, data.sessionResetAt, HistoryPeriod.SESSION.duration, now, deficit, surplus)))
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
                if (data != null) context.getString(R.string.chip_weekly, formatPercent(data.weeklyPercent)) else "Ollama",
            )
            if (data != null) {
                putInt("android.ongoingActivityNoti.progress", data.weeklyPercent.toInt().coerceIn(0, 100))
                putInt("android.ongoingActivityNoti.progressMax", 100)
                putString("android.ongoingActivityNoti.nowbarPrimaryInfo", context.getString(R.string.chip_weekly, formatPercent(data.weeklyPercent)))
                putString("android.ongoingActivityNoti.nowbarSecondaryInfo", context.getString(R.string.nowbar_session, formatPercent(data.sessionPercent)))
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
            Intent().setClassName(context, "${context.packageName}.MainActivity"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private const val UPDATE_NOTIFICATION_ID = 1003
    const val DAILY_SUMMARY_NOTIFICATION_ID = 1004
    private const val COOKIE_EXPIRY_NOTIFICATION_ID = 1005
    private const val SESSION_RESET_NOTIFICATION_ID = 1006

    /** Resumen diario programado (Feature B lote 2): % semana/sesión + proyección. */
    @SuppressLint("MissingPermission") // canNotify() verifica el permiso antes
    fun notifyDailySummary(context: Context, text: String) {
        if (!canNotify(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(context.getString(R.string.daily_summary_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(DAILY_SUMMARY_NOTIFICATION_ID, notification)
        }
    }

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

    /**
     * Recordatorio proactivo de cookie (issue #62): avisa que la cookie
     * expira pronto o ya expiró. El tap abre la app, donde el banner
     * persistente ofrece el CTA "Renovar ahora" que abre el WebView de login.
     */
    @SuppressLint("MissingPermission") // canNotify() verifica el permiso antes
    fun notifyCookieExpiry(context: Context, expired: Boolean, daysRemaining: Long) {
        if (!canNotify(context)) return
        val title = if (expired) {
            context.getString(R.string.cookie_expired_notification_title)
        } else {
            context.getString(R.string.cookie_expiring_notification_title)
        }
        val message = if (expired) {
            context.getString(R.string.cookie_expired_notification_message)
        } else {
            context.getString(R.string.cookie_expiring_notification_message, daysRemaining)
        }
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
            NotificationManagerCompat.from(context).notify(COOKIE_EXPIRY_NOTIFICATION_ID, notification)
        }
    }

    /** Notificación proactiva de reset de sesión (issue #57): avisa 1h antes. */
    @SuppressLint("MissingPermission") // canNotify() verifica el permiso antes
    fun notifySessionResetSoon(context: Context, percent: Double) {
        if (!canNotify(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.session_reset_soon_title))
            .setContentText(context.getString(R.string.session_reset_soon_message, formatPercent(percent)))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.session_reset_soon_message, formatPercent(percent))))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(SESSION_RESET_NOTIFICATION_ID, notification)
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
            SecurePrefs.get(context).getString(PrefsKeys.RESET_DISPLAY, null)
                ?.let { name -> ResetDisplayMode.entries.firstOrNull { it.name == name } }
        }.getOrNull() ?: ResetDisplayMode.COUNTDOWN

    /** Templates localizados para [formatReset]. */
    private fun resetStrings(context: Context): ResetStrings = ResetStrings(
        resetsSoon = context.getString(R.string.reset_soon),
        resetsIn = context.getString(R.string.reset_in),
        lessThanMin = context.getString(R.string.reset_less_than_min),
        resetsOn = context.getString(R.string.reset_on),
    )

    /** Sufijo de balanza para una línea de reset: " · Déficit 8%" o vacío. */
    private fun balanceSuffix(
        percent: Double,
        resetAt: Instant?,
        duration: Duration,
        now: Instant,
        deficit: String,
        surplus: String,
    ): String {
        val balance = computeBalance(percent, resetAt, now, duration) ?: return ""
        val label = balanceLabel(balance, deficit, surplus) ?: return ""
        return " · $label"
    }

}
