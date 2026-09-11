package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato de la accion "Refrescar ahora" (issue #94): el receiver solo debe
 * reaccionar a su propia accion, nunca a broadcasts ajenos.
 */
class RefreshActionReceiverTest {

    @Test
    fun `acepta la accion de refrescar ahora`() {
        assertTrue(RefreshActionReceiver.shouldHandle(UsageNotifier.ACTION_REFRESH_NOW))
    }

    @Test
    fun `ignora acciones desconocidas`() {
        assertFalse(RefreshActionReceiver.shouldHandle("android.intent.action.BOOT_COMPLETED"))
        assertFalse(RefreshActionReceiver.shouldHandle("com.jpyunism.ollamacloudusage.action.OTHER"))
    }

    @Test
    fun `ignora intent sin accion`() {
        assertFalse(RefreshActionReceiver.shouldHandle(null))
    }
}
