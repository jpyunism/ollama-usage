package com.jpyunism.ollamacloudusage

import java.time.Duration
import java.time.Instant

/**
 * Proyeccion de agotamiento de cuota (issue #54).
 *
 * Logica pura y testeable sin Android: a partir de los snapshots del
 * historico calcula el ritmo de consumo (regresion lineal simple sobre los
 * ultimos N snapshots) y lo traduce en una proyeccion informativa:
 * tendencia, % por hora, horas hasta agotar la cuota y % estimado al
 * momento del reset.
 *
 * La proyeccion es SOLO informativa: no dispara alertas ni cambia el
 * comportamiento del refresh.
 */
object ProjectionEngine {

    /** Tendencia del consumo segun el ritmo calculado. */
    enum class Trend { RISING, STABLE, FALLING }

    /**
     * Resultado de la proyeccion para un periodo (sesion o semana).
     *
     * - [trend]: RISING si el consumo crece, FALLING si decrece, STABLE si
     *   se mantiene (umbrales de +/-0.5 %/h).
     * - [percentPerHour]: ritmo calculado en % por hora.
     * - [hoursToTarget]: horas hasta llegar a [targetPercent] (null si el
     *   ritmo no es creciente).
     * - [percentAtReset]: % estimado al momento del reset (null si no hay
     *   reset conocido); clampeado a [0, 100].
     */
    data class Result(
        val trend: Trend,
        val percentPerHour: Double,
        val hoursToTarget: Double?,
        val percentAtReset: Double?,
    )

    /**
     * Calcula la proyeccion sobre [snapshots]. Devuelve null si hay menos
     * de 3 snapshots (datos insuficientes) o si no se puede calcular el
     * ritmo (varianza cero en X).
     *
     * [selector] elige el campo de consumo a proyectar (semana por defecto;
     * pasar `{ it.sessionPercent }` para la sesion).
     */
    fun project(
        snapshots: List<UsageSnapshot>,
        targetPercent: Int = 100,
        resetAt: Instant?,
        now: Instant = Instant.now(),
        selector: (UsageSnapshot) -> Double = { it.weeklyPercent },
    ): Result? {
        if (snapshots.size < 3) return null
        // Ventana: ultimos 7 dias de snapshots (1/h) o todos los disponibles.
        val recent = snapshots.takeLast(minOf(snapshots.size, 7 * 24))
        val rate = linearRate(recent, selector) ?: return null
        val trend = when {
            rate > 0.5 -> Trend.RISING
            rate < -0.5 -> Trend.FALLING
            else -> Trend.STABLE
        }
        val current = selector(snapshots.last())
        val hoursToTarget = if (rate > 0) (targetPercent - current) / rate else null
        val percentAtReset = resetAt?.let {
            val hoursToReset = Duration.between(now, it).toMinutes() / 60.0
            (current + rate * hoursToReset).coerceIn(0.0, 100.0)
        }
        return Result(trend, rate, hoursToTarget, percentAtReset)
    }

    /**
     * Regresion lineal (minimos cuadrados) del % de consumo vs. horas desde
     * el primer snapshot. Devuelve la pendiente en %/hora, o null si no hay
     * al menos 2 snapshots o la varianza en X es cero.
     */
    private fun linearRate(
        snapshots: List<UsageSnapshot>,
        selector: (UsageSnapshot) -> Double,
    ): Double? {
        if (snapshots.size < 2) return null
        val x0 = snapshots.first().timestampMillis.toDouble()
        val n = snapshots.size.toDouble()
        val sumX = snapshots.sumOf { (it.timestampMillis - x0) / 3_600_000.0 }
        val sumY = snapshots.sumOf { selector(it) }
        val sumXY = snapshots.sumOf { ((it.timestampMillis - x0) / 3_600_000.0) * selector(it) }
        val sumX2 = snapshots.sumOf {
            val x = (it.timestampMillis - x0) / 3_600_000.0
            x * x
        }
        val denom = n * sumX2 - sumX * sumX
        if (denom == 0.0) return null
        return (n * sumXY - sumX * sumY) / denom
    }
}
