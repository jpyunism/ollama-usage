package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Tests del resumen diario programado (Feature B lote 2, issue #19).
 * nextDailyRunMillis (programación) y dailySummaryText (texto).
 */
class DailySummaryTest {

    private val zone: ZoneId = ZoneId.of("America/Santiago")

    // ─── nextDailyRunMillis ───

    @Test
    fun `hora futura de hoy`() {
        // 2026-08-28T10:00 CLT; run a 21:00 → hoy 21:00.
        val now = Instant.parse("2026-08-28T14:00:00Z") // 10:00 CLT (UTC-4)
        val run = nextDailyRunMillis(21, 0, now.toEpochMilli(), zone)
        val expected = Instant.parse("2026-08-29T01:00:00Z") // 21:00 CLT
        assertEquals(expected.toEpochMilli(), run)
    }

    @Test
    fun `hora ya pasada corre mañana`() {
        // 2026-08-28T23:00 CLT (03:00Z del 29); run a 08:00 → mañana 29 a las 08:00 CLT.
        val now = Instant.parse("2026-08-29T03:00:00Z")
        val run = nextDailyRunMillis(8, 0, now.toEpochMilli(), zone)
        val expected = Instant.parse("2026-08-29T12:00:00Z") // 29 08:00 CLT
        assertEquals(expected.toEpochMilli(), run)
    }

    @Test
    fun `hora exactamente ahora programa para manana`() {
        // now == 21:00:00 exacto → corre mañana (evita doble disparo en el mismo instante).
        val now = Instant.parse("2026-08-29T01:00:00Z") // 21:00 CLT
        val run = nextDailyRunMillis(21, 0, now.toEpochMilli(), zone)
        val expected = Instant.parse("2026-08-30T01:00:00Z") // mañana 21:00 CLT
        assertEquals(expected.toEpochMilli(), run)
    }

    @Test
    fun `minutos se respetan`() {
        val now = Instant.parse("2026-08-28T14:00:00Z") // 10:00 CLT
        val run = nextDailyRunMillis(21, 30, now.toEpochMilli(), zone)
        val expected = Instant.parse("2026-08-29T01:30:00Z") // 21:30 CLT
        assertEquals(expected.toEpochMilli(), run)
    }

    // ─── dailySummaryText ───

    @Test
    fun `resumen con semana y sesion sin proyeccion`() {
        val data = UsageData(
            sessionPercent = 12.5,
            weeklyPercent = 41.7,
            sessionResetAt = null,
            sessionModels = emptyList(),
            weeklyModels = emptyList(),
            plan = "pro",
        )
        val text = dailySummaryText(data, projectionToPercent = null)
        assertEquals("Semana: 41.7% · Sesión: 12.5%", text)
    }

    @Test
    fun `resumen con proyeccion`() {
        val data = UsageData(50.0, 80.0, null, null, emptyList(), emptyList(), "pro")
        val text = dailySummaryText(data, projectionToPercent = 112.3)
        assertEquals("Semana: 80% · Sesión: 50% · A este ritmo: 112.3%", text)
    }

    @Test
    fun `porcentajes con un decimal`() {
        val data = UsageData(96.08, 41.72, null, null, emptyList(), emptyList(), "pro")
        val text = dailySummaryText(data, projectionToPercent = null)
        assertEquals("Semana: 41.7% · Sesión: 96.1%", text)
    }

    @Test
    fun `sin datos no hay texto`() {
        assertNull(dailySummaryText(null, projectionToPercent = null))
    }
}