package com.jpyunism.ollamacloudusage

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ModelUsage(
    val model: String,
    val requests: Long,
    val percent: Double,
)

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
): String? {
    if (resetAt == null) return null
    return when (mode) {
        ResetDisplayMode.DATE ->
            "resetea el ${resetAt.atZone(zone).format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("es")))}"

        ResetDisplayMode.COUNTDOWN -> {
            val diff = Duration.between(now, resetAt)
            when {
                diff.isNegative || diff.isZero -> "resetea pronto"
                diff.toMinutes() < 1 -> "resetea en <1 min"
                diff.toHours() < 1 -> "resetea en ${diff.toMinutes()} min"
                diff.toHours() < 24 -> "resetea en ${diff.toHours()} h ${diff.toMinutes() % 60} min"
                else -> "resetea en ${diff.toDays()} d ${diff.toHours() % 24} h"
            }
        }
    }
}
