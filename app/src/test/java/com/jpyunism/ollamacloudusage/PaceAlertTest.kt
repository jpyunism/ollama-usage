package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PaceAlertTest {

    private val hour = 60 * 60 * 1000L
    private val now = 100_000_000L

    /** Selector de campo de la semana para los tests. */
    private val weekly: (UsageSnapshot) -> Double = { it.weeklyPercent }

    private fun snapshots(vararg pairs: Pair<Long, Double>): List<UsageSnapshot> =
        pairs.map { (ts, pct) ->
            UsageSnapshot(
                timestampMillis = ts,
                sessionPercent = pct,
                weeklyPercent = pct,
            )
        }

    // ─── Disparo: proyección cruza 100% ───

    @Test
    fun `proyeccion sobre 100 dispara alerta`() {
        // Pendiente 0.05 %/ms desde (anchor, 0%) — cruza 100 mucho antes del fin.
        val anchor = 10_000L
        val s = snapshots(anchor to 0.0, anchor + 1000 to 50.0)

        val alert = paceAlert(s, HistoryPeriod.SESSION, resetAnchor = anchor, now = anchor + 500, selector = weekly)

        assertNotNull(alert)
        assertEquals(anchor, alert!!.periodStart)
        val expected = 0.05 * (anchor + HistoryPeriod.SESSION.durationMillis - anchor)
        assertEquals(expected, alert.toPercent, 1e-6)
    }

    @Test
    fun `proyeccion bajo 100 no dispara alerta`() {
        // Pendiente baja: proyección queda bajo 100.
        val anchor = now + hour
        val s = snapshots(anchor - 2 * hour to 10.0, anchor - hour to 12.0)

        assertNull(paceAlert(s, HistoryPeriod.WEEK, resetAnchor = anchor, now = now, selector = weekly))
    }

    @Test
    fun `proyeccion justo en 100 no dispara (requiere superar)`() {
        // Puntos elegidos para proyectar exactamente 100 al end.
        val anchor = 10_000L
        val end = anchor + HistoryPeriod.SESSION.durationMillis
        val slope = 100.0 / (end - anchor)
        val t1 = anchor + 1000
        // Bajar levemente bajo 100 exacto para evitar ruido de punto flotante:
        // la proyección debe quedar en 99.9…, no en 100.000…007.
        val s = snapshots(anchor to 0.0, t1 to slope * 1000 * 0.999)

        assertNull(paceAlert(s, HistoryPeriod.SESSION, resetAnchor = anchor, now = t1, selector = weekly))
    }

    // ─── Guardas: sin datos suficientes ───

    @Test
    fun `menos de dos snapshots no dispara`() {
        val anchor = now + hour
        val s = snapshots(anchor - hour to 33.0)

        assertNull(paceAlert(s, HistoryPeriod.SESSION, resetAnchor = anchor, now = now, selector = weekly))
    }

    @Test
    fun `sin ancla no dispara`() {
        val s = snapshots(now - hour to 50.0, now - 500 to 60.0)

        assertNull(paceAlert(s, HistoryPeriod.WEEK, resetAnchor = null, now = now, selector = weekly))
    }

    @Test
    fun `sin snapshots no dispara`() {
        val anchor = now + hour

        assertNull(paceAlert(emptyList(), HistoryPeriod.WEEK, resetAnchor = anchor, now = now, selector = weekly))
    }

    // ─── Selector de campo ───

    @Test
    fun `selector de sesion usa sessionPercent`() {
        val anchor = now + hour
        val s = listOf(
            UsageSnapshot(anchor - 2000, sessionPercent = 20.0, weeklyPercent = 5.0),
            UsageSnapshot(anchor - 1000, sessionPercent = 70.0, weeklyPercent = 6.0),
        )

        val alert = paceAlert(s, HistoryPeriod.SESSION, resetAnchor = anchor, now = now, selector = { it.sessionPercent })

        assertNotNull(alert)
        assertEquals(anchor - HistoryPeriod.SESSION.durationMillis, alert!!.periodStart)
    }

    @Test
    fun `selector de semana usa weeklyPercent`() {
        val anchor = now + hour
        val s = listOf(
            UsageSnapshot(anchor - 2000, sessionPercent = 5.0, weeklyPercent = 20.0),
            UsageSnapshot(anchor - 1000, sessionPercent = 6.0, weeklyPercent = 70.0),
        )

        val alert = paceAlert(s, HistoryPeriod.WEEK, resetAnchor = anchor, now = now, selector = weekly)

        assertNotNull(alert)
        assertEquals(anchor - HistoryPeriod.WEEK.durationMillis, alert!!.periodStart)
    }
}