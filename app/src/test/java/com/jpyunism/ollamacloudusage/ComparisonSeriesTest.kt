package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Tests de la comparativa semana actual vs anterior (Feature D, issue #18).
 */
class ComparisonSeriesTest {

    // Reset semanal real: domingo 21:00 CLT = lunes 01:00 UTC.
    private val reset = Instant.parse("2026-08-10T01:00:00Z").toEpochMilli()
    private val week = HistoryPeriod.WEEK

    private fun snap(ts: String, weekly: Double) =
        UsageSnapshot(Instant.parse(ts).toEpochMilli(), 0.0, weekly)

    @Test
    fun `serie previa recortada a las horas transcurridas de la actual`() {
        // Semana actual empezó el 10-ago 01:00 UTC; "now" = 36 h después.
        val now = reset + 36L * 3600_000
        val data = listOf(
            snap("2026-08-03T13:00:00Z", 20.0), // semana previa, a +12h del inicio de esa semana
            snap("2026-08-04T13:00:00Z", 35.0), // semana previa, a +36h
            snap("2026-08-05T13:00:00Z", 50.0), // semana previa, a +60h -> fuera del recorte
            snap("2026-08-10T13:00:00Z", 12.0), // actual, a +12h
            snap("2026-08-11T13:00:00Z", 25.0), // actual, a +36h
        )
        val (cur, prev) = comparisonSeries(data, week, reset, now)
        assertEquals(listOf(12.0, 25.0), cur.map { it.second })
        // La previa solo incluye puntos hasta +36h.
        assertEquals(listOf(20.0, 35.0), prev.map { it.second })
        // Horas desde el inicio de su propio período.
        assertEquals(12.0, cur.first().first, 0.001)
        assertEquals(12.0, prev.first().first, 0.001)
    }

    @Test
    fun `sin datos de la semana anterior devuelve listas vacias`() {
        val now = reset + 24L * 3600_000
        val data = listOf(snap("2026-08-10T12:00:00Z", 10.0))
        val (cur, prev) = comparisonSeries(data, week, reset, now)
        assertEquals(1, cur.size)
        assertTrue(prev.isEmpty())
    }

    @Test
    fun `menos de 2 snapshots previos en el recorte no dibuja nada`() {
        val now = reset + 12L * 3600_000
        val data = listOf(
            snap("2026-08-04T12:00:00Z", 20.0), // a +12h de su semana
            snap("2026-08-10T12:00:00Z", 10.0), // actual a +12h... previa queda con 1 punto
        )
        val (_, prev) = comparisonSeries(data, week, reset, now)
        assertTrue(prev.size < 2)
    }
}