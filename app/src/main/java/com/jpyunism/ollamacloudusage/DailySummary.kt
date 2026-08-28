package com.jpyunism.ollamacloudusage

import java.time.Instant
import java.time.ZoneId

/**
 * Programación y texto del resumen diario (Feature B lote 2, issue #19).
 * Funciones puras, testeables en JVM.
 */

/** Clave de prefs con el próximo run programado (para evitar reprogramar de más). */
const val NEXT_DAILY_RUN_KEY = "daily_summary_next_run"

/**
 * Próxima ocurrencia de la hora [hour]:[minute] en [zone], estrictamente
 * posterior a [now] (si es exactamente ahora, corre mañana).
 * Devuelve el epoch millis del próximo run.
 */
fun nextDailyRunMillis(
    hour: Int,
    minute: Int,
    now: Long,
    zone: ZoneId,
): Long {
    val zdt = Instant.ofEpochMilli(now).atZone(zone)
    var candidate = zdt.toLocalDate().atTime(hour, minute).atZone(zone)
    if (!candidate.toInstant().isAfter(Instant.ofEpochMilli(now))) {
        candidate = candidate.plusDays(1)
    }
    return candidate.toInstant().toEpochMilli()
}

/**
 * Texto del resumen diario (REQ-112): "Semana: X% · Sesión: Y%" y, si hay
 * proyección semanal, " · A este ritmo: Z%". Con [data] null (sin datos)
 * devuelve null (REQ-113: no se notifica nada).
 */
fun dailySummaryText(data: UsageData?, projectionToPercent: Double?): String? {
    data ?: return null
    val base = "Semana: ${formatPercent(data.weeklyPercent)}% · Sesión: ${formatPercent(data.sessionPercent)}%"
    val projection = projectionToPercent?.let { " · A este ritmo: ${formatPercent(it)}%" } ?: ""
    return base + projection
}