package com.jpyunism.ollamacloudusage

/**
 * Logica pura del recordatorio proactivo de cookie (issue #62).
 *
 * La cookie de sesion de ollama.com expira periodicamente. Estimamos la
 * expiracion a partir de la ultima renovacion conocida ([renewedAtMillis])
 * y una vida util configurable ([lifetimeDays], default 7 dias). Sin
 * dependencias Android: testeable en JVM.
 */
object CookieExpiry {

    /** Vida util estimada de la cookie en dias (default). */
    const val DEFAULT_LIFETIME_DAYS = 7L

    /** Umbral de aviso: banner solo cuando quedan menos de estos dias. */
    const val WARN_THRESHOLD_DAYS = 3L

    private const val MS_PER_DAY = 24L * 60L * 60L * 1000L

    enum class Status {
        /** Cookie valida y con margen suficiente. */
        OK,

        /** Quedan menos de [WARN_THRESHOLD_DAYS] dias: avisar con CTA. */
        EXPIRING_SOON,

        /** La cookie ya expiro (o no hay renovacion conocida). */
        EXPIRED,
    }

    data class Result(
        val status: Status,
        /** Dias restantes estimados (0 si expiro o no hay renovacion). */
        val daysRemaining: Long,
    )

    /**
     * Evalua el estado de la cookie. Sin renovacion conocida devuelve
     * [Status.EXPIRED] (no podemos confiar en ella). Con renovacion, calcula
     * los dias restantes y aplica el umbral de [WARN_THRESHOLD_DAYS].
     */
    fun evaluate(
        renewedAtMillis: Long?,
        nowMillis: Long,
        lifetimeDays: Long = DEFAULT_LIFETIME_DAYS,
    ): Result {
        if (renewedAtMillis == null || renewedAtMillis <= 0) {
            return Result(Status.EXPIRED, 0)
        }
        val lifetimeMillis = lifetimeDays * MS_PER_DAY
        val remainingMillis = (renewedAtMillis + lifetimeMillis) - nowMillis
        if (remainingMillis <= 0) {
            return Result(Status.EXPIRED, 0)
        }
        val daysRemaining = remainingMillis / MS_PER_DAY
        return if (daysRemaining < WARN_THRESHOLD_DAYS) {
            Result(Status.EXPIRING_SOON, daysRemaining)
        } else {
            Result(Status.OK, daysRemaining)
        }
    }
}
