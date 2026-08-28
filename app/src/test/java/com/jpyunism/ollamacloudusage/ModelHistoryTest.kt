package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del histórico por modelo (Feature C lote 2, issue #20):
 * campo opcional `models` en UsageSnapshot, serialización "m", parse
 * tolerante hacia atrás, dedupe que considera la distribución y modelPercent.
 */
class ModelHistoryTest {

    /** Fake mínimo de SharedPreferences en memoria (patrón del repo). */
    private class FakePrefs : android.content.SharedPreferences {
        val map = mutableMapOf<String, Any?>()
        override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValue: MutableSet<String>?): MutableSet<String>? =
            map[key] as? MutableSet<String> ?: defValue
        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun edit(): android.content.SharedPreferences.Editor = object : android.content.SharedPreferences.Editor {
            override fun putString(key: String, value: String?): android.content.SharedPreferences.Editor { map[key] = value; return this }
            override fun putInt(key: String, value: Int): android.content.SharedPreferences.Editor { map[key] = value; return this }
            override fun putLong(key: String, value: Long): android.content.SharedPreferences.Editor { map[key] = value; return this }
            override fun putBoolean(key: String, value: Boolean): android.content.SharedPreferences.Editor { map[key] = value; return this }
            override fun putFloat(key: String, value: Float): android.content.SharedPreferences.Editor { map[key] = value; return this }
            override fun putStringSet(key: String, values: MutableSet<String>?): android.content.SharedPreferences.Editor { map[key] = values; return this }
            override fun remove(key: String): android.content.SharedPreferences.Editor { map.remove(key); return this }
            override fun clear(): android.content.SharedPreferences.Editor { map.clear(); return this }
            override fun commit(): Boolean = true
            override fun apply() {}
        }
    }

    // ─── data class + serialización ───

    @Test
    fun `snapshot nuevo lleva modelos y el viejo queda null`() {
        val withModels = UsageSnapshot(1L, 10.0, 20.0, models = mapOf("gpt" to 50.0, "qwen" to 50.0))
        val legacy = UsageSnapshot(2L, 10.0, 20.0)
        assertEquals(2, withModels.models?.size)
        assertNull(legacy.models)
    }

    @Test
    fun `encode incluye campo m solo cuando hay modelos`() {
        val json = UsageHistoryStore.encodeSnapshots(
            listOf(
                UsageSnapshot(1L, 10.0, 20.0, models = mapOf("m1" to 60.0, "m2" to 40.0)),
                UsageSnapshot(2L, 10.0, 20.0, models = null),
            ),
        )
        assertTrue(json.contains("\"m\""))
        // Solo 1 occurrence del campo m (el segundo snapshot no lo tiene).
        assertEquals(1, Regex("\"m\"").findAll(json).count())
    }

    @Test
    fun `encode y parse roundtrip con modelos`() {
        val original = listOf(
            UsageSnapshot(1L, 10.0, 20.0, models = mapOf("qwen" to 60.0, "deepseek" to 40.0)),
            UsageSnapshot(2L, 10.0, 20.0, models = null),
        )
        val parsed = UsageHistoryStore.parseSnapshots(UsageHistoryStore.encodeSnapshots(original))
        assertEquals(original, parsed)
    }

    @Test
    fun `parse tolera JSON viejo sin campo m`() {
        val json = """[{"t":1,"s":10,"w":20},{"t":2,"s":11,"w":21,"m":{"gpt":50.0}}]"""
        val parsed = UsageHistoryStore.parseSnapshots(json)
        assertNull(parsed[0].models)
        assertEquals(mapOf("gpt" to 50.0), parsed[1].models)
    }

    // ─── modelPercent ───

    @Test
    fun `modelPercent devuelve el pct del modelo o null`() {
        val s = UsageSnapshot(1L, 10.0, 20.0, models = mapOf("qwen" to 60.0))
        assertEquals(60.0, modelPercent(s, "qwen")!!, 0.001)
        assertNull(modelPercent(s, "no-existe"))
        assertNull(modelPercent(UsageSnapshot(1L, 10.0, 20.0), "qwen"))
    }

    // ─── dedupe considera modelos ───

    @Test
    fun `dedupe guarda si cambia la distribucion de modelos`() {
        val prefs = FakePrefs()
        var t = 1_000L
        val store = UsageHistoryStore(prefs, now = { t })
        store.record(10.0, 20.0, models = mapOf("a" to 100.0))
        t += 5 * 60_000 // dentro de la ventana de dedupe
        val out = store.record(10.0, 20.0, models = mapOf("a" to 50.0, "b" to 50.0))
        // Mismos % globales pero distinta distribución → se guarda (REQ-122).
        assertEquals(2, out.size)
    }

    @Test
    fun `dedupe omite si todo identico incluidos modelos`() {
        val prefs = FakePrefs()
        var t = 1_000L
        val store = UsageHistoryStore(prefs, now = { t })
        store.record(10.0, 20.0, models = mapOf("a" to 100.0))
        t += 5 * 60_000
        val out = store.record(10.0, 20.0, models = mapOf("a" to 100.0))
        assertEquals(1, out.size)
    }
}