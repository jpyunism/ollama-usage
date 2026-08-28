package com.jpyunism.ollamacloudusage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Único punto de entrada del refresco de consumo. Encapsula la resolución de
 * auth (cookie vs API key), el fetch y TODOS los side-effects posteriores
 * (widget, notificación persistente, alertas de umbral, histórico,
 * last_updated).
 *
 * Los tres caminos de refresco — [UsageViewModel], [UsageWorker] y
 * [UsageMonitorService] — invocan [refreshAndPropagate]; la lógica de negocio
 * vive aquí, en una sola parte.
 *
 * Los side-effects Android (widget, notificaciones) se inyectan como
 * funciones para poder testear el pipeline sin framework.
 */
class UsageRepository(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val scraper: UsageScraper,
    private val apiScraper: UsageScraper,
    private val historyStore: UsageHistoryStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
    // ── Side-effects inyectables (defaults = implementación real) ──
    private val widgetSaver: (Context, UsageData) -> Unit = UsageWidgetProvider::saveData,
    private val widgetUpdater: (Context) -> Unit = { UsageWidgetProvider.updateAll(it) },
    private val persistentShower: (Context, UsageData) -> Unit = UsageNotifier::showPersistent,
    private val persistentHider: (Context) -> Unit = UsageNotifier::hidePersistent,
    private val alertNotifier: (Context, String, String) -> Unit = UsageNotifier::notifyLimit,
) {

    /** true si hay credencial configurada para el método de auth actual. */
    fun hasAuth(): Boolean = when (authSource()) {
        AuthSource.COOKIE -> prefs.contains(PrefsKeys.COOKIE)
        AuthSource.API_KEY -> prefs.contains(PrefsKeys.API_KEY)
    }

    fun authSource(): AuthSource =
        prefs.getString(PrefsKeys.AUTH_SOURCE, null)
            ?.let { name -> AuthSource.entries.firstOrNull { it.name == name } }
            ?: AuthSource.COOKIE

    /** Valor del secreto guardado para el método indicado (vacío si no existe). */
    fun currentSecret(source: AuthSource): String = when (source) {
        AuthSource.COOKIE -> prefs.getString(PrefsKeys.COOKIE, null).orEmpty()
        AuthSource.API_KEY -> prefs.getString(PrefsKeys.API_KEY, null).orEmpty()
    }

    /**
     * Ejecuta el pipeline completo de refresco en [ioDispatcher]:
     * fetch → widget → notif persistente → alertas → histórico → last_updated.
     * Devuelve [Result.success] con el [UsageData] o [Result.failure] con un
     * [UsageError].
     */
    suspend fun refreshAndPropagate(): Result<UsageData> = withContext(ioDispatcher) {
        val authSource = authSource()
        // Multi-cuenta (Feature A lote 2): con lista de cuentas API key, la
        // credencial y el histórico vienen de la cuenta activa (REQ-102/103).
        // Sin cuentas (cookie o API key única legacy) funciona como antes.
        val accountStore = AccountStore(prefs)
        val activeAccount = if (authSource == AuthSource.API_KEY) accountStore.active() else null
        val credential = when (authSource) {
            AuthSource.COOKIE -> prefs.getString(PrefsKeys.COOKIE, null)
            AuthSource.API_KEY -> activeAccount?.apiKey ?: prefs.getString(PrefsKeys.API_KEY, null)
        }
        if (credential.isNullOrBlank()) {
            return@withContext Result.failure(UsageError.NoAuth)
        }

        val fetcher = if (authSource == AuthSource.API_KEY) apiScraper else scraper
        runCatching { fetcher.fetchUsage(credential) }.fold(
            onSuccess = { data ->
                propagate(data)
                Result.success(data)
            },
            onFailure = { Result.failure(UsageError.fromThrowable(it)) },
        )
    }

    /** Side-effects post-fetch. Se ejecuta solo tras un fetch exitoso. */
    private fun propagate(data: UsageData) {
        prefs.edit().putLong(PrefsKeys.LAST_UPDATED, now()).apply()

        // Widget del home screen: refleja el último consumo.
        widgetSaver(context, data)
        widgetUpdater(context)

        // Notificación permanente (pantalla de bloqueo) según preferencia.
        if (prefs.getBoolean(PrefsKeys.PERSISTENT_ENABLED, true)) {
            persistentShower(context, data)
        } else {
            persistentHider(context)
        }

        // Histórico local: acumula el snapshot de este refresh (con desglose
        // por modelo semanal, Feature C REQ-120/122).
        historyStore.record(
            data.sessionPercent,
            data.weeklyPercent,
            models = data.weeklyModels.associate { it.model to it.percent },
        )

        // Ancla semanal automática (Feature D, issue #15): si la fuente no
        // entrega weeklyResetAt real (método API key), se detecta el reset
        // real del histórico y se persiste. El override de la fuente real
        // siempre gana (REQ-032).
        updateWeeklyAnchor(data)

        // Alertas de umbral — solo si el usuario las activó.
        if (prefs.getBoolean(PrefsKeys.NOTIF_ENABLED, true)) {
            checkWeeklyThreshold(data)
            checkSessionThreshold(data)
            checkPaceAlerts()
        }
    }

    /**
     * Alerta temprana de ritmo (Feature A): notifica cuando la proyección del
     * período cruza 100% antes del reset. Una sola vez por período (guard en
     * prefs con el `start` del período notificado).
     */
    private fun checkPaceAlerts() {
        val snapshots = historyStore.load()

        fun check(period: HistoryPeriod, lastKey: String, titleRes: Int) {
            val anchor = dataAnchor(period) ?: fallbackResetAnchor(period, now())
            val alert = paceAlert(snapshots, period, anchor, now()) { p -> selectorOf(period, p) } ?: return
            val lastPeriod = prefs.getLong(lastKey, Long.MIN_VALUE)
            if (lastPeriod == alert.periodStart) return // ya notificado este período
            prefs.edit().putLong(lastKey, alert.periodStart).apply()
            alertNotifier(
                context,
                context.getString(titleRes),
                context.getString(R.string.pace_alert_message, formatPercent(alert.toPercent)),
            )
        }

        check(
            HistoryPeriod.WEEK,
            PrefsKeys.LAST_PACE_PERIOD_WEEK,
            R.string.pace_alert_title_weekly,
        )
        check(
            HistoryPeriod.SESSION,
            PrefsKeys.LAST_PACE_PERIOD_SESSION,
            R.string.pace_alert_title_session,
        )
    }

    /** Ancla real del período si la fuente la entrega (cookie/scraper); null con API key. */
    private fun dataAnchor(period: HistoryPeriod): Long? {
        // El ancla real llega por refresh anterior; el histórico no la guarda.
        // Se usa el fallback salvo que exista ancla persistida (Feature D).
        return prefs.getLong(prefsKeyAnchor(period), 0L).takeIf { it > 0 }
    }

    /**
     * Detecta y persiste el ancla semanal (Feature D). Solo aplica cuando la
     * fuente no entrega `weeklyResetAt` real (API key); con cookie/scraper el
     * valor real manda y aquí no se toca nada (REQ-032). Si la detección
     * encuentra un reset más reciente que el guardado, actualiza el prefs.
     */
    private fun updateWeeklyAnchor(data: UsageData) {
        if (data.weeklyResetAt != null) return
        val detected = detectWeeklyReset(historyStore.load()) ?: return
        val saved = prefs.getLong(PrefsKeys.WEEKLY_RESET_ANCHOR, 0L)
        if (detected > saved) {
            prefs.edit().putLong(PrefsKeys.WEEKLY_RESET_ANCHOR, detected).apply()
        }
    }

    /** Ancla semanal detectada automáticamente (para la UI), o null. */
    fun detectedWeeklyAnchor(): Long? = prefs.getLong(PrefsKeys.WEEKLY_RESET_ANCHOR, 0L).takeIf { it > 0 }

    private fun prefsKeyAnchor(period: HistoryPeriod): String = when (period) {
        HistoryPeriod.WEEK -> PrefsKeys.WEEKLY_RESET_ANCHOR
        HistoryPeriod.SESSION -> PrefsKeys.SESSION_RESET_ANCHOR
    }

    /** Selector de % según el período. */
    private fun selectorOf(period: HistoryPeriod, s: UsageSnapshot): Double = when (period) {
        HistoryPeriod.WEEK -> s.weeklyPercent
        HistoryPeriod.SESSION -> s.sessionPercent
    }

    private fun checkWeeklyThreshold(data: UsageData) {
        AlertEngine.checkThreshold(
            prefs = prefs,
            percent = data.weeklyPercent,
            alert = prefs.getInt(PrefsKeys.WEEKLY_ALERT, 80),
            critical = prefs.getInt(PrefsKeys.WEEKLY_CRITICAL, 95),
            lastKey = PrefsKeys.LAST_NOTIFIED_WEEKLY,
        ) { pct, level ->
            val title = if (level == AlertEngine.CRITICAL) {
                context.getString(R.string.weekly_critical_title)
            } else {
                context.getString(R.string.weekly_alert_title, formatPercent(pct))
            }
            alertNotifier(
                context,
                title,
                context.getString(R.string.weekly_alert_message, formatPercent(pct)),
            )
        }
    }

    private fun checkSessionThreshold(data: UsageData) {
        AlertEngine.checkThreshold(
            prefs = prefs,
            percent = data.sessionPercent,
            alert = prefs.getInt(PrefsKeys.SESSION_ALERT, 80),
            critical = prefs.getInt(PrefsKeys.SESSION_CRITICAL, 95),
            lastKey = PrefsKeys.LAST_NOTIFIED_SESSION,
        ) { pct, _ ->
            alertNotifier(
                context,
                context.getString(R.string.session_alert_title, formatPercent(pct)),
                context.getString(R.string.session_alert_message, formatPercent(pct)),
            )
        }
    }

    /** last_updated guardado (para la UI); null si nunca hubo refresh. */
    fun lastUpdated(): Long? = prefs.getLong(PrefsKeys.LAST_UPDATED, 0L).takeIf { it > 0 }

    /** Snapshots del histórico (el pipeline los registra en cada refresh). */
    fun historySnapshots(): List<UsageSnapshot> = historyStore.load()
}
