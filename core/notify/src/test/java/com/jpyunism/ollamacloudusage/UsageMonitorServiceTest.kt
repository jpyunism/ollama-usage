package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageMonitorServiceTest {

    private val service = UsageMonitorService()

    @Test
    fun `backoff crece exponencialmente desde 1 minuto`() {
        assertEquals(60L, service.backoffSecondsFor(1))
        assertEquals(120L, service.backoffSecondsFor(2))
        assertEquals(240L, service.backoffSecondsFor(3))
        assertEquals(480L, service.backoffSecondsFor(4))
        assertEquals(960L, service.backoffSecondsFor(5))
    }

    @Test
    fun `backoff se topa en 30 minutos`() {
        assertEquals(1800L, service.backoffSecondsFor(6))
        assertEquals(1800L, service.backoffSecondsFor(7))
        assertEquals(1800L, service.backoffSecondsFor(10))
    }
}
