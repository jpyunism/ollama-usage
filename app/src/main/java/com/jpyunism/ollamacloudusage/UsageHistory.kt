package com.jpyunism.ollamacloudusage

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
    WEEK(7 * 24 * 60 * 60 * 1000L),
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
