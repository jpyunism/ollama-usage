package com.jpyunism.ollamacloudusage.ui.settings

/**
 * Reglas del control de frecuencia de refresco (spec 12). Lógica pura, sin
 * Android, para poder testearla en JVM.
 *
 * El slider anterior (`valueRange = 1f..720f, steps = 30`) sólo permitía 31
 * posiciones: saltos de ~23 min y ningún valor redondo como 5, 10, 20, 45 o 90.
 */
enum class RefreshMode {
    /** Refresco casi en tiempo real: servicio en primer plano. */
    REAL_TIME,

    /** Compromiso entre frescura y batería. */
    BALANCED,

    /** Ahorro: WorkManager con intervalos largos. */
    SAVER,
}

object RefreshOptions {

    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 720

    /** Valor por defecto cuando no hay nada guardado. */
    const val DEFAULT_MINUTES = 15

    /** Atajos visibles; el campo numérico acepta cualquier minuto del rango. */
    val presets: List<Int> = listOf(1, 5, 10, 15, 30, 60, 120, 360, 720)

    /** Valor que fija cada modo al pulsarlo. */
    fun modeDefault(mode: RefreshMode): Int = when (mode) {
        RefreshMode.REAL_TIME -> 3
        RefreshMode.BALANCED -> 15
        RefreshMode.SAVER -> 120
    }

    /** Modo al que pertenece un valor (el modo no se guarda: se deriva). */
    fun modeFor(minutes: Int): RefreshMode = when {
        minutes <= 5 -> RefreshMode.REAL_TIME
        minutes <= 30 -> RefreshMode.BALANCED
        else -> RefreshMode.SAVER
    }

    /**
     * Convierte lo escrito en el campo a minutos válidos. Cualquier entrada que
     * no sea un entero dentro de rango deja el valor anterior: escribir "0" o
     * "abc" no debe cambiar el refresco a algo absurdo.
     */
    fun sanitize(input: String, fallback: Int): Int =
        input.trim().toIntOrNull()?.takeIf { it in MIN_MINUTES..MAX_MINUTES } ?: fallback

    /** Suma [delta] minutos sin salir del rango. */
    fun step(minutes: Int, delta: Int): Int =
        (minutes + delta).coerceIn(MIN_MINUTES, MAX_MINUTES)
}
