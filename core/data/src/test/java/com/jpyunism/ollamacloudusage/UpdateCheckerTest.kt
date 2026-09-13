package com.jpyunism.ollamacloudusage

import io.mockk.every
import io.mockk.mockk
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class UpdateCheckerTest {

    /** Context mockeado: currentVersion() cae en el getOrElse y devuelve "0". */
    private fun fakeContext() = mockk<android.content.Context>(relaxed = true)

    /** Response con el codigo y body indicados. */
    private fun fakeResponse(code: Int, body: String): Response {
        val req = Request.Builder().url("https://api.github.com/repos/jpyunism/ollama-usage/releases/latest").build()
        return Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun fakeCall(response: Response): Call {
        val call = mockk<Call>()
        every { call.execute() } returns response
        return call
    }

    /** Call que lanza IOException al ejecutar (sin red, DNS, proxy caido, timeout). */
    private fun failingCall(): Call {
        val call = mockk<Call>()
        every { call.execute() } throws IOException("simulated network error")
        return call
    }

    @Test
    fun `isNewer compara semver`() {
        assertTrue(UpdateChecker.isNewer("0.11.0", "0.10.0"))
        assertTrue(UpdateChecker.isNewer("1.0.0", "0.99.9"))
        assertTrue(UpdateChecker.isNewer("0.10.1", "0.10.0"))
        assertFalse(UpdateChecker.isNewer("0.10.0", "0.10.0"))
        assertFalse(UpdateChecker.isNewer("0.9.0", "0.10.0"))
        assertFalse(UpdateChecker.isNewer("0.10.0", "0.10.1"))
    }

    @Test
    fun `version invalida no cuenta como mas nueva`() {
        assertFalse(UpdateChecker.isNewer("abc", "0.10.0"))
        assertFalse(UpdateChecker.isNewer("", "0.10.0"))
    }

    @Test
    fun `release con tag mas nuevo y asset apk devuelve update`() {
        val json = """
            {"tag_name":"v0.11.0","assets":[
              {"name":"app-release.apk","browser_download_url":"https://github.com/jpyunism/ollama-usage/releases/download/v0.11.0/app-release.apk","digest":"sha256:abc123"}
            ]}
        """.trimIndent()
        val info = UpdateChecker.parseRelease(json, "0.10.0")
        assertEquals("0.11.0", info!!.versionName)
        assertEquals("https://github.com/jpyunism/ollama-usage/releases/download/v0.11.0/app-release.apk", info.downloadUrl)
        assertEquals("abc123", info.sha256)
    }

    @Test
    fun `release mas nuevo sin asset apk no devuelve update`() {
        val json = """{"tag_name":"v0.11.0","assets":[]}"""
        assertNull(UpdateChecker.parseRelease(json, "0.10.0"))
    }

    @Test
    fun `release no mas nuevo que lo instalado devuelve null`() {
        val json = """{"tag_name":"v0.10.0","assets":[{"name":"a.apk","browser_download_url":"https://x/a.apk"}]}"""
        assertNull(UpdateChecker.parseRelease(json, "0.10.0"))
        assertNull(UpdateChecker.parseRelease(json, "0.11.0"))
    }

    @Test
    fun `json invalido devuelve null`() {
        assertNull(UpdateChecker.parseRelease("no json", "0.10.0"))
    }

    // ── check(): el fallo de red NUNCA debe propagar una excepcion ──
    // Regresion: un IOException sin capturar escapaba de la corrutina de
    // arranque y dejaba la app colgada en el spinner de "Consultando
    // ollama.com…" (bug del spinner congelado al iniciar).

    @Test
    fun `check con IOException devuelve null en vez de lanzar`() {
        val result = UpdateChecker.checkWith(fakeContext()) { failingCall() }
        assertNull(result)
    }

    @Test
    fun `check con respuesta no exitosa devuelve null`() {
        val result = UpdateChecker.checkWith(fakeContext()) { fakeCall(fakeResponse(503, "")) }
        assertNull(result)
    }

    @Test
    fun `check con release mas nuevo devuelve la update`() {
        val json = """
            {"tag_name":"v9.9.9","assets":[
              {"name":"app-release.apk","browser_download_url":"https://x/a.apk"}
            ]}
        """.trimIndent()
        val info = UpdateChecker.checkWith(fakeContext(), installedVersion = "0.38.0") { fakeCall(fakeResponse(200, json)) }
        assertEquals("9.9.9", info!!.versionName)
        assertEquals("https://x/a.apk", info.downloadUrl)
    }

    @Test
    fun `check con json corrupto devuelve null sin lanzar`() {
        val result = UpdateChecker.checkWith(fakeContext()) { fakeCall(fakeResponse(200, "no json")) }
        assertNull(result)
    }
}
