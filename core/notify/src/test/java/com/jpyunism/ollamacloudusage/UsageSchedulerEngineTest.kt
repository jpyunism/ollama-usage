package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Motor que el scheduler elige para cada frecuencia (spec 12). La UI muestra
 * este dato para no prometer una precision que el motor no puede dar.
 */
class UsageSchedulerEngineTest {

    @Test
    fun `menos de 15 minutos usa el servicio en primer plano`() {
        assertEquals(RefreshEngine.FOREGROUND_SERVICE, UsageScheduler.engineFor(1))
        assertEquals(RefreshEngine.FOREGROUND_SERVICE, UsageScheduler.engineFor(14))
    }

    @Test
    fun `15 minutos o mas usa WorkManager`() {
        assertEquals(RefreshEngine.WORK_MANAGER, UsageScheduler.engineFor(15))
        assertEquals(RefreshEngine.WORK_MANAGER, UsageScheduler.engineFor(720))
    }

    @Test
    fun `valores fuera de rango se acotan antes de decidir`() {
        assertEquals(RefreshEngine.FOREGROUND_SERVICE, UsageScheduler.engineFor(0))
        assertEquals(RefreshEngine.WORK_MANAGER, UsageScheduler.engineFor(5000))
    }
}
