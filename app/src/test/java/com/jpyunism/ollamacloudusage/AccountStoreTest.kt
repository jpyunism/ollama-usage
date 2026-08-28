package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del store de cuentas multi-cuenta (Feature A, issue #25).
 * Lógica pura sobre JSON en prefs (FakePrefs en memoria, patrón del repo).
 */
class AccountStoreTest {

    /** Fake mínimo de SharedPreferences en memoria (solo lo que usa el store). */
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

    @Test
    fun `add crea cuenta y queda en lista`() {
        val prefs = FakePrefs()
        val store = AccountStore(prefs) { "id-1" }
        val account = store.add("Personal", "key-abc")
        assertEquals("id-1", account.id)
        assertEquals("Personal", account.label)
        assertEquals("key-abc", account.apiKey)
        assertEquals(listOf(account), store.list())
    }

    @Test
    fun `primera cuenta queda activa automaticamente`() {
        val prefs = FakePrefs()
        val store = AccountStore(prefs) { "id-1" }
        val a = store.add("Personal", "key-abc")
        assertEquals(a.id, store.activeId())
        assertEquals(a, store.active())
    }

    @Test
    fun `add genera ids unicos`() {
        var n = 0
        val store = AccountStore(FakePrefs()) { "id-${n++}" }
        val a = store.add("A", "k1")
        val b = store.add("B", "k2")
        assertTrue(a.id != b.id)
        assertEquals(2, store.list().size)
    }

    @Test
    fun `rename cambia el label sin tocar la key`() {
        val prefs = FakePrefs()
        val store = AccountStore(prefs) { "id-1" }
        store.add("Personal", "key-abc")
        store.rename("id-1", "Trabajo")
        val a = store.list().single()
        assertEquals("Trabajo", a.label)
        assertEquals("key-abc", a.apiKey)
    }

    @Test
    fun `remove elimina la cuenta`() {
        val prefs = FakePrefs()
        val store = AccountStore(prefs) { "id-1" }
        store.add("A", "k1")
        store.remove("id-1")
        assertTrue(store.list().isEmpty())
        assertNull(store.activeId())
        assertNull(store.active())
    }

    @Test
    fun `remove de la activa reasigna a la siguiente`() {
        var n = 0
        val store = AccountStore(FakePrefs()) { "id-${n++}" }
        val a = store.add("A", "k1")
        val b = store.add("B", "k2")
        store.remove(a.id)
        assertEquals(b.id, store.activeId())
        assertEquals(b, store.active())
    }

    @Test
    fun `setActive cambia la cuenta activa`() {
        var n = 0
        val store = AccountStore(FakePrefs()) { "id-${n++}" }
        val a = store.add("A", "k1")
        val b = store.add("B", "k2")
        store.setActive(b.id)
        assertEquals(b.id, store.activeId())
        store.setActive(a.id)
        assertEquals(a.id, store.activeId())
    }

    @Test
    fun `setActive con id inexistente es no-op`() {
        val prefs = FakePrefs()
        val store = AccountStore(prefs) { "id-1" }
        val a = store.add("A", "k1")
        store.setActive("no-existe")
        assertEquals(a.id, store.activeId())
    }

    @Test
    fun `persistencia JSON roundtrip`() {
        val prefs = FakePrefs()
        var n = 0
        val store = AccountStore(prefs) { "id-${n++}" }
        store.add("Personal", "key-abc")
        store.add("Trabajo", "key-xyz")
        // Un store nuevo sobre los mismos prefs debe ver las mismas cuentas.
        val reloaded = AccountStore(prefs)
        assertEquals(2, reloaded.list().size)
        assertEquals("key-xyz", reloaded.list().first { it.label == "Trabajo" }.apiKey)
        assertFalse(reloaded.list().any { it.apiKey.isEmpty() })
    }

    @Test
    fun `migracion importa la api key unica como primera cuenta activa`() {
        val prefs = FakePrefs()
        prefs.map[PrefsKeys.API_KEY] = "key-legacy"
        val store = AccountStore(prefs) { "id-1" }
        val migrated = store.migrateIfNeeded(legacyApiKey = "key-legacy")
        assertTrue(migrated != null)
        assertEquals("key-legacy", migrated!!.apiKey)
        assertEquals(1, store.list().size)
        assertEquals(migrated.id, store.activeId())
    }

    @Test
    fun `migracion no se repite si ya hay cuentas`() {
        val prefs = FakePrefs()
        val store = AccountStore(prefs) { "id-1" }
        store.add("A", "k1")
        assertNull(store.migrateIfNeeded(legacyApiKey = "key-legacy"))
        assertEquals(1, store.list().size)
    }

    @Test
    fun `migracion sin api key legacy no hace nada`() {
        val prefs = FakePrefs()
        val store = AccountStore(prefs) { "id-1" }
        assertNull(store.migrateIfNeeded(legacyApiKey = null))
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `activeApiKey devuelve la key de la cuenta activa`() {
        var n = 0
        val store = AccountStore(FakePrefs()) { "id-${n++}" }
        store.add("A", "k1")
        val b = store.add("B", "k2")
        assertEquals("k1", store.activeApiKey())
        store.setActive(b.id)
        assertEquals("k2", store.activeApiKey())
    }

    @Test
    fun `sin cuentas activeApiKey es null`() {
        val store = AccountStore(FakePrefs())
        assertNull(store.activeApiKey())
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `parse tolera JSON corrupto devolviendo lista vacia`() {
        val prefs = FakePrefs()
        prefs.map[AccountStore.KEY_ACCOUNTS] = "not json"
        val store = AccountStore(prefs)
        assertTrue(store.list().isEmpty())
        assertNull(store.activeId())
    }
}