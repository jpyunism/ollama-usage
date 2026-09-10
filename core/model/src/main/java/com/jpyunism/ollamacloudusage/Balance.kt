package com.jpyunism.ollamacloudusage

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong

/** Estado de la balanza de consumo respecto al ritmo lineal esperado. */
enum class BalanceStatus {
    /** Consumimos más de lo esperado a esta altura del período. */
    DEFICIT,

    /** Consumimos menos de lo esperado a esta altura del período. */
    SURPLUS,
}

/** Resultado del cálculo de balanza: estado + desvío en puntos porcentuales. */
data class Balance(
    val status: BalanceStatus,
    val percentDelta: Double,
)

/**
 * Compara el consumo actual contra el ritmo lineal esperado del período.
 *
 * El período va de `resetAt - duration` hasta `resetAt` (p.ej. sesión 24 h,
 * semana 168 h con reset domingo 21:00 CLT). Devuelve:
 * - DEFICIT si consumimos más de lo esperado (delta > 0),
 * - SURPLUS si consumimos menos (delta < 0),
 * - null si no hay reset, si `now` está fuera del período, o si el desvío
 *   redondeado a 1 decimal es 0 (en ritmo).
 */
fun computeBalance(
    percent: Double,
    resetAt: Instant?,
    now: Instant,
    duration: Duration,
): Balance? {
    if (resetAt == null) return null
    val start = resetAt.minus(duration)
    if (now.isBefore(start) || !now.isBefore(resetAt)) return null

    val elapsedMillis = Duration.between(start, now).toMillis().toDouble()
    val totalMillis = duration.toMillis().toDouble()
    val expected = (elapsedMillis / totalMillis * 100.0).coerceIn(0.0, 100.0)
    val delta = percent - expected

    // Redondeo a 1 decimal (mismo criterio que formatPercent); 0 → en ritmo.
    val rounded = (delta * 10).roundToLong() / 10.0
    if (rounded == 0.0) return null

    return Balance(
        status = if (rounded > 0) BalanceStatus.DEFICIT else BalanceStatus.SURPLUS,
        percentDelta = kotlin.math.abs(rounded),
    )
}

/**
 * Formatea la balanza: "Déficit 8%" / "Superávit 5%".
 * Los templates reciben `%1$s` con el desvío ya formateado (formatPercent).
 */
fun balanceLabel(
    balance: Balance?,
    deficitTemplate: String,
    surplusTemplate: String,
): String? {
    if (balance == null) return null
    val percent = formatPercent(balance.percentDelta)
    return when (balance.status) {
        BalanceStatus.DEFICIT -> deficitTemplate.format(percent)
        BalanceStatus.SURPLUS -> surplusTemplate.format(percent)
    }
}
