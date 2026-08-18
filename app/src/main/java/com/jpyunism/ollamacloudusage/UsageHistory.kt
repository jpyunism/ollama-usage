package com.jpyunism.ollamacloudusage

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** Un registro puntual de consumo capturado en un refresh exitoso. */
data class UsageSnapshot(
    val timestampMillis: Long,
    val sessionPercent: Double,
    val weeklyPercent: Double,
)

/** Período de cuota: sesión (24 h) o semana (168 h). */
enum class HistoryPeriod(val durationMillis: Long) {
    SESSION(24 * 60 * 60 * 1000L),
    WEEK(7 * 24 * 60 * 60 * 1000L);

    /** Duración como java.time.Duration (para computeBalance/formatReset). */
    val duration: Duration
        get() = Duration.ofMillis(durationMillis)
}

/** Un período de cuota (sesión o semana) con sus snapshots. */
data class HistoryPeriodGroup(
    val start: Long,
    val end: Long,
    val snapshots: List<UsageSnapshot>,
)

/** Resumen de un período: pico de consumo (máx %) alcanzado antes del reset. */
data class PeriodSummary(
    val start: Long,
    val end: Long,
    val peakPercent: Double,
)

/** Resumen completo para la UI: último período cerrado + período actual. */
data class HistorySummary(
    val lastClosed: PeriodSummary?,
    val current: PeriodSummary?,
)

/** Una barra del gráfico "Consumo por período": pico de un período de cuota. */
data class PeriodBar(
    val start: Long,
    val end: Long,
    val peakPercent: Double,
    val inProgress: Boolean,
)

/**
 * Agrupa snapshots en períodos de cuota consecutivos.
 *
 * Si hay un reset conocido (`resetAnchor`), los períodos se alinean a él
 * (p.ej. semana = [reset-168h, reset)). Si no, se anclan al primer snapshot.
 * Los períodos vacíos intermedios se omiten (solo se devuelven los que tienen
 * al menos un snapshot).
 */
fun periodsFor(
    snapshots: List<UsageSnapshot>,
    period: HistoryPeriod,
    resetAnchor: Long?,
): List<HistoryPeriodGroup> {
    if (snapshots.isEmpty()) return emptyList()
    val first = snapshots.minOf { it.timestampMillis }
    val last = snapshots.maxOf { it.timestampMillis }
    val d = period.durationMillis

    // Ancla: último reset <= primer snapshot (o el primer snapshot si no hay reset).
    val anchor = resetAnchor?.let { r ->
        var a = r
        while (a > first) a -= d
        a
    } ?: first

    val groups = mutableListOf<HistoryPeriodGroup>()
    var start = anchor
    while (start <= last) {
        val end = start + d
        val inRange = snapshots.filter { it.timestampMillis in start until end }
        if (inRange.isNotEmpty()) {
            groups.add(HistoryPeriodGroup(start, end, inRange))
        }
        start = end
    }
    return groups
}

/**
 * Timestamps de los resets ya ocurridos dentro del rango de los snapshots
 * (para las líneas punteadas del gráfico). Camina hacia atrás desde el ancla
 * (próximo reset) y conserva los resets en (first, last].
 */
fun resetMarkers(
    snapshots: List<UsageSnapshot>,
    period: HistoryPeriod,
    resetAnchor: Long?,
): List<Long> {
    if (snapshots.isEmpty() || resetAnchor == null) return emptyList()
    val first = snapshots.minOf { it.timestampMillis }
    val last = snapshots.maxOf { it.timestampMillis }
    val d = period.durationMillis

    val markers = mutableListOf<Long>()
    var r = resetAnchor
    while (r > first) {
        if (r <= last) markers.add(r)
        r -= d
    }
    return markers.sorted()
}

/** Pico (máx %) de un período según el selector de campo. */
fun peakPercent(
    group: HistoryPeriodGroup,
    selector: (UsageSnapshot) -> Double,
): Double? = group.snapshots.maxOfOrNull(selector)

/**
 * Snapshot más cercano en el tiempo a `timestampMillis` (para el tooltip).
 */
fun nearestSnapshot(
    snapshots: List<UsageSnapshot>,
    timestampMillis: Long,
): UsageSnapshot? =
    snapshots.minByOrNull { abs(it.timestampMillis - timestampMillis) }

/**
 * Barras del gráfico "Consumo por período": una por período con datos, en
 * orden cronológico. El valor de cada barra es el pico (máx %) del período
 * ("cuánto se consumió antes del reset"). El período actual en curso se
 * incluye con `inProgress = true` (end > now).
 */
fun periodBars(
    snapshots: List<UsageSnapshot>,
    period: HistoryPeriod,
    resetAnchor: Long?,
    now: Long,
    selector: (UsageSnapshot) -> Double,
): List<PeriodBar> =
    periodsFor(snapshots, period, resetAnchor).map { g ->
        PeriodBar(
            start = g.start,
            end = g.end,
            peakPercent = peakPercent(g, selector) ?: 0.0,
            inProgress = g.end > now,
        )
    }

/**
 * Resumen para la UI: último período cerrado (pico de consumo antes de su
 * reset) y período actual en curso (consumo actual = pico hasta ahora).
 */
fun summarize(
    snapshots: List<UsageSnapshot>,
    period: HistoryPeriod,
    resetAnchor: Long?,
    now: Long,
    selector: (UsageSnapshot) -> Double,
): HistorySummary {
    if (snapshots.isEmpty()) return HistorySummary(null, null)
    val groups = periodsFor(snapshots, period, resetAnchor)

    val closed = groups.filter { it.end <= now }
    val lastClosed = closed.lastOrNull()?.let { g ->
        PeriodSummary(g.start, g.end, peakPercent(g, selector) ?: 0.0)
    }

    val current = groups.lastOrNull()?.takeIf { it.end > now }?.let { g ->
        PeriodSummary(g.start, g.end, peakPercent(g, selector) ?: 0.0)
    }

    return HistorySummary(lastClosed, current)
}

/**
 * Proyección lineal del consumo del período actual hasta el próximo reset:
 * si se siguiera consumiendo al ritmo actual, en [toTimestamp] (el reset) se
 * llegaría a [toPercent]. Sin clamp: puede superar 100.
 */
data class Projection(
    val fromTimestamp: Long,
    val fromPercent: Double,
    val toTimestamp: Long,
    val toPercent: Double,
)

/** Período de cuota en curso con la info para dibujar ideal + proyección. */
data class CurrentPeriod(
    val start: Long,
    val end: Long,
    val snapshotCount: Int,
    val projection: Projection?,
)
fun currentPeriod(
    snapshots: List<UsageSnapshot>,
    period: HistoryPeriod,
    resetAnchor: Long?,
    now: Long,
    selector: (UsageSnapshot) -> Double,
): CurrentPeriod? {
    if (resetAnchor == null || snapshots.isEmpty()) return null
    val d = period.durationMillis

    // Próximo reset estrictamente futuro (puede ser el propio anchor si > now).
    var end = resetAnchor
    while (end <= now) end += d
    val start = end - d

    val inPeriod = snapshots.filter { it.timestampMillis in start until end }
    return CurrentPeriod(
        start = start,
        end = end,
        snapshotCount = inPeriod.size,
        projection = linearProjection(inPeriod, end, selector),
    )
}

/**
 * Regresión lineal (mínimos cuadrados) del % de consumo vs. timestamp sobre
 * los snapshots del período actual, evaluada en [end] (próximo reset).
 * Requiere ≥ 2 snapshots; si no, devuelve null. El % proyectado no se
 * clampa (puede superar 100 para señalizar riesgo de exceder la cuota).
 */
fun linearProjection(
    snapshotsInPeriod: List<UsageSnapshot>,
    end: Long,
    selector: (UsageSnapshot) -> Double,
): Projection? {
    if (snapshotsInPeriod.size < 2) return null

    val last = snapshotsInPeriod.last()
    val n = snapshotsInPeriod.size.toDouble()
    val sumX = snapshotsInPeriod.sumOf { it.timestampMillis.toDouble() }
    val sumY = snapshotsInPeriod.sumOf { selector(it) }
    val sumXY = snapshotsInPeriod.sumOf { it.timestampMillis.toDouble() * selector(it) }
    val sumX2 = snapshotsInPeriod.sumOf { it.timestampMillis.toDouble() * it.timestampMillis.toDouble() }

    val denom = n * sumX2 - sumX * sumX
    // Con ≥2 snapshots de timestamps distintos el denominador es > 0.
    if (denom == 0.0) return null
    val slope = (n * sumXY - sumX * sumY) / denom
    val intercept = (sumY - slope * sumX) / n

    return Projection(
        fromTimestamp = last.timestampMillis,
        fromPercent = selector(last),
        toTimestamp = end,
        toPercent = slope * end + intercept,
    )
}

/**
 * Ancla de reset sintetizada cuando la fuente de datos no la entrega
 * (método de API key: la API /api/usage no expone resets).
 *
 * - Semana: próximo domingo 21:00 en [zone] (supuesto documentado del repo:
 *   la cuota semanal de Ollama Cloud se reinicia el domingo 21:00 CLT).
 * - Sesión: no sintetizable — sin ancla real la ventana móvil de 24 h se
 *   desliza continuamente y no tiene cierre; se devuelve null y la UI omite
 *   ideal/proyección (solo el scraper entrega resets de sesión reales).
 */
fun fallbackResetAnchor(
    period: HistoryPeriod,
    now: Long,
    zone: ZoneId = ZoneId.of("America/Santiago"),
): Long? = when (period) {
    HistoryPeriod.SESSION -> null
    HistoryPeriod.WEEK -> nextSundayAt21(now, zone)
}

/** Próximo domingo 21:00 en [zone] estrictamente posterior a `now`. */
private fun nextSundayAt21(now: Long, zone: ZoneId): Long {
    val zdt = Instant.ofEpochMilli(now).atZone(zone)
    // dayOfWeek: 1=Lunes … 7=Domingo.
    val daysUntilSunday = (7 - zdt.dayOfWeek.value) % 7
    var candidate = zdt.toLocalDate()
        .plusDays(daysUntilSunday.toLong())
        .atTime(21, 0)
        .atZone(zone)
    if (!candidate.toInstant().isAfter(Instant.ofEpochMilli(now))) {
        candidate = candidate.plusDays(7)
    }
    return candidate.toInstant().toEpochMilli()
}
