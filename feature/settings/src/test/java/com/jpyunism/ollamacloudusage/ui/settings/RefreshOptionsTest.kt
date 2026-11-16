package com.jpyunism.ollamacloudusage.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reglas del control de frecuencia (spec 12). El slider viejo tenía 31
 * posiciones con saltos de ~23 min: no se podía fijar 5, 10, 20, 45 ni 90.
 */
class RefreshOptionsTest {

    @Test
    fun `los presets son los elegidos y estan ordenados dentro de rango`() {
        assertEquals(listOf(1, 5, 10, 15, 30, 60, 120, 360, 720), RefreshOptions.presets)
        assertEquals(RefreshOptions.presets.sorted(), RefreshOptions.presets)
        assertTrue(RefreshOptions.presets.all { it in RefreshOptions.MIN_MINUTES..RefreshOptions.MAX_MINUTES })
    }

    @Test
    fun `el modo se deriva del valor`() {
        assertEquals(RefreshMode.REAL_TIME, RefreshOptions.modeFor(1))
        assertEquals(RefreshMode.REAL_TIME, RefreshOptions.modeFor(5))
        assertEquals(RefreshMode.BALANCED, RefreshOptions.modeFor(6))
        assertEquals(RefreshMode.BALANCED, RefreshOptions.modeFor(30))
        assertEquals(RefreshMode.SAVER, RefreshOptions.modeFor(31))
        assertEquals(RefreshMode.SAVER, RefreshOptions.modeFor(720))
    }

    @Test
    fun `cada modo tiene su valor tipico`() {
        assertEquals(3, RefreshOptions.modeDefault(RefreshMode.REAL_TIME))
        assertEquals(15, RefreshOptions.modeDefault(RefreshMode.BALANCED))
        assertEquals(120, RefreshOptions.modeDefault(RefreshMode.SAVER))
        // el valor tipico pertenece a su propio modo
        RefreshMode.entries.forEach { mode ->
            assertEquals(mode, RefreshOptions.modeFor(RefreshOptions.modeDefault(mode)))
        }
    }

    @Test
    fun `sanitize acepta cualquier minuto valido`() {
        assertEquals(45, RefreshOptions.sanitize("45", fallback = 15))
        assertEquals(1, RefreshOptions.sanitize(" 1 ", fallback = 15))
        assertEquals(720, RefreshOptions.sanitize("720", fallback = 15))
    }

    @Test
    fun `sanitize ignora entradas invalidas y conserva el valor anterior`() {
        assertEquals(15, RefreshOptions.sanitize("", fallback = 15))
        assertEquals(15, RefreshOptions.sanitize("abc", fallback = 15))
        assertEquals(15, RefreshOptions.sanitize("0", fallback = 15))
        assertEquals(15, RefreshOptions.sanitize("-5", fallback = 15))
        assertEquals(15, RefreshOptions.sanitize("721", fallback = 15))
    }

    @Test
    fun `el stepper no sale de rango`() {
        assertEquals(2, RefreshOptions.step(1, +1))
        assertEquals(1, RefreshOptions.step(1, -1))
        assertEquals(720, RefreshOptions.step(720, +1))
        assertEquals(44, RefreshOptions.step(45, -1))
    }
}
