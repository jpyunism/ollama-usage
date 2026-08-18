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
 * Los datos se guardan en prefs cifradas ([saveData]) tras cada refresh en
 * segundo plano (WorkManager o servicio en primer plano). El widget solo lee
 * y renderiza — nunca hace red — y al tocarlo abre la app.
 */
class UsageWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context, loadData(context)))
        }
    }

    companion object {
        private const val KEY_DATA = "widget_usage_json"

        /** Persiste el último consumo para que el widget lo muestre. */
        fun saveData(context: Context, data: UsageData) {
            val json = JSONObject().apply {
                put("weekly", data.weeklyPercent)
                put("session", data.sessionPercent)
                put("plan", data.plan)
                put("weeklyReset", data.weeklyResetAt?.toEpochMilli() ?: JSONObject.NULL)
                put("sessionReset", data.sessionResetAt?.toEpochMilli() ?: JSONObject.NULL)
            }.toString()
            SecurePrefs.get(context).edit().putString(KEY_DATA, json).apply()
        }

        /** Re-renderiza todos los widgets instalados con los datos guardados. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, UsageWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context, loadData(context))
            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }

        private fun loadData(context: Context): UsageData? {
            val raw = SecurePrefs.get(context).getString(KEY_DATA, null) ?: return null
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
                val reset = data.sessionResetAt?.let { formatReset(it, ResetDisplayMode.COUNTDOWN) }
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
            }
            return views
        }

    }
}
