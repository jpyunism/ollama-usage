package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ResetFormatTest {

    private val zone = ZoneId.of("America/Santiago")
    private val now = Instant.parse("2026-08-08T17:24:00Z")

    @Test
    fun `countdown con minutos`() {
        val reset = Instant.parse("2026-08-08T18:00:00Z")
        assertEquals("resetea en 36 min", formatReset(reset, ResetDisplayMode.COUNTDOWN, now, zone))
    }

    @Test
    fun `countdown con horas y minutos`() {
        val reset = Instant.parse("2026-08-08T20:10:00Z")
        assertEquals("resetea en 2 h 46 min", formatReset(reset, ResetDisplayMode.COUNTDOWN, now, zone))
    }

    @Test
    fun `countdown con dias`() {
        val reset = Instant.parse("2026-08-11T18:00:00Z")
        assertEquals("resetea en 3 d 0 h", formatReset(reset, ResetDisplayMode.COUNTDOWN, now, zone))
    }

    @Test
    fun `countdown con menos de un minuto`() {
        val reset = Instant.parse("2026-08-08T17:24:30Z")
        assertEquals("resetea en <1 min", formatReset(reset, ResetDisplayMode.COUNTDOWN, now, zone))
    }

    @Test
    fun `countdown con reset ya pasado`() {
        val reset = Instant.parse("2026-08-08T17:00:00Z")
        assertEquals("resetea pronto", formatReset(reset, ResetDisplayMode.COUNTDOWN, now, zone))
    }

    @Test
    fun `date muestra fecha y hora local`() {
        val reset = Instant.parse("2026-08-08T18:00:00Z")
        // 18:00 UTC = 14:00 en Santiago (UTC-4)
        assertEquals("resetea el 8 ago, 14:00", formatReset(reset, ResetDisplayMode.DATE, now, zone))
    }

    @Test
    fun `null devuelve null en ambos modos`() {
        assertNull(formatReset(null, ResetDisplayMode.COUNTDOWN, now, zone))
        assertNull(formatReset(null, ResetDisplayMode.DATE, now, zone))
    }
}
