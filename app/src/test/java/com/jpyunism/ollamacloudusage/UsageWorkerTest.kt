package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageWorkerTest {

    // nextLevel: CRITICAL=1, ALERT=0, -1=no notificar

    @Test
    fun `cruza umbral de alerta notifica ALERT`() {
        assertEquals(0, UsageWorker.nextLevel(pct = 85, alert = 80, critical = 95, lastNotified = -1))
    }

    @Test
    fun `ya notificado en alerta no repite`() {
        assertEquals(-1, UsageWorker.nextLevel(pct = 90, alert = 80, critical = 95, lastNotified = 80))
    }

    @Test
    fun `supera critico notifica CRITICAL aunque ya haya alerta`() {
        assertEquals(1, UsageWorker.nextLevel(pct = 97, alert = 80, critical = 95, lastNotified = 80))
    }

    @Test
    fun `ya notificado en critico no repite`() {
        assertEquals(-1, UsageWorker.nextLevel(pct = 98, alert = 80, critical = 95, lastNotified = 95))
    }

    @Test
    fun `bajo el umbral resetea`() {
        assertEquals(-1, UsageWorker.nextLevel(pct = 60, alert = 80, critical = 95, lastNotified = 80))
    }

    @Test
    fun `umbrales custom del usuario`() {
        // Alerta a 70, crítico a 90
        assertEquals(0, UsageWorker.nextLevel(pct = 75, alert = 70, critical = 90, lastNotified = -1))
        assertEquals(1, UsageWorker.nextLevel(pct = 92, alert = 70, critical = 90, lastNotified = 70))
        assertEquals(-1, UsageWorker.nextLevel(pct = 65, alert = 70, critical = 90, lastNotified = 70))
    }

    @Test
    fun `en el limite exacto notifica`() {
        assertEquals(0, UsageWorker.nextLevel(pct = 80, alert = 80, critical = 95, lastNotified = -1))
        assertEquals(1, UsageWorker.nextLevel(pct = 95, alert = 80, critical = 95, lastNotified = 80))
    }
}
