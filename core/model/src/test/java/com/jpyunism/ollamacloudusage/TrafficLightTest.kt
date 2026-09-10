package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de la función pura del semáforo de consumo (Feature C, issue #28).
 * Bordes: verde < alerta, ámbar [alerta, crítica), rojo >= crítica.
 */
class TrafficLightTest {

    @Test
    fun `porcentaje bajo el umbral de alerta es verde`() {
        assertEquals(TrafficLightLevel.GREEN, TrafficLight.paceColor(0.0, 80, 95))
        assertEquals(TrafficLightLevel.GREEN, TrafficLight.paceColor(50.0, 80, 95))
        assertEquals(TrafficLightLevel.GREEN, TrafficLight.paceColor(79.9, 80, 95))
    }

    @Test
    fun `borde exacto del umbral de alerta es ámbar`() {
        assertEquals(TrafficLightLevel.AMBER, TrafficLight.paceColor(80.0, 80, 95))
    }

    @Test
    fun `entre alerta y crítica es ámbar`() {
        assertEquals(TrafficLightLevel.AMBER, TrafficLight.paceColor(85.0, 80, 95))
        assertEquals(TrafficLightLevel.AMBER, TrafficLight.paceColor(94.9, 80, 95))
    }

    @Test
    fun `borde exacto del umbral crítico es rojo`() {
        assertEquals(TrafficLightLevel.RED, TrafficLight.paceColor(95.0, 80, 95))
    }

    @Test
    fun `porcentaje sobre la crítica es rojo`() {
        assertEquals(TrafficLightLevel.RED, TrafficLight.paceColor(100.0, 80, 95))
        assertEquals(TrafficLightLevel.RED, TrafficLight.paceColor(120.0, 80, 95))
    }

    @Test
    fun `umbrales personalizados se respetan`() {
        // Con umbrales 60/90: 70 es ámbar (verde con los default 80/95)
        assertEquals(TrafficLightLevel.AMBER, TrafficLight.paceColor(70.0, 60, 90))
        assertEquals(TrafficLightLevel.GREEN, TrafficLight.paceColor(59.9, 60, 90))
        assertEquals(TrafficLightLevel.RED, TrafficLight.paceColor(90.0, 60, 90))
    }

    @Test
    fun `sin configuración usa los umbrales default 80 y 95`() {
        // REQ-023: el semáforo es visual, no depende de NOTIF_ENABLED;
        // los umbrales default de AlertSettings son 80/95.
        assertEquals(TrafficLightLevel.GREEN, TrafficLight.paceColor(79.0))
        assertEquals(TrafficLightLevel.AMBER, TrafficLight.paceColor(80.0))
        assertEquals(TrafficLightLevel.RED, TrafficLight.paceColor(95.0))
    }
}