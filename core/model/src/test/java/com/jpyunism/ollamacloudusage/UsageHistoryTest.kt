package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class UsageHistoryTest {

    private val week = HistoryPeriod.WEEK
    private val session = HistoryPeriod.SESSION

    // Reset semanal real: domingo 21:00 CLT = lunes 01:00 UTC.
    private val reset = Instant.parse("2026-08-10T01:00:00Z").toEpochMilli()

    private fun snap(vararg triples: Triple<String, Double, Double>): List<UsageSnapshot> =
        triples.map { (ts, s, w) ->
            UsageSnapshot(Instant.parse(ts).toEpochMilli(), s, w)
        }

    // ── periodsFor ──

    @Test
    fun `agrupa snapshots en periodos semanales alineados al reset`() {
        val data = snap(
            Triple("2026-07-27T12:00:00Z", 10.0, 30.0),
            Triple("2026-08-03T12:00:00Z", 20.0, 55.0),
            Triple("2026-08-10T12:00:00Z", 5.0, 20.0),
        )
        val groups = periodsFor(data, week, reset)
        assertEquals(3, groups.size)
        assertEquals(Instant.parse("2026-07-27T01:00:00Z").toEpochMilli(), groups[0].start)
        assertEquals(Instant.parse("2026-08-03T01:00:00Z").toEpochMilli(), groups[0].end)
        assertEquals(Instant.parse("2026-08-03T01:00:00Z").toEpochMilli(), groups[1].start)
        assertEquals(Instant.parse("2026-08-10T01:00:00Z").toEpochMilli(), groups[1].end)
        assertEquals(Instant.parse("2026-08-10T01:00:00Z").toEpochMilli(), groups[2].start)
        assertEquals(Instant.parse("2026-08-17T01:00:00Z").toEpochMilli(), groups[2].end)
        assertEquals(1, groups[0].snapshots.size)
        assertEquals(1, groups[1].snapshots.size)
        assertEquals(1, groups[2].snapshots.size)
    }

    @Test
    fun `sin reset conocido ancla desde el ultimo snapshot`() {
        val data = snap(
            Triple("2026-08-03T12:00:00Z", 20.0, 55.0),
            Triple("2026-08-10T12:00:00Z", 5.0, 20.0),
        )
        val groups = periodsFor(data, week, null)
        assertEquals(2, groups.size)
        assertEquals(Instant.parse("2026-08-03T12:00:00Z").toEpochMilli(), groups[0].start)
        assertEquals(Instant.parse("2026-08-10T12:00:00Z").toEpochMilli(), groups[0].end)
        assertEquals(Instant.parse("2026-08-10T12:00:00Z").toEpochMilli(), groups[1].start)
    }

    @Test
    fun `sesion agrupa cada 24 horas`() {
        val data = snap(
            Triple("2026-08-09T12:00:00Z", 40.0, 10.0),
            Triple("2026-08-10T12:00:00Z", 60.0, 20.0),
        )
        val groups = periodsFor(data, session, reset)
        assertEquals(2, groups.size)
        assertEquals(Instant.parse("2026-08-09T01:00:00Z").toEpochMilli(), groups[0].start)
        assertEquals(Instant.parse("2026-08-10T01:00:00Z").toEpochMilli(), groups[0].end)
        assertEquals(Instant.parse("2026-08-10T01:00:00Z").toEpochMilli(), groups[1].start)
        assertEquals(Instant.parse("2026-08-11T01:00:00Z").toEpochMilli(), groups[1].end)
    }

    @Test
    fun `sin snapshots devuelve lista vacia`() {
        assertEquals(0, periodsFor(emptyList(), week, reset).size)
    }

    // ── resetMarkers ──

    @Test
    fun `marcadores son los resets ya ocurridos dentro del rango`() {
        val data = snap(
            Triple("2026-07-27T12:00:00Z", 10.0, 30.0),
            Triple("2026-08-03T12:00:00Z", 20.0, 55.0),
            Triple("2026-08-10T12:00:00Z", 5.0, 20.0),
        )
        val markers = resetMarkers(data, week, reset)
        assertEquals(2, markers.size)
        assertEquals(Instant.parse("2026-08-03T01:00:00Z").toEpochMilli(), markers[0])
        assertEquals(Instant.parse("2026-08-10T01:00:00Z").toEpochMilli(), markers[1])
    }

    // ── peakPercent ──

    @Test
    fun `pico del periodo es el maximo porcentaje`() {
        val data = snap(
            Triple("2026-08-03T12:00:00Z", 20.0, 55.0),
            Triple("2026-08-05T12:00:00Z", 30.0, 80.0),
            Triple("2026-08-08T12:00:00Z", 25.0, 62.0),
        )
        val group = periodsFor(data, week, reset).single()
        assertEquals(80.0, peakPercent(group, UsageSnapshot::weeklyPercent)!!, 0.001)
        assertEquals(30.0, peakPercent(group, UsageSnapshot::sessionPercent)!!, 0.001)
    }

    // ── nearestSnapshot ──

    @Test
    fun `devuelve el snapshot mas cercano en el tiempo`() {
        val data = snap(
            Triple("2026-08-03T12:00:00Z", 20.0, 55.0),
            Triple("2026-08-10T12:00:00Z", 5.0, 20.0),
        )
        val near = nearestSnapshot(data, Instant.parse("2026-08-03T13:00:00Z").toEpochMilli())
        assertEquals(data[0], near)
        val near2 = nearestSnapshot(data, Instant.parse("2026-08-10T11:00:00Z").toEpochMilli())
        assertEquals(data[1], near2)
    }

    @Test
    fun `sin snapshots devuelve null`() {
        assertNull(nearestSnapshot(emptyList(), 0L))
    }

    // ── periodBars ──

    @Test
    fun `una barra por periodo con datos en orden cronologico`() {
        val data = snap(
            Triple("2026-07-27T12:00:00Z", 10.0, 30.0),
            Triple("2026-08-03T12:00:00Z", 20.0, 55.0),
            Triple("2026-08-10T12:00:00Z", 5.0, 20.0),
        )
        val now = Instant.parse("2026-08-10T20:00:00Z").toEpochMilli()
        val bars = periodBars(data, week, reset, now, UsageSnapshot::weeklyPercent)
        assertEquals(3, bars.size)
        assertEquals(Instant.parse("2026-07-27T01:00:00Z").toEpochMilli(), bars[0].start)
        assertEquals(Instant.parse("2026-08-03T01:00:00Z").toEpochMilli(), bars[1].start)
        assertEquals(Instant.parse("2026-08-10T01:00:00Z").toEpochMilli(), bars[2].start)
    }

    @Test
    fun `pico de la barra es el maximo del periodo no el ultimo snapshot`() {
        val data = snap(
            Triple("2026-08-03T12:00:00Z", 20.0, 55.0),
            Triple("2026-08-05T12:00:00Z", 30.0, 80.0),
            Triple("2026-08-08T12:00:00Z", 25.0, 62.0),
        )
        val now = Instant.parse("2026-08-10T20:00:00Z").toEpochMilli()
        val bars = periodBars(data, week, reset, now, UsageSnapshot::weeklyPercent)
        assertEquals(1, bars.size)
        assertEquals(80.0, bars[0].peakPercent, 0.001)
    }

    @Test
    fun `periodo actual marcado como en curso`() {
        val data = snap(
            Triple("2026-08-03T12:00:00Z", 20.0, 55.0),
            Triple("2026-08-10T12:00:00Z", 5.0, 20.0),
        )
        val now = Instant.parse("2026-08-10T20:00:00Z").toEpochMilli()
        val bars = periodBars(data, week, reset, now, UsageSnapshot::weeklyPercent)
        assertEquals(2, bars.size)
        assertEquals(false, bars[0].inProgress)
        assertEquals(true, bars[1].inProgress)
    }

    @Test
    fun `periodos vacios intermedios se omiten`() {
        val data = snap(
            Triple("2026-07-27T12:00:00Z", 10.0, 30.0),
            Triple("2026-08-10T12:00:00Z", 5.0, 20.0),
        )
        val now = Instant.parse("2026-08-10T20:00:00Z").toEpochMilli()
        val bars = periodBars(data, week, reset, now, UsageSnapshot::weeklyPercent)
        assertEquals(2, bars.size)
        assertEquals(Instant.parse("2026-07-27T01:00:00Z").toEpochMilli(), bars[0].start)
        assertEquals(Instant.parse("2026-08-10T01:00:00Z").toEpochMilli(), bars[1].start)
    }

    @Test
    fun `periodBars sin snapshots devuelve lista vacia`() {
        val now = Instant.parse("2026-08-10T20:00:00Z").toEpochMilli()
        assertEquals(0, periodBars(emptyList(), week, reset, now, UsageSnapshot::weeklyPercent).size)
    }

    @Test
    fun `sesion genera una barra por cada 24 horas`() {
        val data = snap(
            Triple("2026-08-09T12:00:00Z", 40.0, 10.0),
            Triple("2026-08-10T12:00:00Z", 60.0, 20.0),
        )
        val now = Instant.parse("2026-08-10T20:00:00Z").toEpochMilli()
        val bars = periodBars(data, session, reset, now, UsageSnapshot::sessionPercent)
        assertEquals(2, bars.size)
        assertEquals(40.0, bars[0].peakPercent, 0.001)
        assertEquals(60.0, bars[1].peakPercent, 0.001)
        assertEquals(false, bars[0].inProgress)
        assertEquals(true, bars[1].inProgress)
    }

    // ── summarize ──

    @Test
    fun `resumen con ultimo periodo cerrado y periodo actual`() {
        val data = snap(
            Triple("2026-07-27T12:00:00Z", 10.0, 30.0),
            Triple("2026-08-03T12:00:00Z", 20.0, 55.0),
            Triple("2026-08-10T12:00:00Z", 5.0, 20.0),
        )
        val now = Instant.parse("2026-08-10T20:00:00Z").toEpochMilli()
        val stats = summarize(data, week, reset, now, UsageSnapshot::weeklyPercent)

        // Último período cerrado: [03 ago, 10 ago) con pico 55.
        assertEquals(Instant.parse("2026-08-03T01:00:00Z").toEpochMilli(), stats.lastClosed?.start)
        assertEquals(55.0, stats.lastClosed?.peakPercent!!, 0.001)

        // Período actual: [10 ago, 17 ago) con consumo actual 20.
        assertEquals(Instant.parse("2026-08-10T01:00:00Z").toEpochMilli(), stats.current?.start)
        assertEquals(20.0, stats.current?.peakPercent!!, 0.001)
    }

    @Test
    fun `sin periodo cerrado devuelve solo el actual`() {
        val data = snap(
            Triple("2026-08-10T12:00:00Z", 5.0, 20.0),
        )
        val now = Instant.parse("2026-08-10T20:00:00Z").toEpochMilli()
        val stats = summarize(data, week, reset, now, UsageSnapshot::weeklyPercent)
        assertNull(stats.lastClosed)
        assertEquals(20.0, stats.current?.peakPercent!!, 0.001)
    }

    @Test
    fun `sin datos devuelve resumen vacio`() {
        val stats = summarize(emptyList(), week, reset, 0L, UsageSnapshot::weeklyPercent)
        assertNull(stats.lastClosed)
        assertNull(stats.current)
    }
}
