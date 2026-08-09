package com.jpyunism.ollamacloudusage

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Cómo autentica el usuario ante ollama.com. */
enum class AuthSource { COOKIE, API_KEY }

/** Credencial inválida/vencida al usar API key. */
class InvalidApiKeyException : Exception("La API key es inválida o expiró. Crea una nueva en ollama.com/settings/keys.")

/**
 * Fuente oficial de consumo: GET https://ollama.com/api/usage con API key
 * (Bearer token). Devuelve fracciones de uso (0..1) por sesión y semana más
 * el desglose por modelo. No incluye fechas de reset ni nombre de plan.
 */
class OllamaApiUsage(
    private val client: OkHttpClient = defaultClient(),
) : UsageScraper {

    override fun fetchUsage(apiKey: String): UsageData {
        val request = Request.Builder()
            .url("https://ollama.com/api/usage")
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .header("User-Agent", "OllamaUsage/Android")
            .build()

        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            when {
                resp.code == 401 -> throw InvalidApiKeyException()
                !resp.isSuccessful -> throw RuntimeException("HTTP ${resp.code}")
            }
            return parseUsage(body)
        }
    }

    /**
     * Parsea la respuesta JSON de /api/usage. Lógica pura, sin red.
     * El porcentaje por modelo se deriva de la proporción de request_count
     * (la API no lo entrega explícito).
     */
    internal fun parseUsage(json: String): UsageData {
        val root = JSONObject(json)
        val limits = root.getJSONObject("limits")
        val session = limits.getJSONObject("session")
        val weekly = limits.getJSONObject("weekly")

        return UsageData(
            sessionPercent = session.getDouble("usage") * 100.0,
            weeklyPercent = weekly.getDouble("usage") * 100.0,
            sessionResetAt = null,
            weeklyResetAt = null,
            sessionModels = parseModels(session.optJSONArray("models")),
            weeklyModels = parseModels(weekly.optJSONArray("models")),
            plan = "cloud",
        )
    }

    private fun parseModels(arr: JSONArray?): List<ModelUsage> {
        if (arr == null) return emptyList()
        val models = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name", "")
                if (name.isNotBlank()) {
                    add(ModelUsage(name, o.optLong("request_count", 0), 0.0))
                }
            }
        }
        val total = models.sumOf { it.requests }.toDouble()
        if (total <= 0.0) return models
        return models.map { it.copy(percent = it.requests / total * 100.0) }
    }

    private companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
