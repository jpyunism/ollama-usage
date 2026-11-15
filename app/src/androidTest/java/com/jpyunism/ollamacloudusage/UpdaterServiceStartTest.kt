package com.jpyunism.ollamacloudusage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regresion del bug "tocar Actualizar congela/mata la app".
 *
 * Causa raiz: `UpdaterService.start()` arrancaba el servicio con
 * `startForegroundService()` y `onStartCommand()` retornaba SIN llamar
 * `startForeground()` cuando faltaba el permiso "Instalar apps desconocidas".
 * A los ~5 s el sistema mataba el proceso con
 * `ForegroundServiceDidNotStartInTimeException`.
 *
 * Este test arranca el servicio de verdad: si el bug volviera, el proceso de
 * instrumentacion moriria durante la espera y el test fallaria.
 */
@RunWith(AndroidJUnit4::class)
class UpdaterServiceStartTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun sin_permiso_de_instalar_no_arranca_el_servicio_y_sobrevive() {
        // El emulador de CI no concede REQUEST_INSTALL_PACKAGES: este es el
        // camino que crasheaba.
        if (UpdaterService.canRequestPackageInstalls(context)) return

        UpdaterService.state.value = DownloadState.Idle

        val started = UpdaterService.start(context, "https://example.invalid/app.apk", null)

        assertFalse("no debe arrancar un FGS que no llamara startForeground", started)
        assertEquals(DownloadState.NeedsPermission, UpdaterService.state.value)

        // Mas que el timeout del FGS (~5 s): si el proceso hubiera muerto, esta
        // linea no se ejecutaria.
        Thread.sleep(7_000)
        assertTrue("el proceso sigue vivo tras tocar Actualizar", true)
    }
}
