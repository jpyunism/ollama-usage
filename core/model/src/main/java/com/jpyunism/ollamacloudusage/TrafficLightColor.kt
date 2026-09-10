package com.jpyunism.ollamacloudusage

/**
 * Colores ARGB del semáforo de consumo (Feature C, issue #28) como Int
 * puros: los usa el widget vía RemoteViews (que no accede al tema Compose)
 * y la app los convierte a Compose Color en ui/Theme.kt. Fuente única del
 * criterio de color; Material 3 no incluye semáforo en su palette
 * (excepción justificada a la regla de solo M3, precedente: paleta del widget).
 */
object TrafficLightColor {
    const val GREEN = 0xFF2E7D32.toInt()
    const val AMBER = 0xFFF9A825.toInt()
    const val RED = 0xFFC62828.toInt()

    /** Color ARGB para el nivel del semáforo calculado con [TrafficLight]. */
    fun colorFor(level: TrafficLightLevel): Int = when (level) {
        TrafficLightLevel.GREEN -> GREEN
        TrafficLightLevel.AMBER -> AMBER
        TrafficLightLevel.RED -> RED
    }
}