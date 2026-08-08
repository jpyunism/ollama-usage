package com.jpyunism.ollamacloudusage

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.Instant

class OllamaUsageScraper {

    /** Obtiene el consumo desde https://ollama.com/settings usando la cookie de sesión. */
    fun fetchUsage(cookie: String): UsageData {
        val doc = Jsoup.connect("https://ollama.com/settings")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Cookie", cookie)
            .timeout(15_000)
            .get()

        if (doc.title().contains("Sign in") || doc.select("input[type=password]").isNotEmpty()) {
            throw CookieExpiredException()
        }

        val plan = doc.selectFirst("h2 span.capitalize")?.text() ?: "unknown"

        val session = parseMeter(doc.selectFirst("[data-usage-track][aria-label*='Session']"), "Session")
        val weekly = parseMeter(doc.selectFirst("[data-usage-track][aria-label*='Weekly']"), "Weekly")

        val sessionReset = doc.selectFirst("[data-time]")?.attr("data-time")
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }

        return UsageData(
            sessionPercent = session.percent,
            weeklyPercent = weekly.percent,
            sessionResetAt = sessionReset,
            sessionModels = session.models,
            weeklyModels = weekly.models,
            plan = plan,
        )
    }

    private data class Meter(val percent: Double, val models: List<ModelUsage>)

    private fun parseMeter(track: Element?, label: String): Meter {
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
