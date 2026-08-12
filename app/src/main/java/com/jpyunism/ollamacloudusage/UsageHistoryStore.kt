package com.jpyunism.ollamacloudusage

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistencia local del histórico de consumo.
 *
 * La API de Ollama Cloud no entrega histórico, así que la app acumula
 * snapshots en cada refresh exitoso. Se guardan como JSON en SharedPreferences
 * (SecurePrefs, mismo mecanismo que el resto de ajustes; sin Room ni deps).
 *
 * Reglas:
 * - Dedupe: se omite un snapshot si ambos % son idénticos al anterior y
 *   pasaron menos de [DEDUPE_MINUTES] minutos (evita ruido con refrescos
 *   frecuentes).
 * - Límite: se retienen a lo sumo [MAX_SNAPSHOTS] snapshots (FIFO: se
 *   descartan los más viejos).
 */
class UsageHistoryStore(
    private val prefs: SharedPreferences,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** Snapshots guardados, ordenados por timestamp ascendente. */
    fun load(): List<UsageSnapshot> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return parseSnapshots(raw)
    }

    /**
     * Registra un snapshot de consumo. Aplica dedupe y límite FIFO.
     * Devuelve la lista resultante (para actualizar el StateFlow).
     */
    fun record(sessionPercent: Double, weeklyPercent: Double): List<UsageSnapshot> {
        val ts = now()
        val current = load()

        val last = current.lastOrNull()
        if (last != null &&
            last.sessionPercent == sessionPercent &&
            last.weeklyPercent == weeklyPercent &&
            ts - last.timestampMillis < DEDUPE_MINUTES * 60_000L
        ) {
            return current
        }

        val updated = (current + UsageSnapshot(ts, sessionPercent, weeklyPercent))
            .takeLast(MAX_SNAPSHOTS)
        save(updated)
        return updated
    }

    fun clear() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun save(snapshots: List<UsageSnapshot>) {
        prefs.edit().putString(KEY_HISTORY, encodeSnapshots(snapshots)).apply()
    }

    companion object {
        const val KEY_HISTORY = "usage_history"
        const val MAX_SNAPSHOTS = 600
        const val DEDUPE_MINUTES = 15L

        /** Serializa snapshots a JSON: [{"t":ms,"s":pct,"w":pct}, ...]. */
        fun encodeSnapshots(snapshots: List<UsageSnapshot>): String {
            val arr = JSONArray()
            snapshots.forEach { s ->
                arr.put(
                    JSONObject()
                        .put("t", s.timestampMillis)
                        .put("s", s.sessionPercent)
                        .put("w", s.weeklyPercent),
                )
            }
            return arr.toString()
        }

        /** Parsea el JSON guardado; ante cualquier corrupción devuelve vacío. */
        fun parseSnapshots(raw: String): List<UsageSnapshot> = runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        UsageSnapshot(
                            timestampMillis = o.getLong("t"),
                            sessionPercent = o.getDouble("s"),
                            weeklyPercent = o.getDouble("w"),
                        ),
                    )
                }
            }.sortedBy { it.timestampMillis }
        }.getOrDefault(emptyList())
    }
}
