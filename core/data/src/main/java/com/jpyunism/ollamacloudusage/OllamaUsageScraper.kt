package com.jpyunism.ollamacloudusage

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.Instant

interface UsageScraper {
    fun fetchUsage(cookie: String): UsageData
}

class OllamaUsageScraper : UsageScraper {

    /** Obtiene el consumo desde https://ollama.com/settings usando la cookie de sesión. */
    override fun fetchUsage(cookie: String): UsageData {
        val doc = Jsoup.connect("https://ollama.com/settings")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Cookie", cookie)
            .timeout(15_000)
            .get()
        return parseUsage(doc)
    }

    /**
     * Parsea el HTML de ollama.com/settings. Lógica pura, sin red:
     * testable con un Document armado desde un string.
     */
    internal fun parseUsage(doc: Document): UsageData {
        if (isLoginPage(doc)) throw CookieExpiredException()

        val plan = doc.selectFirst("h2 span.capitalize")?.text() ?: "unknown"

        val session = parseMeter(doc.selectFirst("[data-usage-track][aria-label*='Session']"))
        val weekly = parseMeter(doc.selectFirst("[data-usage-track][aria-label*='Weekly']"))

        // Cada meter tiene su propio "Resets in …" con data-time (ISO 8601).
        val sessionReset = parseResetTime(doc.selectFirst("[data-usage-track][aria-label*='Session']"))
        val weeklyReset = parseResetTime(doc.selectFirst("[data-usage-track][aria-label*='Weekly']"))

        return UsageData(
            sessionPercent = session.percent,
            weeklyPercent = weekly.percent,
            sessionResetAt = sessionReset,
            weeklyResetAt = weeklyReset,
            sessionModels = session.models,
            weeklyModels = weekly.models,
            plan = plan,
        )
    }

    /**
     * Busca el [data-time] (ISO 8601) del meter indicado. El elemento de reset
     * es hermano del bloque del meter dentro de su tarjeta: se sube por los
     * ancestros y se revisan solo los hijos directos, para no filtrar el
     * data-time de la sesión hacia el weekly (ni viceversa).
     */
    private fun parseResetTime(track: Element?): Instant? {
        if (track == null) return null
        var current: Element? = track
        while (current != null) {
            val time = current.children().firstOrNull { it.hasAttr("data-time") }
                ?.attr("data-time")
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            if (time != null) return time
            current = current.parent()
        }
        return null
    }

    /** true si el documento es la página de login (cookie inválida/expirada). */
    internal fun isLoginPage(doc: Document): Boolean =
        doc.title().contains("Sign in") || doc.select("input[type=password]").isNotEmpty()

    internal data class Meter(val percent: Double, val models: List<ModelUsage>)

    /** Extrae el porcentaje y el desglose por modelo de un meter [data-usage-track]. */
    internal fun parseMeter(track: Element?): Meter {
        if (track == null) return Meter(0.0, emptyList())

        val pct = Regex("""([\d.]+)%\s+used""")
            .find(track.attr("aria-label"))
            ?.groupValues?.get(1)
            ?.toDoubleOrNull()
            ?: 0.0

        val models = track.select("[data-usage-segment]").mapNotNull { seg ->
            val model = seg.attr("data-model")
            val requests = seg.attr("data-requests").toLongOrNull() ?: return@mapNotNull null
            val width = Regex("""([\d.]+)%""")
                .find(seg.attr("style"))
                ?.groupValues?.get(1)
                ?.toDoubleOrNull()
                ?: 0.0
            ModelUsage(model, requests, width)
        }

        return Meter(pct, models)
    }
}

class CookieExpiredException : Exception("La cookie de sesión expiró. Copia una nueva desde ollama.com/settings.")
