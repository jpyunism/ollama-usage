package com.jpyunism.ollamacloudusage

import java.time.Duration
import java.time.Instant

/**
 * Logica pura de la notificacion proactiva de reset de sesion (issue #57).
 * Decide el delay hasta el aviso (resetAt - 1h) y si el consumo actual
 * justifica notificar. Sin dependencias Android: testeable en JVM.
 */
object SessionResetScheduler {

    /** Cuanto antes del reset se avisa al usuario. */
    const val LEAD_HOURS = 1L

    /** Umbral minimo de consumo de sesion para avisar (> 70%). */
    const val MIN_PERCENT = 70.0

    /**
     * Delay (ms) hasta el momento de avisar (resetAt - 1h). Devuelve null si
     * no hay reset o si el aviso ya deberia haber ocurrido (delay <= 0), en
     * cuyo caso no corresponde programar.
     */
    fun delayUntilAlert(resetAt: Instant?, now: Instant = Instant.now()): Long? {
        if (resetAt == null) return null
        val alertAt = resetAt.minus(Duration.ofHours(LEAD_HOURS))
        val delay = Duration.between(now, alertAt).toMillis()
        return if (delay > 0) delay else null
    }

    /** true si el consumo de sesion justifica el aviso (> 70%). */
    fun shouldAlert(percent: Double): Boolean = percent > MIN_PERCENT
}
