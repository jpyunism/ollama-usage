package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Tests de regresion del color por modelo.
 *
 * El bug: `modelColor` usaba `abs(hashCode) % size`. `abs(Int.MIN_VALUE)` sigue
 * siendo negativo (overflow), asi que un modelo cuyo hashCode sea
 * Int.MIN_VALUE producia un indice negativo -> IndexOutOfBoundsException en
 * plena composicion -> crash del hilo main -> UI congelada en el ultimo frame
 * (el spinner de carga), sin error visible.
 */
class ModelColorTest {

    @Test
    fun `modelo con hashCode Int MIN VALUE no rompe (indice no negativo)`() {
        // "polygenelubricants" tiene hashCode == Int.MIN_VALUE en la JVM.
        val name = "polygenelubricants"
        assertEquals(Int.MIN_VALUE, name.hashCode())
        // No debe lanzar IndexOutOfBoundsException.
        com.jpyunism.ollamacloudusage.ui.modelColor(name)
    }

    @Test
    fun `mismo modelo devuelve siempre el mismo color`() {
        val a = com.jpyunism.ollamacloudusage.ui.modelColor("llama3.3")
        val b = com.jpyunism.ollamacloudusage.ui.modelColor("llama3.3")
        assertEquals(a, b)
    }

    @Test
    fun `modelos distintos suelen dar colores distintos`() {
        val a = com.jpyunism.ollamacloudusage.ui.modelColor("llama3.3")
        val b = com.jpyunism.ollamacloudusage.ui.modelColor("qwen2.5")
        assertNotEquals(a, b)
    }
}
