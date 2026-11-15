package com.jpyunism.ollamacloudusage

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Cómo autentica el usuario ante ollama.com. */
enum class AuthSource { COOKIE, API_KEY }

/** Credencial inválida/vencida al usar API key. */
class InvalidApiKeyException : Exception("La API key es inválida o expiró. Crea una nueva en ollama.com/settings/keys.")

/** La API respondió 200 pero sin datos de consumo que la app sepa leer. */
class UnsupportedUsageResponse(detail: String) :
    Exception("La respuesta de consumo de ollama.com no es reconocible ($detail).")

/**
 * Fuente oficial de consumo con API key (Bearer).
 *
 * Noviembre 2026: Ollama reescribió `GET /api/usage` como serie temporal de
 * actividad (`totals` + `buckets`, `range=24h|7d|30d`) y **eliminó `limits.*`**,
 * que era lo único que esta app leía. Resultado: `JSONException: No value for
 * limits` en cada refresco con API key. El porcentaje de cuota vive ahora en
 * `GET /api/balance`.
 *
 *  - `/api/balance` — porcentaje restante de la ventana de sesión (5 h) y
 *    semanal, con `resets_at`. Fuente de verdad del consumo.
 *  - `/api/usage?range=24h|7d&group_by=model` — desglose por modelo. Se pide
 *    best-effort: el endpoint tiene 10 req/min por usuario, y quedarse sin el
 *    desglose es mucho menos grave que perder el porcentaje.
 */
class OllamaApiUsage(
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = BASE_URL,
) : UsageScraper {

    override fun fetchUsage(apiKey: String): UsageData {
        val usage = parseBalance(getJson("/api/balance", apiKey))
        return usage.copy(
            sessionModels = modelsOrEmpty("/api/usage?range=24h&group_by=model", apiKey),
            weeklyModels = modelsOrEmpty("/api/usage?range=7d&group_by=model", apiKey),
        )
    }

    private fun getJson(path: String, apiKey: String): String {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .header("User-Agent", "OllamaUsage/Android")
            .build()

        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            when {
                resp.code == 401 -> throw InvalidApiKeyException()
                resp.code == 429 -> throw RuntimeException("Demasiadas consultas a la API. Reintenta en un minuto.")
                !resp.isSuccessful -> throw RuntimeException("HTTP ${resp.code}")
            }
            return body
        }
    }

    /** Desglose por modelo, o vacío si el endpoint falla o está limitado. */
    private fun modelsOrEmpty(path: String, apiKey: String): List<ModelUsage> =
        runCatching { parseModelGroups(getJson(path, apiKey)) }.getOrDefault(emptyList())

    /**
     * Parsea `GET /api/balance`. Formato vigente de los planes por ventanas:
     *
     * ```json
     * {"included":{"session":{"remaining_percent":94.91,"resets_at":"2026-11-15T22:00:00Z"},
     *              "weekly":{"remaining_percent":84.17,"resets_at":"2026-11-16T00:00:00Z"}},
     *  "purchased":{"balance_usd":0}}
     * ```
     *
     * `remaining_percent` es lo que **queda**: el consumo es `100 - restante`.
     * También acepta el esquema de créditos (`allowance_usd` / `balance_usd` /
     * `period.until`) y el legacy `limits.*` con fracciones 0..1.
     */
    internal fun parseBalance(json: String): UsageData {
        val root = JSONObject(json)
        val included = root.optJSONObject("included")
            ?: return parseUsage(json) // legacy: {"limits":{...}}

        val session = windowOf(included.optJSONObject("session"))
        val weekly = windowOf(included.optJSONObject("weekly"))
            ?: windowOf(included) // plan de créditos: la ventana cuelga de included
        if (session == null && weekly == null) {
            throw UnsupportedUsageResponse("sin ventanas session/weekly")
        }
        return UsageData(
            sessionPercent = session?.percent ?: 0.0,
            weeklyPercent = weekly?.percent ?: 0.0,
            sessionResetAt = session?.resetAt,
            weeklyResetAt = weekly?.resetAt,
            sessionModels = emptyList(),
            weeklyModels = emptyList(),
            plan = "cloud",
        )
    }

    private data class Window(val percent: Double, val resetAt: Instant?)

    private fun windowOf(o: JSONObject?): Window? {
        if (o == null) return null
        val resetAt = o.optString("resets_at", "")
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: o.optJSONObject("period")
                ?.optString("until", "")
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }

        val percent = when {
            o.has("remaining_percent") -> 100.0 - o.optDouble("remaining_percent")
            o.has("usage") && o.optDouble("usage") <= 1.0 -> o.optDouble("usage") * 100.0
            o.has("allowance_usd") -> allowancePercent(o)
            else -> null
        } ?: return null
        return Window(percent.coerceIn(0.0, 100.0), resetAt)
    }

    /** Consumo derivado de los dólares del plan de créditos. */
    private fun allowancePercent(o: JSONObject): Double? {
        val allowance = o.optDouble("allowance_usd", -1.0)
        val balance = o.optDouble("balance_usd", -1.0)
        if (allowance <= 0.0 || balance < 0.0) return null
        return (1.0 - (balance / allowance)) * 100.0
    }

    /**
     * Parsea el formato legacy `{"limits":{"session":{"usage":0.06},...}}`.
     * Se mantiene porque el esquema de créditos aún puede devolverlo y porque
     * fija el comportamiento histórico de la app.
     */
    internal fun parseUsage(json: String): UsageData {
        val root = JSONObject(json)
        val limits = root.optJSONObject("limits")
            ?: throw UnsupportedUsageResponse("sin 'limits' ni 'included'")
        val session = limits.optJSONObject("session")
            ?: throw UnsupportedUsageResponse("sin 'limits.session'")
        val weekly = limits.optJSONObject("weekly")
            ?: throw UnsupportedUsageResponse("sin 'limits.weekly'")

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

    /**
     * Parsea el desglose por modelo de `/api/usage?group_by=model`:
     * `{"totals":{"request_count":3080,"groups":[{"model":"...","request_count":2987}]}}`.
     */
    internal fun parseModelGroups(json: String): List<ModelUsage> {
        val groups = JSONObject(json)
            .optJSONObject("totals")
            ?.optJSONArray("groups")
            ?: return emptyList()
        val models = buildList {
            for (i in 0 until groups.length()) {
                val o = groups.getJSONObject(i)
                val name = o.optString("model", "")
                if (name.isNotBlank()) {
                    add(ModelUsage(name, o.optLong("request_count", 0), 0.0))
                }
            }
        }
        return withPercents(models)
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
        return withPercents(models)
    }

    /** El porcentaje por modelo se deriva de la proporción de requests. */
    private fun withPercents(models: List<ModelUsage>): List<ModelUsage> {
        val total = models.sumOf { it.requests }.toDouble()
        if (total <= 0.0) return models
        return models.map { it.copy(percent = it.requests / total * 100.0) }
    }

    private companion object {
        const val BASE_URL = "https://ollama.com"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
