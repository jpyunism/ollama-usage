package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SessionResetSchedulerTest {

    private val now = Instant.parse("2026-08-08T12:00:00Z")

    @Test
    fun `delay es exactamente 1h antes del reset`() {
        val resetAt = Instant.parse("2026-08-08T15:00:00Z")
        val delay = SessionResetScheduler.delayUntilAlert(resetAt, now)
        assertEquals(2 * 60 * 60 * 1000L, delay) // 15:00 - 1h = 14:00, desde 12:00 = 2h
    }

    @Test
    fun `sin reset devuelve null`() {
        assertNull(SessionResetScheduler.delayUntilAlert(null, now))
    }

    @Test
    fun `reset a menos de 1h devuelve null (aviso ya paso)`() {
        val resetAt = Instant.parse("2026-08-08T12:30:00Z")
        assertNull(SessionResetScheduler.delayUntilAlert(resetAt, now))
    }

    @Test
    fun `reset exactamente en 1h devuelve null (delay 0)`() {
        val resetAt = Instant.parse("2026-08-08T13:00:00Z")
        assertNull(SessionResetScheduler.delayUntilAlert(resetAt, now))
    }

    @Test
    fun `reset en el pasado devuelve null`() {
        val resetAt = Instant.parse("2026-08-08T11:00:00Z")
        assertNull(SessionResetScheduler.delayUntilAlert(resetAt, now))
    }

    @Test
    fun `avisa si consumo supera 70`() {
        assertTrue(SessionResetScheduler.shouldAlert(85.0))
        assertTrue(SessionResetScheduler.shouldAlert(70.1))
    }

    @Test
    fun `no avisa si consumo es 70 o menos`() {
        assertFalse(SessionResetScheduler.shouldAlert(70.0))
        assertFalse(SessionResetScheduler.shouldAlert(12.0))
    }
}
