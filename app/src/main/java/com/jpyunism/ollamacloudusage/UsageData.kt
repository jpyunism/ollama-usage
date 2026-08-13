package com.jpyunism.ollamacloudusage

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

data class ModelUsage(
    val model: String,
    val requests: Long,
    val percent: Double,
)

/**
 * Segmento de la barra de consumo: un modelo individual o el grupo "Otros".
 * [colorKey] = nombre del modelo para asignar color de la paleta;
 * `null` para "Otros" (color neutro del tema).
 */
data class UsageSegment(
    val label: String,
    val percent: Double,
    val modelCount: Int,
    val colorKey: String?,
)

/**
 * Ordena los modelos por % de uso descendente (tiebreak: requests desc).
 * Usado por la lista de modelos (detalle individual de todos).
 */
fun sortedByUsage(models: List<ModelUsage>): List<ModelUsage> =
    models.sortedWith(compareByDescending<ModelUsage> { it.percent }.thenByDescending { it.requests })

/**
 * Agrupa los modelos para la barra de consumo:
 * - Ordena por % desc (tiebreak requests desc).
 * - Modelos con % >= [threshold] → segmento individual.
 * - Modelos con % < [threshold]: si hay ≥ 2 → un segmento "Otros" con la suma
 *   de sus % y modelCount = N; si hay 1 solo → se mantiene individual.
 * - Si todos están bajo el umbral → no agrupa (la barra conserva los colores).
 * - "Otros" siempre va al final.
 */
fun groupModels(
    models: List<ModelUsage>,
    threshold: Double = 3.0,
    othersLabel: String = "Otros",
): List<UsageSegment> {
    val sorted = sortedByUsage(models)
    if (sorted.isEmpty()) return emptyList()

    val (small, big) = sorted.partition { it.percent < threshold }
    if (small.size >= 2 && big.isNotEmpty()) {
        return big.map { UsageSegment(it.model, it.percent, 1, it.model) } +
            UsageSegment(
                label = othersLabel,
                percent = small.sumOf { it.percent },
                modelCount = small.size,
                colorKey = null,
            )
    }
    return sorted.map { UsageSegment(it.model, it.percent, 1, it.model) }
}

data class UsageData(
    val sessionPercent: Double,
    val weeklyPercent: Double,
    val sessionResetAt: Instant?,
    val weeklyResetAt: Instant? = null,
    val sessionModels: List<ModelUsage>,
    val weeklyModels: List<ModelUsage>,
    val plan: String,
) {
    val sessionUsed: Boolean get() = sessionPercent > 0.0
    val weeklyUsed: Boolean get() = weeklyPercent > 0.0
}

/**
 * Formatea un porcentaje a un decimal: 96.08 → "96.1".
 * Los valores enteros se muestran sin decimales (100.0 → "100").
 */
fun formatPercent(value: Double): String {
    val rounded = (value * 10).roundToLong() / 10.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", rounded)
    }
}

/** Cómo mostrar el reset de cuota en la notificación y la UI. */
enum class ResetDisplayMode { COUNTDOWN, DATE }

/**
 * Formatea el reset de una cuota según el modo elegido.
 * - COUNTDOWN: tiempo restante ("resetea en 36 min").
 * - DATE: fecha y hora local ("resetea el 8 ago, 18:00").
 * Devuelve null si no hay fecha de reset.
 */
fun formatReset(
    resetAt: Instant?,
    mode: ResetDisplayMode,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String? {
    if (resetAt == null) return null
    val es = locale.language == "es"
    return when (mode) {
        ResetDisplayMode.DATE -> {
            val date = resetAt.atZone(zone).format(DateTimeFormatter.ofPattern("d MMM, HH:mm", locale))
            if (es) "resetea el $date" else "resets on $date"
        }

        ResetDisplayMode.COUNTDOWN -> {
            val diff = Duration.between(now, resetAt)
            val verb = if (es) "resetea en" else "resets in"
            when {
                diff.isNegative || diff.isZero -> if (es) "resetea pronto" else "resets soon"
                diff.toMinutes() < 1 -> "$verb <1 min"
                diff.toHours() < 1 -> "$verb ${diff.toMinutes()} min"
                diff.toHours() < 24 -> "$verb ${diff.toHours()} h ${diff.toMinutes() % 60} min"
                else -> "$verb ${diff.toDays()} d ${diff.toHours() % 24} h"
            }
        }
    }
}
