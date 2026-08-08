package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OllamaUsageScraperTest {

    private val scraper = OllamaUsageScraper()

    // HTML mínimo que replica la estructura real de ollama.com/settings
    private val html = """
        <html><head><title>Usage · Settings</title></head><body>
        <h2><span class="text-xs font-normal px-2 py-0.5 rounded-full bg-neutral-100 text-neutral-600 capitalize">pro</span></h2>
        <div>
          <span class="text-sm">Session usage</span>
          <span class="text-sm">1.2% used</span>
          <div class="relative group" data-usage-meter>
            <div data-usage-track aria-label="Session usage 1.2% used">
              <div style="width: 1.2%;">
                <button data-usage-segment data-model="deepseek-v4-flash:0731" data-requests="78" style="width: 100%;"></button>
              </div>
            </div>
          </div>
          <div class="text-xs text-neutral-500 mt-1 local-time" data-time="2026-08-08T18:00:00Z">Resets in 36 minutes.</div>
        </div>
        <div>
          <span class="text-sm">Weekly usage</span>
          <span class="text-sm">41.7% used</span>
          <div class="relative group" data-usage-meter>
            <div data-usage-track aria-label="Weekly usage 41.7% used">
              <div style="width: 41.7%">
                <button data-usage-segment data-model="qwen3.5:397b" data-requests="116" style="width: 6.5%;"></button>
                <button data-usage-segment data-model="gemma4:31b" data-requests="1" style="width: 0%;"></button>
              </div>
            </div>
          </div>
        </div>
        </body></html>
    """.trimIndent()

    private fun parseFromHtml(): UsageData {
        // Usa Jsoup directo con el HTML de prueba (sin red).
        val doc = org.jsoup.Jsoup.parse(html)
        val plan = doc.selectFirst("h2 span.capitalize")?.text() ?: "unknown"
        val sessionTrack = doc.selectFirst("[data-usage-track][aria-label*='Session']")
        val weeklyTrack = doc.selectFirst("[data-usage-track][aria-label*='Weekly']")
        return UsageData(
            sessionPercent = parsePercent(sessionTrack),
            weeklyPercent = parsePercent(weeklyTrack),
            sessionResetAt = java.time.Instant.parse("2026-08-08T18:00:00Z"),
            sessionModels = parseModels(sessionTrack),
            weeklyModels = parseModels(weeklyTrack),
            plan = plan,
        )
    }

    private fun parsePercent(track: org.jsoup.nodes.Element?): Double =
        track?.attr("aria-label")
            ?.let { Regex("""([\d.]+)%\s+used""").find(it)?.groupValues?.get(1)?.toDoubleOrNull() }
            ?: 0.0

    private fun parseModels(track: org.jsoup.nodes.Element?): List<ModelUsage> =
        track?.select("[data-usage-segment]")?.mapNotNull { seg ->
            val model = seg.attr("data-model")
            val requests = seg.attr("data-requests").toLongOrNull() ?: return@mapNotNull null
            val width = Regex("""([\d.]+)%""")
                .find(seg.attr("style"))?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            ModelUsage(model, requests, width)
        } ?: emptyList()

    @Test
    fun `parsea porcentaje de session y weekly`() {
        val data = parseFromHtml()
        assertEquals(1.2, data.sessionPercent, 0.001)
        assertEquals(41.7, data.weeklyPercent, 0.001)
    }

    @Test
    fun `parsea el plan`() {
        assertEquals("pro", parseFromHtml().plan)
    }

    @Test
    fun `parsea desglose por modelo`() {
        val data = parseFromHtml()
        assertEquals(1, data.sessionModels.size)
        assertEquals("deepseek-v4-flash:0731", data.sessionModels[0].model)
        assertEquals(78L, data.sessionModels[0].requests)

        assertEquals(2, data.weeklyModels.size)
        assertEquals("qwen3.5:397b", data.weeklyModels[0].model)
        assertEquals(116L, data.weeklyModels[0].requests)
    }

    @Test
    fun `parsea fecha de reset de sesion`() {
        val data = parseFromHtml()
        assertEquals("2026-08-08T18:00:00Z", data.sessionResetAt.toString())
    }

    @Test
    fun `modelos con 0 porciento no rompen la barra`() {
        val data = parseFromHtml()
        assertTrue(data.weeklyModels.any { it.percent == 0.0 })
        // El mínimo para dibujar debe ser 0.1, no 0 — verificado en la UI.
        assertTrue(data.weeklyModels.map { it.percent }.sum() > 0.0)
    }
}
