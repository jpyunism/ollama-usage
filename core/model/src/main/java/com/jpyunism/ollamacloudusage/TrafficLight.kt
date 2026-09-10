package com.jpyunism.ollamacloudusage

/**
 * Semáforo de consumo por nivel (Feature C, issue #28): verde → ámbar → rojo
 * según cercanía al límite, con los umbrales de alerta/crítica de las
 * notificaciones (default 80/95). Función pura compartida por la app
 * (UsageMeterCard) y el widget (UsageWidgetProvider) para un criterio único.
 */
enum class TrafficLightLevel { GREEN, AMBER, RED }

object TrafficLight {

    /** Mapeo puro: verde < [alert], ámbar en [alert, critical), rojo >= [critical]. */
    fun paceColor(percent: Double, alert: Int = 80, critical: Int = 95): TrafficLightLevel = when {
        percent >= critical -> TrafficLightLevel.RED
        percent >= alert -> TrafficLightLevel.AMBER
        else -> TrafficLightLevel.GREEN
    }
}