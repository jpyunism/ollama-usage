package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupModelsTest {

    private fun model(name: String, percent: Double, requests: Long = 0) =
        ModelUsage(model = name, requests = requests, percent = percent)

    // ── sortedByUsage ──

    @Test
    fun `sortedByUsage ordena por percent descendente`() {
        val sorted = sortedByUsage(
            listOf(
                model("a", 10.0),
                model("b", 50.0),
                model("c", 30.0),
            ),
        )
        assertEquals(listOf("b", "c", "a"), sorted.map { it.model })
    }

    @Test
    fun `sortedByUsage desempata por requests descendente`() {
        val sorted = sortedByUsage(
            listOf(
                model("a", 10.0, requests = 5),
                model("b", 10.0, requests = 9),
            ),
        )
        assertEquals(listOf("b", "a"), sorted.map { it.model })
    }

    @Test
    fun `sortedByUsage con lista vacia devuelve vacio`() {
        assertEquals(emptyList<ModelUsage>(), sortedByUsage(emptyList()))
    }

    // ── groupModels: orden ──

    @Test
    fun `groupModels ordena segmentos por percent descendente`() {
        val segments = groupModels(
            listOf(
                model("chico", 1.0),
                model("grande", 60.0),
                model("medio", 25.0),
            ),
        )
        assertEquals(listOf("grande", "medio", "chico"), segments.map { it.label })
    }

    // ── groupModels: agrupación ──

    @Test
    fun `modelos bajo umbral se agrupan en Otros con suma y conteo`() {
        val segments = groupModels(
            listOf(
                model("a", 50.0),
                model("b", 2.0),
                model("c", 1.5),
            ),
        )
        assertEquals(2, segments.size)
        val others = segments.last()
        assertEquals("Otros", others.label)
        assertEquals(3.5, others.percent, 0.001)
        assertEquals(2, others.modelCount)
        assertNull(others.colorKey)
    }

    @Test
    fun `un solo modelo bajo umbral se mantiene individual`() {
        val segments = groupModels(
            listOf(
                model("a", 50.0),
                model("b", 2.0),
            ),
        )
        assertEquals(2, segments.size)
        assertEquals("b", segments.last().label)
        assertEquals(1, segments.last().modelCount)
        assertEquals("b", segments.last().colorKey)
    }

    @Test
    fun `todos bajo umbral no agrupa`() {
        val segments = groupModels(
            listOf(
                model("a", 2.0),
                model("b", 1.0),
            ),
        )
        assertEquals(2, segments.size)
        assertEquals(listOf("a", "b"), segments.map { it.label })
        assertEquals(listOf("a", "b"), segments.map { it.colorKey })
    }

    @Test
    fun `modelo exactamente en el umbral se mantiene individual`() {
        val segments = groupModels(
            listOf(
                model("a", 3.0),
                model("b", 2.0),
                model("c", 1.0),
            ),
        )
        assertEquals(2, segments.size)
        assertEquals("a", segments.first().label)
        assertEquals("Otros", segments.last().label)
        assertEquals(3.0, segments.last().percent, 0.001)
    }

    @Test
    fun `umbral custom se respeta`() {
        val segments = groupModels(
            listOf(
                model("a", 6.0),
                model("b", 4.0),
                model("c", 2.0),
            ),
            threshold = 5.0,
        )
        assertEquals(2, segments.size)
        assertEquals("a", segments.first().label)
        assertEquals("Otros", segments.last().label)
        assertEquals(6.0, segments.last().percent, 0.001)
        assertEquals(2, segments.last().modelCount)
    }

    @Test
    fun `label custom de Otros se usa`() {
        val segments = groupModels(
            listOf(
                model("a", 50.0),
                model("b", 2.0),
                model("c", 1.0),
            ),
            othersLabel = "Others",
        )
        assertEquals("Others", segments.last().label)
    }

    // ── groupModels: casos borde ──

    @Test
    fun `lista vacia devuelve vacio`() {
        assertEquals(emptyList<UsageSegment>(), groupModels(emptyList()))
    }

    @Test
    fun `un solo modelo devuelve un segmento`() {
        val segments = groupModels(listOf(model("a", 100.0)))
        assertEquals(1, segments.size)
        assertEquals("a", segments.first().label)
        assertEquals(100.0, segments.first().percent, 0.001)
        assertEquals(1, segments.first().modelCount)
    }

    @Test
    fun `segmentos individuales conservan colorKey y modelCount 1`() {
        val segments = groupModels(
            listOf(
                model("a", 70.0),
                model("b", 30.0),
            ),
        )
        assertEquals("a", segments[0].colorKey)
        assertEquals("b", segments[1].colorKey)
        assertEquals(1, segments[0].modelCount)
        assertEquals(1, segments[1].modelCount)
    }

    // ── othersGroup: modelos que van a "Otros" ──

    @Test
    fun `othersGroup devuelve los modelos bajo umbral cuando hay 2 o mas y hay grandes`() {
        val others = othersGroup(
            listOf(
                model("a", 50.0),
                model("b", 2.0),
                model("c", 1.5),
            ),
        )
        assertEquals(listOf("b", "c"), others?.map { it.model })
    }

    @Test
    fun `othersGroup devuelve null con un solo modelo bajo umbral`() {
        val others = othersGroup(
            listOf(
                model("a", 50.0),
                model("b", 2.0),
            ),
        )
        assertNull(others)
    }

    @Test
    fun `othersGroup devuelve null si todos estan bajo umbral`() {
        val others = othersGroup(
            listOf(
                model("a", 2.0),
                model("b", 1.0),
            ),
        )
        assertNull(others)
    }

    @Test
    fun `othersGroup devuelve null con lista vacia`() {
        assertNull(othersGroup(emptyList()))
    }

    @Test
    fun `othersGroup respeta umbral custom`() {
        val others = othersGroup(
            listOf(
                model("a", 6.0),
                model("b", 4.0),
                model("c", 2.0),
            ),
            threshold = 5.0,
        )
        assertEquals(listOf("b", "c"), others?.map { it.model })
    }

    @Test
    fun `othersGroup es consistente con groupModels`() {
        val models = listOf(
            model("a", 50.0),
            model("b", 2.0),
            model("c", 1.5),
        )
        val others = othersGroup(models)
        val segments = groupModels(models)
        val othersSegment = segments.last()
        if (others != null) {
            assertEquals("Otros", othersSegment.label)
            assertEquals(others.size, othersSegment.modelCount)
        } else {
            assertNull(othersSegment.colorKey)
        }
    }

    // ── groupModels: casos borde ──
}