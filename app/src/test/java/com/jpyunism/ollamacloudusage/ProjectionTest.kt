package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ProjectionTest {

    private val hour = 60 * 60 * 1000L
    private val day = 24 * hour
    private val now = 100_000_000L

    /** Selector de campo de la semana para los tests. */
    private val weekly: (UsageSnapshot) -> Double = { it.weeklyPercent }

    /** Selector de campo de la sesión para los tests. */
    private val sessionSel: (UsageSnapshot) -> Double = { it.sessionPercent }

    private fun snapshots(vararg pairs: Pair<Long, Double>): List<UsageSnapshot> =
        pairs.map { (ts, pct) ->
            UsageSnapshot(
                timestampMillis = ts,
                sessionPercent = pct,
                weeklyPercent = pct,
            )
        }

    // ─────────── currentPeriod: perímetro del período actual ───────────

    @Test
    fun `sin anchor devuelve null`() {
        val s = snapshots(now - 2 * hour to 5.0, now - hour to 6.0)
        assertNull(currentPeriod(s, HistoryPeriod.WEEK, resetAnchor = null, now = now, selector = weekly))
    }

    @Test
    fun `con anchor el periodo actual va del reset pasado al proximo reset`() {
        // Semana: anchor = próximo reset conocido. now está dentro del
        // período [anchor - 168h, anchor).
        val anchor = now + 3 * hour
        val s = snapshots(now - 2 * hour to 40.0, now - hour to 50.0)

        val cp = currentPeriod(s, HistoryPeriod.WEEK, resetAnchor = anchor, now = now, selector = weekly)

        assertNotNull(cp)
        assertEquals(anchor - HistoryPeriod.WEEK.durationMillis, cp!!.start)
        assertEquals(anchor, cp.end)
        assertEquals(2, cp.snapshotCount)
    }

    @Test
    fun `proximo reset es el primero estrictamente mayor que now`() {
        // now justo 1 ms antes del próximo reset: end debe ser ESE reset.
        val reset = now + 1
        val s = snapshots(now - 2 * hour to 5.0)

        val cp = currentPeriod(s, HistoryPeriod.SESSION, resetAnchor = reset, now = now, selector = weekly)

        assertNotNull(cp)
        assertEquals(reset, cp!!.end)
        assertEquals(reset - HistoryPeriod.SESSION.durationMillis, cp.start)
    }

    @Test
    fun `snapshots de periodos anteriores quedan fuera del periodo actual`() {
        val anchor = now + hour
        val start = anchor - HistoryPeriod.WEEK.durationMillis
        // Uno dentro y uno muy anterior (período previo).
        val inside = start + 3 * hour
        val ancient = start - 2 * day
        val s = snapshots(ancient to 90.0, inside to 10.0)

        val cp = currentPeriod(s, HistoryPeriod.WEEK, resetAnchor = anchor, now = now, selector = weekly)

        assertNotNull(cp)
        assertEquals(1, cp!!.snapshotCount)
    }

    // ─── Proyección lineal ───

    @Test
    fun `proyeccion con dos puntos calcula la regresion lineal exacta`() {
        // Puntos (anchor, 0%) y (anchor+1000, 50%): pendiente 0.05 %/ms.
        // Proyección: desde el último snapshot hasta el próximo reset (end).
        val anchor = 10_000L
        val s = snapshots(
            anchor to 0.0,
            anchor + 1000 to 50.0,
        )
        val cp = currentPeriod(s, HistoryPeriod.SESSION, resetAnchor = anchor, now = anchor + 500, selector = weekly)
        val proj = cp!!.projection

        assertNotNull(proj)
        assertEquals(anchor + 1000, proj!!.fromTimestamp)
        assertEquals(50.0, proj.fromPercent, 1e-9)
        val end = anchor + HistoryPeriod.SESSION.durationMillis
        assertEquals(end, proj.toTimestamp)
        val expected = 0.05 * (end - anchor)
        assertEquals(expected, proj.toPercent, 1e-6)
    }

    @Test
    fun `proyeccion puede superar 100 sin clamp`() {
        // Puntos: (anchor, 80%) y (anchor+1000, 85%) → pendiente 0.005 %/ms,
        // que a 24 h proyecta muy por encima de 100.
        val anchor = 10_000L
        val s = snapshots(
            anchor to 80.0,
            anchor + 1000 to 85.0,
        )
        val proj = currentPeriod(s, HistoryPeriod.SESSION, resetAnchor = anchor, now = anchor + 500, selector = weekly)!!.projection

        assertNotNull(proj)
        val end = anchor + HistoryPeriod.SESSION.durationMillis
        val expected = 80.0 + 0.005 * (end - anchor)
        assertTrue("esperado $expected > 100", expected > 100)
        assertEquals(expected, proj!!.toPercent, 1e-6)
    }

    @Test
    fun `con un solo snapshot en el periodo no hay proyeccion`() {
        val anchor = now + hour
        val s = snapshots(anchor - hour to 33.0)

        val cp = currentPeriod(s, HistoryPeriod.SESSION, resetAnchor = anchor, now = now, selector = weekly)

        assertNotNull(cp)
        assertEquals(1, cp!!.snapshotCount)
        assertNull(cp.projection)
    }

    @Test
    fun `sin snapshots en el periodo actual no hay proyeccion`() {
        val anchor = now + hour
        val start = anchor - HistoryPeriod.WEEK.durationMillis
        val s = snapshots((start - day) to 10.0) // solo período anterior

        val cp = currentPeriod(s, HistoryPeriod.WEEK, resetAnchor = anchor, now = now, selector = weekly)

        assertNotNull(cp)
        assertEquals(0, cp!!.snapshotCount)
        assertNull(cp.projection)
    }

    // ─── Selector de campo (semana vs sesión) ───

    @Test
    fun `selector de sesion usa sessionPercent`() {
        val anchor = now + hour
        val s = listOf(
            UsageSnapshot(anchor - 2000, sessionPercent = 20.0, weeklyPercent = 80.0),
            UsageSnapshot(anchor - 1000, sessionPercent = 30.0, weeklyPercent = 85.0),
        )
        val cp = currentPeriod(s, HistoryPeriod.SESSION, resetAnchor = anchor, now = now, selector = sessionSel)

        assertEquals(30.0, cp!!.projection!!.fromPercent, 1e-9)
    }

    // ─── Ancla sintetizada (fuente sin resets: método API key) ───

    @Test
    fun `fallback semana es proximo domingo 21 CLT`() {
        // Martes 2026-08-18 15:00 CLT → domingo 2026-08-23 21:00 CLT.
        val now = Instant.parse("2026-08-18T19:00:00Z").toEpochMilli() // 15:00 CLT
        val expected = Instant.parse("2026-08-24T01:00:00Z").toEpochMilli() // dom 21:00 CLT
        assertEquals(expected, fallbackResetAnchor(HistoryPeriod.WEEK, now))
    }

    @Test
    fun `fallback de sesion no es sintetizable y devuelve null`() {
        assertNull(fallbackResetAnchor(HistoryPeriod.SESSION, 123_456L))
    }

    @Test
    fun `fallback semana con now justo antes del domingo 21 usa ese domingo`() {
        // Domingo 2026-08-23 20:59:59 CLT (= 00:59:59Z del 24).
        val now = Instant.parse("2026-08-24T00:59:59Z").toEpochMilli()
        val expected = Instant.parse("2026-08-24T01:00:00Z").toEpochMilli()
        assertEquals(expected, fallbackResetAnchor(HistoryPeriod.WEEK, now))
    }
}
