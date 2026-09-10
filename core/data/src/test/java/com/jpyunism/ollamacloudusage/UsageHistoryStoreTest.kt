package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageHistoryStoreTest {

    /** Fake mínimo de SharedPreferences en memoria (solo lo que usa el store). */
    private class FakePrefs : android.content.SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

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
            override fun putString(key: String, value: String?): android.content.SharedPreferences.Editor {
                map[key] = value
                return this
            }
            override fun putInt(key: String, value: Int): android.content.SharedPreferences.Editor {
                map[key] = value
                return this
            }
            override fun putLong(key: String, value: Long): android.content.SharedPreferences.Editor {
                map[key] = value
                return this
            }
            override fun putBoolean(key: String, value: Boolean): android.content.SharedPreferences.Editor {
                map[key] = value
                return this
            }
            override fun putFloat(key: String, value: Float): android.content.SharedPreferences.Editor {
                map[key] = value
                return this
            }
            override fun putStringSet(key: String, values: MutableSet<String>?): android.content.SharedPreferences.Editor {
                map[key] = values
                return this
            }
            override fun remove(key: String): android.content.SharedPreferences.Editor {
                map.remove(key)
                return this
            }
            override fun clear(): android.content.SharedPreferences.Editor {
                map.clear()
                return this
            }
            override fun commit(): Boolean = true
            override fun apply() {}
        }
    }

    private fun store(prefs: FakePrefs, now: () -> Long) = UsageHistoryStore(prefs, now)

    @Test
    fun `record guarda y load devuelve en orden`() {
        val prefs = FakePrefs()
        var t = 1_000L
        val s = store(prefs) { t }
        s.record(10.0, 20.0)
        t += 60_000
        s.record(15.0, 30.0)

        val loaded = s.load()
        assertEquals(2, loaded.size)
        assertEquals(1_000L, loaded[0].timestampMillis)
        assertEquals(10.0, loaded[0].sessionPercent, 0.001)
        assertEquals(30.0, loaded[1].weeklyPercent, 0.001)
    }

    @Test
    fun `dedupe omite snapshot identico dentro de la ventana`() {
        val prefs = FakePrefs()
        var t = 1_000L
        val s = store(prefs) { t }
        s.record(10.0, 20.0)
        t += 5 * 60_000 // 5 min < 15 min
        s.record(10.0, 20.0) // idéntico → se omite
        assertEquals(1, s.load().size)
    }

    @Test
    fun `dedupe no aplica si cambio el porcentaje`() {
        val prefs = FakePrefs()
        var t = 1_000L
        val s = store(prefs) { t }
        s.record(10.0, 20.0)
        t += 5 * 60_000
        s.record(12.0, 20.0) // cambió session → se guarda
        assertEquals(2, s.load().size)
    }

    @Test
    fun `dedupe no aplica si paso la ventana`() {
        val prefs = FakePrefs()
        var t = 1_000L
        val s = store(prefs) { t }
        s.record(10.0, 20.0)
        t += 20 * 60_000 // 20 min > 15 min
        s.record(10.0, 20.0)
        assertEquals(2, s.load().size)
    }

    @Test
    fun `limite FIFO descarta los mas viejos`() {
        val prefs = FakePrefs()
        var t = 0L
        val s = store(prefs) { t }
        repeat(UsageHistoryStore.MAX_SNAPSHOTS + 50) { i ->
            t = i * 60_000L
            s.record(i.toDouble(), i.toDouble())
        }
        val loaded = s.load()
        assertEquals(UsageHistoryStore.MAX_SNAPSHOTS, loaded.size)
        // El más viejo conservado es el 50 (se descartaron 0..49).
        assertEquals(50 * 60_000L, loaded.first().timestampMillis)
    }

    @Test
    fun `parse tolera JSON corrupto`() {
        assertTrue(UsageHistoryStore.parseSnapshots("not json").isEmpty())
        assertTrue(UsageHistoryStore.parseSnapshots("").isEmpty())
    }

    @Test
    fun `encode y parse son inversos`() {
        val snapshots = listOf(
            UsageSnapshot(1L, 10.0, 20.0),
            UsageSnapshot(2L, 15.5, 30.25),
        )
        val parsed = UsageHistoryStore.parseSnapshots(UsageHistoryStore.encodeSnapshots(snapshots))
        assertEquals(snapshots, parsed)
    }
}
