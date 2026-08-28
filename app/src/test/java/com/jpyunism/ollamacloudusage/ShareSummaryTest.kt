package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Tests del resumen para compartir (Feature E, issue #22 / REQ-140).
 */
class ShareSummaryTest {

    private val data = UsageData(
        sessionPercent = 42.5,
        weeklyPercent = 78.3,
        sessionResetAt = null,
        weeklyResetAt = null,
        sessionModels = emptyList(),
        weeklyModels = listOf(
            ModelUsage("gpt-oss:120b", 100, 55.0),
            ModelUsage("qwen3:32b", 20, 12.0),
        ),
        plan = "pro",
    )

    @Test
    fun `formato completo con top model`() {
        val t = shareSummaryText(data)
        assertEquals("📊 Ollama Cloud (pro): semana 78.3% · sesión 42.5% · top: gpt-oss:120b 55%", t)
    }

    @Test
    fun `sin modelos omite la parte top`() {
        val t = shareSummaryText(data.copy(weeklyModels = emptyList()))
        assertEquals("📊 Ollama Cloud (pro): semana 78.3% · sesión 42.5%", t)
        assertFalse(t.contains("top"))
    }
}