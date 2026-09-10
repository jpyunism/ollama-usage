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
}
