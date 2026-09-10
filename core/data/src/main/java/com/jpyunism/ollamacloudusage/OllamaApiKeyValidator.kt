package com.jpyunism.ollamacloudusage

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Validador ligero de Ollama Cloud API keys (issue #61, spec 01).
 *
 * Hace un ping a `https://ollama.com/api/usage` (mismo endpoint que el
 * scraper de consumo) con la API key como Bearer y mapea la respuesta a
 * [Result]:
 *
 *  - HTTP 2xx                       -> [Result.Valid] (la key funciona).
 *  - HTTP 401                       -> [Result.Invalid] (credencial rechazada).
 *  - HTTP 5xx / otro / excepcion    -> [Result.Inconclusive] (no podemos
 *    afirmar nada sobre la key).
 *
 * Inyectable en [OnboardingViewModel]. Para tests, se puede pasar un
 * `callFactory` falso sin red: ver [OllamaApiKeyValidatorTest].
 *
 * Decisiones de scope:
 *  - No se usa `/api/me` ni `/api/tags` (que la spec menciona como
 *    alternativas) porque no estan documentados publicamente y el repo ya
 *    usa `/api/usage` como unica fuente de verdad para API key.
 *  - Timeout 5s (mas corto que los 15s del AppContainer) para no hacer
 *    esperar al usuario durante el onboarding.
 */
class OllamaApiKeyValidator(
    private val client: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val endpoint: String = DEFAULT_ENDPOINT,
    /** Inyectable: permite tests deterministas sin red. */
    private val callFactory: (Request) -> Call = { client.newCall(it) },
) {

    /**
     * Resultado de la validacion: el VM lo mapea a strings de UI.
     */
    sealed interface Result {
        data object Valid : Result
        data object Invalid : Result
        data object Inconclusive : Result
    }

    /**
     * Valida la API key contra el endpoint configurado.
     * Suspende en [ioDispatcher] para no bloquear el main thread.
     */
    suspend fun validate(apiKey: String): Result = withContext(ioDispatcher) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) return@withContext Result.Invalid

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $trimmed")
            .header("Accept", "application/json")
            .header("User-Agent", "OllamaUsage/Android")
            .get()
            .build()

        try {
            callFactory(request).execute().use { resp ->
                when {
                    resp.isSuccessful -> Result.Valid
                    resp.code == 401 -> Result.Invalid
                    resp.code in 500..599 -> Result.Inconclusive
                    else -> Result.Inconclusive
                }
            }
        } catch (_: IOException) {
            // Timeout, DNS, conexion rehusada, etc.
            Result.Inconclusive
        } catch (_: Exception) {
            Result.Inconclusive
        }
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://ollama.com/api/usage"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }
}
