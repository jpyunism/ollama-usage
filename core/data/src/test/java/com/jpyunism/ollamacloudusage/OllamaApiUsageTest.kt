package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del parsing de la respuesta JSON de GET /api/usage (API key).
 */
class OllamaApiUsageTest {

    private val api = OllamaApiUsage()

    private val json = """
        {
          "activity": {
            "cost": "0.00000",
            "period": {
              "type": "last_4_weeks",
              "starting_at": "2026-07-13T00:00:00Z",
              "ending_at": "2026-08-09T18:29:43Z"
            },
            "models": []
          },
          "limits": {
            "session": {
              "usage": 0.062,
              "models": [
                { "name": "deepseek-v4-flash:0731", "request_count": 385 }
              ]
            },
            "weekly": {
              "usage": 0.456,
              "models": [
                { "name": "deepseek-v4-flash:0731", "request_count": 2846 },
                { "name": "qwen3.5:397b", "request_count": 116 }
              ]
            }
          }
        }
    """.trimIndent()

    @Test
    fun `parsea porcentajes de session y weekly`() {
        val data = api.parseUsage(json)
        assertEquals(6.2, data.sessionPercent, 0.001)
        assertEquals(45.6, data.weeklyPercent, 0.001)
    }

    @Test
    fun `no expone fechas de reset (la API no las entrega)`() {
        val data = api.parseUsage(json)
        assertNull(data.sessionResetAt)
        assertNull(data.weeklyResetAt)
    }

    @Test
    fun `deriva el porcentaje por modelo de la proporcion de requests`() {
        val data = api.parseUsage(json)
        assertEquals(2, data.weeklyModels.size)
        // 2846 / (2846 + 116) = 96.08%
        assertEquals(96.08, data.weeklyModels[0].percent, 0.01)
        assertEquals(2846L, data.weeklyModels[0].requests)
        assertEquals("deepseek-v4-flash:0731", data.weeklyModels[0].model)
    }

    @Test
    fun `sin modelos devuelve lista vacia`() {
        val empty = """{"limits":{"session":{"usage":0.0,"models":[]},"weekly":{"usage":0.0,"models":[]}}}"""
        val data = api.parseUsage(empty)
        assertTrue(data.sessionModels.isEmpty())
        assertTrue(data.weeklyModels.isEmpty())
        assertEquals(0.0, data.weeklyPercent, 0.001)
    }

    @Test
    fun `modelos con request_count 0 no rompen la distribucion`() {
        val withZero = """{"limits":{"session":{"usage":0.0,"models":[]},"weekly":{"usage":0.5,"models":[
            {"name":"a","request_count":10},{"name":"b","request_count":0}]}}}"""
        val data = api.parseUsage(withZero)
        assertEquals(2, data.weeklyModels.size)
        assertEquals(100.0, data.weeklyModels[0].percent, 0.001)
        assertEquals(0.0, data.weeklyModels[1].percent, 0.001)
    }

    // ── /api/balance: contrato vigente desde 2026-11 ──────────────────────
    // Regresion: `/api/usage` dejo de devolver `limits` y la app mostraba
    // "network error, no value for limits" con cualquier API key valida.

    private val balance = """
        {
          "included": {
            "session": {"remaining_percent": 94.91, "resets_at": "2026-11-15T22:00:00Z"},
            "weekly":  {"remaining_percent": 84.17, "resets_at": "2026-11-16T00:00:00Z"}
          },
          "purchased": {"balance_usd": 0}
        }
    """.trimIndent()

    @Test
    fun `balance convierte el restante en consumo`() {
        val data = api.parseBalance(balance)
        assertEquals(5.09, data.sessionPercent, 0.001)
        assertEquals(15.83, data.weeklyPercent, 0.001)
    }

    @Test
    fun `balance entrega las fechas de reset que la API vieja no daba`() {
        val data = api.parseBalance(balance)
        assertEquals("2026-11-15T22:00:00Z", data.sessionResetAt.toString())
        assertEquals("2026-11-16T00:00:00Z", data.weeklyResetAt.toString())
    }

    @Test
    fun `balance acepta el esquema de creditos en dolares`() {
        val credits = """{"included":{"allowance_usd":60.0,"balance_usd":45.0,
            "period":{"until":"2026-12-01T00:00:00Z"}},"purchased":{"balance_usd":0}}"""
        val data = api.parseBalance(credits)
        assertEquals(25.0, data.weeklyPercent, 0.001)
        assertEquals("2026-12-01T00:00:00Z", data.weeklyResetAt.toString())
    }

    @Test
    fun `balance sin ventanas conocidas lanza un error explicito`() {
        try {
            api.parseBalance("""{"included":{}}""")
            throw AssertionError("debia lanzar UnsupportedUsageResponse")
        } catch (e: UnsupportedUsageResponse) {
            assertTrue(e.message!!.contains("no es reconocible"))
        }
    }

    @Test
    fun `desglose por modelo del endpoint de actividad`() {
        val groups = """{"range":"7d","totals":{"request_count":3080,"groups":[
            {"model":"deepseek-v4.1-flash:1017","request_count":2987},
            {"model":"deepseek-v4-pro:0813","request_count":52},
            {"model":"minimax-m3","request_count":38},
            {"model":"gemma4:31b","request_count":3}]}}"""
        val models = api.parseModelGroups(groups)
        assertEquals(4, models.size)
        assertEquals("deepseek-v4.1-flash:1017", models[0].model)
        assertEquals(96.98, models[0].percent, 0.01)
        assertEquals(2987L, models[0].requests)
    }

    @Test
    fun `json sin groups devuelve lista vacia y no explota`() {
        assertTrue(api.parseModelGroups("""{"error":"too many requests"}""").isEmpty())
        assertTrue(api.parseModelGroups("""{"totals":{"request_count":0}}""").isEmpty())
    }
}
