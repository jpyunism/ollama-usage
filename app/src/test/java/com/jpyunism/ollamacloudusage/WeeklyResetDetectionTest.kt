package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Tests de detección automática del ancla de reset semanal (Feature D, issue
 * #15): el % semanal cae ≥ 15 pp entre dos snapshots consecutivos separados
 * ≤ 4 h → reset real en el timestamp del snapshot bajo.
 */
class WeeklyResetDetectionTest {

    private fun at(iso: String): Long = Instant.parse(iso).toEpochMilli()

    @Test
    fun `caida brusca de 15pp o mas en menos de 4h detecta reset`() {
        val snapshots = listOf(
            UsageSnapshot(at("2026-08-23T20:30:00Z"), 50.0, 70.0),
            UsageSnapshot(at("2026-08-23T21:10:00Z"), 5.0, 3.0), // cae 67 pp en 40 min
            UsageSnapshot(at("2026-08-23T22:00:00Z"), 8.0, 6.0),
        )
        assertEquals(at("2026-08-23T21:10:00Z"), detectWeeklyReset(snapshots))
    }

    @Test
    fun `caida de menos de 15pp no detecta`() {
        val snapshots = listOf(
            UsageSnapshot(at("2026-08-23T20:30:00Z"), 50.0, 70.0),
            UsageSnapshot(at("2026-08-23T21:10:00Z"), 45.0, 60.0), // cae 10 pp
        )
        assertNull(detectWeeklyReset(snapshots))
    }

    @Test
    fun `caida de 15pp pero despues de mas de 4h no detecta`() {
        val snapshots = listOf(
            UsageSnapshot(at("2026-08-23T16:00:00Z"), 50.0, 70.0),
            UsageSnapshot(at("2026-08-23T21:10:00Z"), 5.0, 3.0), // 5h10m después
        )
        assertNull(detectWeeklyReset(snapshots))
    }

    @Test
    fun `caida gradual en varios snapshots no detecta`() {
        // Ninguna caída individual ≥ 15 pp, aunque el total sea mayor.
        val snapshots = listOf(
            UsageSnapshot(at("2026-08-23T20:00:00Z"), 50.0, 70.0),
            UsageSnapshot(at("2026-08-23T20:40:00Z"), 40.0, 60.0),
            UsageSnapshot(at("2026-08-23T21:20:00Z"), 30.0, 50.0),
            UsageSnapshot(at("2026-08-23T22:00:00Z"), 20.0, 40.0),
        )
        assertNull(detectWeeklyReset(snapshots))
    }

    @Test
    fun `devuelve el ultimo reset detectado en el historico`() {
        val snapshots = listOf(
            UsageSnapshot(at("2026-08-16T20:30:00Z"), 5.0, 70.0),
            UsageSnapshot(at("2026-08-16T21:10:00Z"), 5.0, 3.0),  // reset 1
            UsageSnapshot(at("2026-08-23T20:30:00Z"), 5.0, 70.0),
            UsageSnapshot(at("2026-08-23T21:10:00Z"), 5.0, 3.0),  // reset 2 (último)
        )
        assertEquals(at("2026-08-23T21:10:00Z"), detectWeeklyReset(snapshots))
    }

    @Test
    fun `subidas de porcentaje no detectan`() {
        val snapshots = listOf(
            UsageSnapshot(at("2026-08-23T20:30:00Z"), 10.0, 3.0),
            UsageSnapshot(at("2026-08-23T21:10:00Z"), 50.0, 70.0),
        )
        assertNull(detectWeeklyReset(snapshots))
    }

    @Test
    fun `menos de dos snapshots no detecta`() {
        assertNull(detectWeeklyReset(emptyList()))
        assertNull(detectWeeklyReset(listOf(UsageSnapshot(at("2026-08-23T20:30:00Z"), 50.0, 70.0))))
    }
}