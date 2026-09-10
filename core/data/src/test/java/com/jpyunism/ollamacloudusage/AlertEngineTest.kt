package com.jpyunism.ollamacloudusage

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertEngineTest {

    // nextLevel: CRITICAL=1, ALERT=0, -1=no notificar

    @Test
    fun `cruza umbral de alerta notifica ALERT`() {
        assertEquals(0, AlertEngine.nextLevel(pct = 85, alert = 80, critical = 95, lastNotified = -1))
    }

    @Test
    fun `ya notificado en alerta no repite`() {
        assertEquals(-1, AlertEngine.nextLevel(pct = 90, alert = 80, critical = 95, lastNotified = 80))
    }

    @Test
    fun `supera critico notifica CRITICAL aunque ya haya alerta`() {
        assertEquals(1, AlertEngine.nextLevel(pct = 97, alert = 80, critical = 95, lastNotified = 80))
    }

    @Test
    fun `ya notificado en critico no repite`() {
        assertEquals(-1, AlertEngine.nextLevel(pct = 98, alert = 80, critical = 95, lastNotified = 95))
    }

    @Test
    fun `bajo el umbral resetea`() {
        assertEquals(-1, AlertEngine.nextLevel(pct = 60, alert = 80, critical = 95, lastNotified = 80))
    }

    @Test
    fun `umbrales custom del usuario`() {
        assertEquals(0, AlertEngine.nextLevel(pct = 75, alert = 70, critical = 90, lastNotified = -1))
        assertEquals(1, AlertEngine.nextLevel(pct = 92, alert = 70, critical = 90, lastNotified = 70))
        assertEquals(-1, AlertEngine.nextLevel(pct = 65, alert = 70, critical = 90, lastNotified = 70))
    }

    @Test
    fun `en el limite exacto notifica`() {
        assertEquals(0, AlertEngine.nextLevel(pct = 80, alert = 80, critical = 95, lastNotified = -1))
        assertEquals(1, AlertEngine.nextLevel(pct = 95, alert = 80, critical = 95, lastNotified = 80))
    }

    // ─── checkThreshold: persiste el nivel notificado ───

    private fun prefsWith(lastNotified: Int): Pair<SharedPreferences, SharedPreferences.Editor> {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getInt(any(), any()) } returns lastNotified
        every { prefs.edit() } returns editor
        return prefs to editor
    }

    @Test
    fun `checkThreshold notifica ALERT y guarda el umbral de alerta`() {
        val (prefs, editor) = prefsWith(-1)
        var notified = -1
        AlertEngine.checkThreshold(prefs, percent = 85.0, alert = 80, critical = 95, lastKey = "k") { _, level ->
            notified = level
        }
        assertEquals(0, notified)
        verify { editor.putInt("k", 80) }
    }

    @Test
    fun `checkThreshold notifica CRITICAL y guarda el umbral critico`() {
        val (prefs, editor) = prefsWith(80)
        var notified = -1
        AlertEngine.checkThreshold(prefs, percent = 97.0, alert = 80, critical = 95, lastKey = "k") { _, level ->
            notified = level
        }
        assertEquals(1, notified)
        verify { editor.putInt("k", 95) }
    }

    @Test
    fun `checkThreshold bajo el umbral resetea sin notificar`() {
        val (prefs, editor) = prefsWith(80)
        var notified = -2
        AlertEngine.checkThreshold(prefs, percent = 60.0, alert = 80, critical = 95, lastKey = "k") { _, level ->
            notified = level
        }
        assertEquals(-2, notified)
        verify { editor.putInt("k", -1) }
    }
}
