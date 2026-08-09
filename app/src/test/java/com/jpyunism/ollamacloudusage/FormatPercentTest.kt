package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatPercentTest {

    @Test
    fun `un decimal con redondeo`() {
        assertEquals("96.1", formatPercent(96.08))
        assertEquals("41.7", formatPercent(41.7))
        assertEquals("6.5", formatPercent(6.5))
        assertEquals("33.3", formatPercent(33.33))
        assertEquals("33.4", formatPercent(33.37))
    }

    @Test
    fun `enteros sin decimales`() {
        assertEquals("100", formatPercent(100.0))
        assertEquals("0", formatPercent(0.0))
        assertEquals("80", formatPercent(79.99))
    }

    @Test
    fun `redondeo de borde`() {
        assertEquals("95", formatPercent(95.0))
        assertEquals("12.5", formatPercent(12.5))
        assertEquals("12.6", formatPercent(12.55))
    }
}
