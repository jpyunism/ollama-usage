package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del parsing REAL de OllamaUsageScraper con un Document armado en
 * memoria (Jsoup.parse, sin red). Antes estos tests reimplementaban el
 * parsing; ahora ejercitan parseUsage/parseMeter/isLoginPage del scraper.
 */
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

    private fun parseHtml(html: String = this.html): UsageData =
        scraper.parseUsage(org.jsoup.Jsoup.parse(html))

    @Test
    fun `parsea porcentaje de session y weekly`() {
        val data = parseHtml()
        assertEquals(1.2, data.sessionPercent, 0.001)
        assertEquals(41.7, data.weeklyPercent, 0.001)
    }

    @Test
    fun `parsea el plan`() {
        assertEquals("pro", parseHtml().plan)
    }

    @Test
    fun `parsea desglose por modelo`() {
        val data = parseHtml()
        assertEquals(1, data.sessionModels.size)
        assertEquals("deepseek-v4-flash:0731", data.sessionModels[0].model)
        assertEquals(78L, data.sessionModels[0].requests)

        assertEquals(2, data.weeklyModels.size)
        assertEquals("qwen3.5:397b", data.weeklyModels[0].model)
        assertEquals(116L, data.weeklyModels[0].requests)
    }

    @Test
    fun `parsea fecha de reset de sesion`() {
        val data = parseHtml()
        assertEquals("2026-08-08T18:00:00Z", data.sessionResetAt.toString())
    }

    @Test
    fun `modelos con 0 porciento no rompen la barra`() {
        val data = parseHtml()
        assertTrue(data.weeklyModels.any { it.percent == 0.0 })
        // El mínimo para dibujar debe ser 0.1, no 0 — verificado en la UI.
        assertTrue(data.weeklyModels.map { it.percent }.sum() > 0.0)
    }

    // ── parseMeter directo (casos borde) ──

    @Test
    fun `parseMeter con null devuelve cero vacio`() {
        val meter = scraper.parseMeter(null)
        assertEquals(0.0, meter.percent, 0.001)
        assertTrue(meter.models.isEmpty())
    }

    @Test
    fun `parseMeter ignora segmentos con requests invalido`() {
        val doc = org.jsoup.Jsoup.parse(
            """<div data-usage-track aria-label="Weekly usage 50% used">
                 <button data-usage-segment data-model="a" data-requests="10" style="width: 50%;"></button>
                 <button data-usage-segment data-model="b" data-requests="???" style="width: 50%;"></button>
               </div>"""
        )
        val meter = scraper.parseMeter(doc.selectFirst("[data-usage-track]"))
        assertEquals(50.0, meter.percent, 0.001)
        assertEquals(1, meter.models.size)
        assertEquals("a", meter.models[0].model)
    }

    @Test
    fun `parseMeter con porcentaje no numerico devuelve cero`() {
        val doc = org.jsoup.Jsoup.parse(
            """<div data-usage-track aria-label="Weekly usage n/a used"></div>"""
        )
        val meter = scraper.parseMeter(doc.selectFirst("[data-usage-track]"))
        assertEquals(0.0, meter.percent, 0.001)
    }

    // ── Detección de login (cookie expirada) ──

    @Test
    fun `pagina de login dispara CookieExpiredException`() {
        val login = """<html><head><title>Sign in · Ollama</title></head><body>
            <input type="password" name="password"></body></html>"""
        val ex = runCatching { parseHtml(login) }.exceptionOrNull()
        assertTrue(ex is CookieExpiredException)
    }

    @Test
    fun `pagina normal no se confunde con login`() {
        assertFalse(scraper.isLoginPage(org.jsoup.Jsoup.parse(html)))
        assertTrue(scraper.isLoginPage(org.jsoup.Jsoup.parse("<title>Sign in</title><input type='password'>")))
    }

    @Test
    fun `sin data-time el reset queda null`() {
        val noReset = html.replace("data-time=\"2026-08-08T18:00:00Z\"", "")
        assertNull(parseHtml(noReset).sessionResetAt)
    }

    @Test
    fun `plan desconocido si no hay span capitalize`() {
        val noPlan = html.replace("""<span class="text-xs font-normal px-2 py-0.5 rounded-full bg-neutral-100 text-neutral-600 capitalize">pro</span>""", "")
        assertEquals("unknown", parseHtml(noPlan).plan)
    }
}
