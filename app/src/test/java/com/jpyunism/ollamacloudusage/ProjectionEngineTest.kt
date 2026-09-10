package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ProjectionEngineTest {

    private val hour = 3_600_000L
    private val base = 1_000_000_000L

    private fun snapshots(vararg pcts: Double): List<UsageSnapshot> =
        pcts.mapIndexed { i, pct ->
            UsageSnapshot(
                timestampMillis = base + i * hour,
                sessionPercent = pct,
                weeklyPercent = pct,
            )
        }

    private fun nowAt(index: Int): Instant = Instant.ofEpochMilli(base + index * hour)

    // ─────────── Sin datos suficientes ───────────

    @Test
    fun `menos de 3 snapshots devuelve null`() {
        assertNull(ProjectionEngine.project(snapshots(10.0, 20.0), resetAt = null, now = nowAt(1)))
        assertNull(ProjectionEngine.project(emptyList(), resetAt = null, now = nowAt(0)))
        assertNull(ProjectionEngine.project(snapshots(10.0), resetAt = null, now = nowAt(0)))
    }

    // ─────────── Ritmo creciente ───────────

    @Test
    fun `ritmo creciente produce RISING y hoursToTarget positivo`() {
        val s = snapshots(10.0, 20.0, 30.0) // +10 %/h
        val r = ProjectionEngine.project(s, resetAt = null, now = nowAt(2))!!
        assertEquals(ProjectionEngine.Trend.RISING, r.trend)
        assertEquals(10.0, r.percentPerHour, 1e-9)
        // current = 30, target 100 -> (100-30)/10 = 7 h
        assertEquals(7.0, r.hoursToTarget!!, 1e-9)
    }

    // ─────────── Varianza cero (estable) ───────────

    @Test
    fun `varianza cero produce STABLE y hoursToTarget null`() {
        val s = snapshots(50.0, 50.0, 50.0)
        val r = ProjectionEngine.project(s, resetAt = null, now = nowAt(2))!!
        assertEquals(ProjectionEngine.Trend.STABLE, r.trend)
        assertEquals(0.0, r.percentPerHour, 1e-9)
        assertNull(r.hoursToTarget)
    }

    // ─────────── Ritmo decreciente ───────────

    @Test
    fun `ritmo decreciente produce FALLING y hoursToTarget null`() {
        val s = snapshots(30.0, 20.0, 10.0) // -10 %/h
        val r = ProjectionEngine.project(s, resetAt = null, now = nowAt(2))!!
        assertEquals(ProjectionEngine.Trend.FALLING, r.trend)
        assertEquals(-10.0, r.percentPerHour, 1e-9)
        assertNull(r.hoursToTarget)
    }

    // ─────────── percentAtReset ───────────

    @Test
    fun `percentAtReset con reset en el futuro usa el ritmo`() {
        val s = snapshots(10.0, 20.0, 30.0) // +10 %/h
        val resetAt = Instant.ofEpochMilli(base + 7 * hour) // 5 h despues de now
        val r = ProjectionEngine.project(s, resetAt = resetAt, now = nowAt(2))!!
        // current = 30, rate = 10, 5 h -> 80
        assertEquals(80.0, r.percentAtReset!!, 1e-9)
    }

    @Test
    fun `percentAtReset se clampea a 100 cuando el ritmo lo sobrepasa`() {
        val s = snapshots(10.0, 20.0, 30.0) // +10 %/h
        val resetAt = Instant.ofEpochMilli(base + 22 * hour) // 20 h despues de now
        val r = ProjectionEngine.project(s, resetAt = resetAt, now = nowAt(2))!!
        // current = 30 + 10*20 = 230 -> clamp 100
        assertEquals(100.0, r.percentAtReset!!, 1e-9)
    }

    @Test
    fun `percentAtReset se clampea a 0 cuando el ritmo es negativo`() {
        val s = snapshots(30.0, 20.0, 10.0) // -10 %/h
        val resetAt = Instant.ofEpochMilli(base + 7 * hour) // 5 h despues de now
        val r = ProjectionEngine.project(s, resetAt = resetAt, now = nowAt(2))!!
        // current = 10 - 10*5 = -40 -> clamp 0
        assertEquals(0.0, r.percentAtReset!!, 1e-9)
    }

    @Test
    fun `sin resetAt percentAtReset es null`() {
        val s = snapshots(10.0, 20.0, 30.0)
        val r = ProjectionEngine.project(s, resetAt = null, now = nowAt(2))!!
        assertNull(r.percentAtReset)
    }

    // ─────────── Selector de sesion ───────────

    @Test
    fun `selector de sesion usa sessionPercent`() {
        val s = listOf(
            UsageSnapshot(base, sessionPercent = 5.0, weeklyPercent = 50.0),
            UsageSnapshot(base + hour, sessionPercent = 15.0, weeklyPercent = 60.0),
            UsageSnapshot(base + 2 * hour, sessionPercent = 25.0, weeklyPercent = 70.0),
        )
        val r = ProjectionEngine.project(
            s,
            resetAt = null,
            now = nowAt(2),
            selector = { it.sessionPercent },
        )!!
        assertEquals(ProjectionEngine.Trend.RISING, r.trend)
        assertEquals(10.0, r.percentPerHour, 1e-9)
        // current = 25 -> (100-25)/10 = 7.5
        assertEquals(7.5, r.hoursToTarget!!, 1e-9)
    }

    // ─────────── Ventana de 7 dias ───────────

    @Test
    fun `usa solo los ultimos 7 dias de snapshots`() {
        // 170 snapshots: los 2 mas viejos con valor alto (99%), el resto
        // creciente +1 %/h. La ventana (7*24 = 168) descarta los 2 viejos.
        val old = (0 until 2).map { i ->
            UsageSnapshot(base - (170 - i) * hour, sessionPercent = 99.0, weeklyPercent = 99.0)
        }
        val recent = (0 until 168).map { i ->
            UsageSnapshot(base + i * hour, sessionPercent = i.toDouble(), weeklyPercent = i.toDouble())
        }
        val s = old + recent
        val r = ProjectionEngine.project(s, resetAt = null, now = nowAt(167))!!
        // Los 168 ultimos van 0..167 -> +1 %/h
        assertEquals(1.0, r.percentPerHour, 1e-9)
        assertTrue(r.trend == ProjectionEngine.Trend.RISING)
    }
}
