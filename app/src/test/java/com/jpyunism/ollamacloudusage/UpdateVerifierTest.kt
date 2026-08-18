package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class UpdateVerifierTest {

    private fun sha256(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it) }

    private fun tempFile(content: String): File =
        File.createTempFile("update-test", ".apk").apply {
            writeText(content)
            deleteOnExit()
        }

    @Test
    fun `sha256 correcto devuelve true`() {
        val file = tempFile("contenido-apk")
        val expected = sha256("contenido-apk".toByteArray())
        assertTrue(UpdaterService.verifySha256(file, expected))
    }

    @Test
    fun `sha256 distinto devuelve false`() {
        val file = tempFile("contenido-apk")
        assertFalse(UpdaterService.verifySha256(file, sha256("otro-contenido".toByteArray())))
    }

    @Test
    fun `expected null o vacio no verifica`() {
        val file = tempFile("contenido-apk")
        // null: el caller no llama a verifySha256 (REQ-016: solo si hay digest).
        // Vacío: no puede coincidir con un hash real.
        assertFalse(UpdaterService.verifySha256(file, ""))
    }

    @Test
    fun `archivo inexistente devuelve false sin excepcion`() {
        val missing = File("/no/existe/apk")
        assertFalse(UpdaterService.verifySha256(missing, sha256("x".toByteArray())))
    }

    @Test
    fun `sha256 en mayusculas tambien coincide`() {
        val file = tempFile("contenido-apk")
        val expected = sha256("contenido-apk".toByteArray()).uppercase()
        assertTrue(UpdaterService.verifySha256(file, expected))
    }
}
