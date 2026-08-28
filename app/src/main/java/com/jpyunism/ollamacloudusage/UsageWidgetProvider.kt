package com.jpyunism.ollamacloudusage

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Widget de home screen con el consumo actual de Ollama Cloud.
 *
 * Los datos se guardan en SharedPreferences claras (no son secretos) tras
 * cada refresh en segundo plano (WorkManager o servicio en primer plano).
 * El widget solo lee y renderiza — nunca hace red — y al tocarlo abre la app.
 */
class UsageWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context, loadData(context)))
        }
    }

    companion object {
        private const val KEY_DATA = "widget_usage_json"
        private const val PREFS_NAME = "widget_data"
        private const val KEY_ALERT = "widget_traffic_alert"
        private const val KEY_CRITICAL = "widget_traffic_critical"

        /** Prefs claras del widget: evita decrypt de SecurePrefs en el main thread. */
        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        /** Persiste el último consumo (y copia de umbrales del semáforo). */
        fun saveData(context: Context, data: UsageData) {
            val json = JSONObject().apply {
                put("weekly", data.weeklyPercent)
                put("session", data.sessionPercent)
                put("plan", data.plan)
                put("weeklyReset", data.weeklyResetAt?.toEpochMilli() ?: JSONObject.NULL)
                put("sessionReset", data.sessionResetAt?.toEpochMilli() ?: JSONObject.NULL)
            }.toString()
            prefs(context).edit().putString(KEY_DATA, json).apply()
            // Copia de umbrales del semáforo desde las prefs de la app (no
            // son secretos; EncryptedPrefs los pasa en claro).
            val appPrefs = context.getSharedPreferences(
                "ollama_usage_secure_v2", Context.MODE_PRIVATE,
            )
            prefs(context).edit()
                .putInt(KEY_ALERT, appPrefs.getInt(PrefsKeys.WEEKLY_ALERT, 80))
                .putInt(KEY_CRITICAL, appPrefs.getInt(PrefsKeys.WEEKLY_CRITICAL, 95))
                .apply()
        }

        /**
         * Umbrales del semáforo (default 80/95). Los umbrales de alerta viven
         * en las prefs de la app (no son secretos: EncryptedPrefs los deja
         * pasar en claro), pero leer ese archivo requiere la clave correcta;
         * para no acoplar el widget a SecurePrefs se copian a las prefs
         * claras del widget en cada updateAll.
         */
        private fun thresholds(context: Context): Pair<Int, Int> =
            prefs(context).getInt(KEY_ALERT, 80) to prefs(context).getInt(KEY_CRITICAL, 95)

        /** Re-renderiza todos los widgets instalados con los datos guardados. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, UsageWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context, loadData(context))
            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }

        private fun loadData(context: Context): UsageData? {
            val raw = prefs(context).getString(KEY_DATA, null) ?: return null
            return runCatching {
                val o = JSONObject(raw)
                UsageData(
                    sessionPercent = o.getDouble("session"),
                    weeklyPercent = o.getDouble("weekly"),
                    sessionResetAt = o.optLong("sessionReset", -1).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
                    weeklyResetAt = o.optLong("weeklyReset", -1).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
                    sessionModels = emptyList(),
                    weeklyModels = emptyList(),
                    plan = o.optString("plan", ""),
                )
            }.getOrNull()
        }

        private fun buildViews(context: Context, data: UsageData?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_usage)
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)

            if (data == null) {
                views.setTextViewText(R.id.widget_session, context.getString(R.string.checking_usage))
                views.setTextViewText(R.id.widget_plan, "")
                views.setTextViewText(R.id.widget_week, "")
                views.setTextViewText(R.id.widget_session_reset, "")
                views.setViewVisibility(R.id.widget_progress, View.GONE)
            } else {
                views.setTextViewText(
                    R.id.widget_session,
                    context.getString(R.string.widget_session, formatPercent(data.sessionPercent)),
                )
                views.setTextViewText(
                    R.id.widget_week,
                    context.getString(R.string.widget_week, formatPercent(data.weeklyPercent)),
                )
                views.setTextViewText(R.id.widget_plan, context.getString(R.string.widget_plan, data.plan))
                val reset = data.sessionResetAt?.let {
                    formatReset(
                        it,
                        ResetDisplayMode.COUNTDOWN,
                        ResetStrings(
                            resetsSoon = context.getString(R.string.reset_soon),
                            resetsIn = context.getString(R.string.reset_in),
                            lessThanMin = context.getString(R.string.reset_less_than_min),
                            resetsOn = context.getString(R.string.reset_on),
                        ),
                    )
                }
                val balance = computeBalance(
                    data.sessionPercent,
                    data.sessionResetAt,
                    Instant.now(),
                    HistoryPeriod.SESSION.duration,
                )
                val balanceText = balanceLabel(
                    balance,
                    context.getString(R.string.balance_deficit),
                    context.getString(R.string.balance_surplus),
                )
                views.setTextViewText(
                    R.id.widget_session_reset,
                    listOfNotNull(reset, balanceText).joinToString(" · "),
                )
                views.setProgressBar(
                    R.id.widget_progress,
                    100,
                    data.sessionPercent.roundToInt().coerceIn(0, 100),
                    false,
                )
                views.setViewVisibility(R.id.widget_progress, View.VISIBLE)
                // Semáforo de la barra (REQ-021): RemoteViews#setColorStateList
                // requiere API 31 (minSdk 26), así que se selecciona el drawable
                // clip del nivel (verde/ámbar/rojo) con la misma paleta que la app.
                val (alert, critical) = thresholds(context)
                val level = TrafficLight.paceColor(data.sessionPercent, alert, critical)
                views.setInt(
                    R.id.widget_progress,
                    "setProgressDrawable",
                    when (level) {
                        TrafficLightLevel.GREEN -> R.drawable.widget_progress_green
                        TrafficLightLevel.AMBER -> R.drawable.widget_progress_amber
                        TrafficLightLevel.RED -> R.drawable.widget_progress_red
                    },
                )
            }
            return views
        }

    }
}
