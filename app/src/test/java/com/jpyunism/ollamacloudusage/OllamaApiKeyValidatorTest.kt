package com.jpyunism.ollamacloudusage

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * Tests del validador de API key (issue #61).
 * Cubre los casos del spec: 2xx (valida), 401 (invalida), 500 (no
 * concluyente), timeout / IOException (no concluyente), key vacia (invalida),
 * 403 (no concluyente).
 *
 * Inyecta un `callFactory` mockeado para evitar MockWebServer (no es dep
 * del proyecto): el validador ejecuta `Call.execute()` del fake.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OllamaApiKeyValidatorTest {

    /** Crea un `Response` con el codigo indicado y un body vacio. */
    private fun fakeResponse(code: Int): Response {
        val req = Request.Builder().url(OllamaApiKeyValidator.DEFAULT_ENDPOINT).build()
        return Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body("".toResponseBody("application/json".toMediaType()))
            .build()
    }

    /** Crea un Call mockeado que devuelve una respuesta fija al ejecutar. */
    private fun fakeCallFor(code: Int): Call {
        val call = mockk<Call>()
        every { call.execute() } returns fakeResponse(code)
        return call
    }

    /** Call mockeado que lanza IOException al ejecutar (timeout, DNS, etc). */
    private fun failingCall(): Call {
        val call = mockk<Call>()
        every { call.execute() } throws IOException("simulated network error")
        return call
    }

    private fun validatorFor(call: Call) = OllamaApiKeyValidator(
        client = OkHttpClient(),
        ioDispatcher = UnconfinedTestDispatcher(),
        callFactory = { call },
    )

    @Test
    fun `HTTP 200 devuelve Valid`() = runTest {
        val v = validatorFor(fakeCallFor(200))
        assertEquals(OllamaApiKeyValidator.Result.Valid, v.validate("valid-key-abc"))
    }

    @Test
    fun `HTTP 201 devuelve Valid (cualquier 2xx)`() = runTest {
        val v = validatorFor(fakeCallFor(201))
        assertEquals(OllamaApiKeyValidator.Result.Valid, v.validate("valid-key-abc"))
    }

    @Test
    fun `HTTP 401 devuelve Invalid`() = runTest {
        val v = validatorFor(fakeCallFor(401))
        assertEquals(OllamaApiKeyValidator.Result.Invalid, v.validate("bad-key-xyz"))
    }

    @Test
    fun `HTTP 500 devuelve Inconclusive (no podemos afirmar)`() = runTest {
        val v = validatorFor(fakeCallFor(500))
        assertEquals(OllamaApiKeyValidator.Result.Inconclusive, v.validate("some-key"))
    }

    @Test
    fun `HTTP 503 devuelve Inconclusive`() = runTest {
        val v = validatorFor(fakeCallFor(503))
        assertEquals(OllamaApiKeyValidator.Result.Inconclusive, v.validate("some-key"))
    }

    @Test
    fun `HTTP 403 devuelve Inconclusive (no es 401 pero tampoco 2xx)`() = runTest {
        val v = validatorFor(fakeCallFor(403))
        assertEquals(OllamaApiKeyValidator.Result.Inconclusive, v.validate("some-key"))
    }

    @Test
    fun `IOException en execute devuelve Inconclusive`() = runTest {
        val v = validatorFor(failingCall())
        assertEquals(OllamaApiKeyValidator.Result.Inconclusive, v.validate("some-key"))
    }

    @Test
    fun `key vacia devuelve Invalid sin llamar a la red`() = runTest {
        var callCount = 0
        val v = OllamaApiKeyValidator(
            client = OkHttpClient(),
            ioDispatcher = UnconfinedTestDispatcher(),
            callFactory = {
                callCount++
                error("no deberia llamarse con key vacia")
                throw AssertionError("no llamado")
            },
        )
        assertEquals(OllamaApiKeyValidator.Result.Invalid, v.validate(""))
        assertEquals(0, callCount)
    }

    @Test
    fun `key en blanco (solo espacios) devuelve Invalid sin llamar a la red`() = runTest {
        var callCount = 0
        val v = OllamaApiKeyValidator(
            client = OkHttpClient(),
            ioDispatcher = UnconfinedTestDispatcher(),
            callFactory = {
                callCount++
                error("no deberia llamarse con key en blanco")
                throw AssertionError("no llamado")
            },
        )
        assertEquals(OllamaApiKeyValidator.Result.Invalid, v.validate("   \t  "))
        assertEquals(0, callCount)
    }

    @Test
    fun `key con espacios al rededor se trimea antes de enviar`() = runTest {
        val call = mockk<Call>()
        every { call.execute() } returns fakeResponse(200)
        val v = OllamaApiKeyValidator(
            client = OkHttpClient(),
            ioDispatcher = UnconfinedTestDispatcher(),
            callFactory = { call },
        )
        assertEquals(OllamaApiKeyValidator.Result.Valid, v.validate("  my-key  "))
    }
}
