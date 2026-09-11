package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Tests de la logica pura de la evolucion temporal por modelo (issue #92):
 * extraccion de la serie de un modelo del historico, serie "Otros",
 * orden del selector y regla de visibilidad.
 */
class ModelTimelineTest {

    private fun ts(h: Int): Long = Instant.parse("2026-08-10T00:00:00Z").toEpochMilli() + h * 3600_000L

    private fun snap(h: Int, models: Map<String, Double>?) =
        UsageSnapshot(ts(h), 0.0, 10.0, models)

    // ── modelSeries ──

    @Test
    fun `modelSeries devuelve solo los snapshots que reportan el modelo`() {
        val data = listOf(
            snap(0, mapOf("qwen" to 40.0, "gpt" to 60.0)),
            snap(1, null), // snapshot viejo sin desglose: se omite
            snap(2, mapOf("qwen" to 55.0)), // sin gpt: se omite para gpt
            snap(3, mapOf("qwen" to 70.0, "gpt" to 30.0)),
        )
        val qwen = modelSeries(data, "qwen")
        assertEquals(3, qwen.size)
        assertEquals(listOf(40.0, 55.0, 70.0), qwen.map { it.percent })
        assertEquals(listOf(ts(0), ts(2), ts(3)), qwen.map { it.timestampMillis })

        val gpt = modelSeries(data, "gpt")
        assertEquals(2, gpt.size)
        assertEquals(listOf(60.0, 30.0), gpt.map { it.percent })
    }

    @Test
    fun `modelSeries de un modelo inexistente queda vacia`() {
        val data = listOf(snap(0, mapOf("qwen" to 100.0)))
        assertTrue(modelSeries(data, "no-existe").isEmpty())
    }

    @Test
    fun `modelSeries con historico sin desglose queda vacia`() {
        val data = listOf(snap(0, null), snap(1, null))
        assertTrue(modelSeries(data, "qwen").isEmpty())
    }

    // ── othersSeries ──

    @Test
    fun `othersSeries suma los modelos fuera del top`() {
        val data = listOf(
            snap(0, mapOf("a" to 50.0, "b" to 30.0, "c" to 20.0)),
            snap(1, mapOf("a" to 60.0, "b" to 25.0, "c" to 15.0)),
        )
        val others = othersSeries(data, exclude = setOf("a"))
        assertEquals(listOf(50.0, 40.0), others.map { it.percent })
    }

    @Test
    fun `othersSeries omite snapshots sin desglose o sin modelos fuera del top`() {
        val data = listOf(
            snap(0, mapOf("a" to 100.0)), // nada fuera del top: se omite
            snap(1, null), // sin desglose: se omite
            snap(2, mapOf("a" to 80.0, "z" to 20.0)),
        )
        val others = othersSeries(data, exclude = setOf("a"))
        assertEquals(1, others.size)
        assertEquals(20.0, others.first().percent, 0.001)
        assertEquals(ts(2), others.first().timestampMillis)
    }

    // ── latestModelShares / topModelNames / hasOtherModels ──

    @Test
    fun `latestModelShares usa el ultimo snapshot CON desglose y ordena por pct desc`() {
        val data = listOf(
            snap(0, mapOf("a" to 10.0, "b" to 90.0)),
            snap(1, mapOf("a" to 70.0, "b" to 20.0, "c" to 10.0)),
            snap(2, null), // mas reciente pero sin desglose: no pisa el orden
        )
        val shares = latestModelShares(data)
        assertEquals(listOf("a", "b", "c"), shares.map { it.model })
        assertEquals(70.0, shares.first().percent, 0.001)
    }

    @Test
    fun `latestModelShares desempata por nombre ascendente`() {
        val data = listOf(snap(0, mapOf("zeta" to 50.0, "alfa" to 50.0)))
        assertEquals(listOf("alfa", "zeta"), latestModelShares(data).map { it.model })
    }

    @Test
    fun `topModelNames corta en topN y respeta el orden por pct desc`() {
        val data = listOf(
            snap(0, mapOf("a" to 50.0, "b" to 30.0, "c" to 15.0, "d" to 5.0)),
        )
        assertEquals(listOf("a", "b", "c"), topModelNames(data, topN = 3))
        assertTrue(hasOtherModels(data, topN = 3))
        assertFalse(hasOtherModels(data, topN = 4))
    }

    @Test
    fun `sin desglose no hay modelos ni otros`() {
        val data = listOf(snap(0, null))
        assertTrue(latestModelShares(data).isEmpty())
        assertTrue(topModelNames(data).isEmpty())
        assertFalse(hasOtherModels(data))
    }

    // ── visibilidad ──

    @Test
    fun `hasModelHistory exige al menos minPoints snapshots con desglose`() {
        val two = listOf(snap(0, mapOf("a" to 50.0)), snap(1, mapOf("a" to 60.0)))
        assertFalse(hasModelHistory(two))
        val three = two + snap(2, mapOf("a" to 70.0))
        assertTrue(hasModelHistory(three))
    }

    @Test
    fun `hasModelHistory ignora snapshots viejos sin desglose`() {
        val data = listOf(snap(0, null), snap(1, null), snap(2, null), snap(3, mapOf("a" to 1.0)))
        assertFalse(hasModelHistory(data))
    }

    @Test
    fun `modelSeriesVisible exige minPoints puntos`() {
        val two = listOf(ModelPoint(ts(0), 10.0), ModelPoint(ts(1), 20.0))
        assertFalse(modelSeriesVisible(two))
        assertTrue(modelSeriesVisible(two + ModelPoint(ts(2), 30.0)))
    }
}
