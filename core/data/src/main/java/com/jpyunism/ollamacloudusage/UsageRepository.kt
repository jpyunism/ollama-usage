package com.jpyunism.ollamacloudusage

import com.jpyunism.ollamacloudusage.core.ui.R
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

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
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val now: () -> Long = System::currentTimeMillis,
    // ── Side-effects inyectables (defaults = implementación real) ──
    private val widgetSaver: (Context, UsageData) -> Unit = UsageWidgetProvider::saveData,
    private val widgetUpdater: (Context) -> Unit = { UsageWidgetProvider.updateAll(it) },
    private val persistentShower: (Context, UsageData) -> Unit = UsageNotifier::showPersistent,
    private val persistentHider: (Context) -> Unit = UsageNotifier::hidePersistent,
    private val alertNotifier: (Context, String, String, Int) -> Unit = UsageNotifier::notifyLimit,
) {

    /**
     * Notificación proactiva de reset de sesión (issue #57): se reprograma en
     * cada refresh que actualice el resetAt. No-op por defecto; se inyecta
     * desde la capa :app (que depende de :core:notify) vía [setSessionResetScheduler].
     */
    @Volatile
    var sessionResetScheduler: (Context, Instant?, Double) -> Unit = { _, _, _ -> }

    /**
     * Estado de expiración de la cookie recalculado en cada refresh (issue
     * #78). Se cachea para que [cookieExpiryStatus] refleje la última
     * renovación conocida sin depender de un refresh previo exitoso: si el
     * usuario renueva la cookie y el fetch falla, el banner igual se
     * actualiza. Null hasta el primer cálculo.
     */
    @Volatile
    private var cachedCookieExpiry: CookieExpiry.Result? = null

    /** Conecta el programador real (SessionResetWorker) desde la capa app. */
    fun connectSessionResetScheduler(scheduler: (Context, Instant?, Double) -> Unit) {
        sessionResetScheduler = scheduler
    }


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
        // Issue #78: recalcular el estado de expiración de la cookie al inicio
        // de cada refresh. Si el usuario renovó la cookie (recordCookieRenewal)
        // y el fetch falla, el banner igual refleja el nuevo estado en vez de
        // quedarse mostrando "cookie expira pronto" con datos viejos. Se
        // invalida el cache para forzar el recálculo (no leer el valor viejo).
        cachedCookieExpiry = null
        cachedCookieExpiry = cookieExpiryStatus()
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
        val data = runCatching { fetcher.fetchUsage(credential) }
            .getOrElse { return@withContext Result.failure(UsageError.fromThrowable(it)) }
        propagate(data)
        Result.success(data)
    }

    /**
     * Fetch del consumo de UNA cuenta puntual para la comparativa multi-cuenta
     * (issue #93), SIN side-effects: no toca widget, notificaciones, alertas ni
     * historico. La comparativa es solo lectura; los efectos del pipeline
     * corresponden a la cuenta activa ([refreshAndPropagate]).
     *
     * Devuelve [Result.failure] con un [UsageError] si la key es invalida o
     * falla la red, aislando el error por cuenta. Devuelve
     * [Result.failure] con [UsageError.NoAuth] si el id no existe.
     */
    suspend fun fetchUsageForAccount(accountId: String): Result<UsageData> = withContext(ioDispatcher) {
        val account = AccountStore(prefs).list().firstOrNull { it.id == accountId }
            ?: return@withContext Result.failure(UsageError.NoAuth)
        if (account.apiKey.isBlank()) return@withContext Result.failure(UsageError.NoAuth)
        runCatching { apiScraper.fetchUsage(account.apiKey) }
            .fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(UsageError.fromThrowable(it)) },
            )
    }

    /** Side-effects post-fetch. Se ejecuta solo tras un fetch exitoso. */
    private suspend fun propagate(data: UsageData) {
        prefs.edit().putLong(PrefsKeys.LAST_UPDATED, now()).apply()

        // Widget del home screen: refleja el último consumo.
        // `AppWidgetManager.updateAppWidget()` exige main thread en Android
        // < 12 (issue #79); si se invoca desde el ioDispatcher la actualización
        // falla silenciosamente y el widget no se refresca. Por eso los
        // side-effects del widget corren en el main dispatcher.
        withContext(mainDispatcher) {
            widgetSaver(context, data)
            widgetUpdater(context)
        }

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

        // Notificación proactiva de reset de sesión (issue #57): reprograma
        // el aviso 1h antes del resetAt en cada refresh que lo actualice.
        sessionResetScheduler(context, data.sessionResetAt, data.sessionPercent)
    }

    /**
     * Alerta temprana de ritmo (Feature A): notifica cuando la proyección del
     * período cruza 100% antes del reset. Una sola vez por período (guard en
     * prefs con el `start` del período notificado).
     */
    private fun checkPaceAlerts() {
        val snapshots = historyStore.load()

        fun check(period: HistoryPeriod, lastKey: String, titleRes: Int, notificationId: Int) {
            // Sin ancla real no se puede calcular el inicio del período: saltear
            // (issue #74). Con API key el fallback de sesión es null y el guard
            // nunca coincidiría → la alerta se dispararía en cada refresh.
            val anchor = dataAnchor(period) ?: fallbackResetAnchor(period, now())
            if (anchor == null) return
            val alert = paceAlert(snapshots, period, anchor, now()) { p -> selectorOf(period, p) } ?: return
            // Guard: el start del período notificado (estable con anchor real).
            val lastPeriod = prefs.getLong(lastKey, Long.MIN_VALUE)
            if (lastPeriod == alert.periodStart) return // ya notificado este período
            prefs.edit().putLong(lastKey, alert.periodStart).apply()
            alertNotifier(
                context,
                context.getString(titleRes),
                context.getString(R.string.pace_alert_message, formatPercent(alert.toPercent)),
                notificationId,
            )
        }

        check(
            HistoryPeriod.WEEK,
            PrefsKeys.LAST_PACE_PERIOD_WEEK,
            R.string.pace_alert_title_weekly,
            UsageNotifier.PACE_WEEKLY_ID,
        )
        check(
            HistoryPeriod.SESSION,
            PrefsKeys.LAST_PACE_PERIOD_SESSION,
            R.string.pace_alert_title_session,
            UsageNotifier.PACE_SESSION_ID,
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
                UsageNotifier.WEEKLY_ALERT_ID,
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
                UsageNotifier.SESSION_ALERT_ID,
            )
        }
    }

    /** last_updated guardado (para la UI); null si nunca hubo refresh. */
    fun lastUpdated(): Long? = prefs.getLong(PrefsKeys.LAST_UPDATED, 0L).takeIf { it > 0 }

    /** Snapshots del histórico (el pipeline los registra en cada refresh). */
    fun historySnapshots(): List<UsageSnapshot> = historyStore.load()

    // ── Recordatorio proactivo de cookie (issue #62) ──

    /** Timestamp de la última renovación conocida de la cookie; null si nunca. */
    fun cookieRenewedAt(): Long? =
        prefs.getLong(PrefsKeys.COOKIE_RENEWED_AT, 0L).takeIf { it > 0 }

    /** Registra la renovación de la cookie (se llama al guardar una nueva). */
    fun recordCookieRenewal() {
        prefs.edit().putLong(PrefsKeys.COOKIE_RENEWED_AT, now()).apply()
        // Issue #78: refrescar el estado cacheado de inmediato para que el
        // banner deje de mostrar "cookie expira pronto" sin esperar un refresh.
        // Se invalida el cache antes de recalcular para no leer el valor viejo.
        cachedCookieExpiry = null
        cachedCookieExpiry = cookieExpiryStatus()
    }

    /**
     * Estado de expiración de la cookie según la última renovación conocida.
     * Solo aplica cuando el método de auth es cookie; con API key devuelve OK
     * (no molesta, criterio de aceptación del issue #62).
     *
     * Devuelve el valor cacheado (recalculado en cada refresh y en cada
     * renovación) si existe; si no, lo calcula al vuelo.
     */
    fun cookieExpiryStatus(): CookieExpiry.Result {
        if (authSource() != AuthSource.COOKIE) return CookieExpiry.Result(CookieExpiry.Status.OK, 0)
        return cachedCookieExpiry ?: CookieExpiry.evaluate(cookieRenewedAt(), now()).also {
            cachedCookieExpiry = it
        }
    }

    /**
     * Evalúa la cookie y, si está por expirar o expiró, notifica al usuario
     * con el CTA para renovar. Solo si las notificaciones están activadas.
     * Se invoca desde [UsageWorker] y [UsageMonitorService] en cada ciclo.
     */
    fun notifyCookieExpiryIfNeeded() {
        if (!prefs.getBoolean(PrefsKeys.NOTIF_ENABLED, true)) return
        val status = cookieExpiryStatus()
        when (status.status) {
            CookieExpiry.Status.EXPIRING_SOON ->
                UsageNotifier.notifyCookieExpiry(context, expired = false, status.daysRemaining)
            CookieExpiry.Status.EXPIRED ->
                UsageNotifier.notifyCookieExpiry(context, expired = true, status.daysRemaining)
            CookieExpiry.Status.OK -> Unit
        }
    }
}
