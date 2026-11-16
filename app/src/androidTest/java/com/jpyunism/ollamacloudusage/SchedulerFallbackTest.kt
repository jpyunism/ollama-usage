package com.jpyunism.ollamacloudusage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regresion del cap de 6 h/dia de los FGS `dataSync` (Android 15+, spec 12):
 * cuando el sistema corta el servicio, el refresco debe seguir con el
 * WorkManager de 15 min en vez de morir en silencio.
 */
@RunWith(AndroidJUnit4::class)
class SchedulerFallbackTest {

    @Test
    fun fallback_encola_el_work_de_15_min_y_deja_el_intervalo_pendiente() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        UsageScheduler.fallbackToPeriodic(context, pendingInterval = 3)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(UsageScheduler.WORK_NAME)
            .get()

        assertTrue("el work periódico debe quedar encolado", infos.isNotEmpty())
        assertTrue(
            "estado inesperado: ${infos.first().state}",
            infos.first().state == WorkInfo.State.ENQUEUED || infos.first().state == WorkInfo.State.RUNNING,
        )
        assertEquals(
            "el intervalo pedido queda pendiente para retomarlo en foreground",
            3,
            SecurePrefs.get(context).getInt(UsageScheduler.KEY_PENDING_FGS, -1),
        )
    }
}
