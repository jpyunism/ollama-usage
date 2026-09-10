package com.jpyunism.ollamacloudusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del helper de flag `onboarding_completed` (issue #61).
 * Cubre lectura por defecto, escritura, persistencia, reset y round-trip.
 *
 * Usa un FakePrefs en memoria (mismo patron que AccountStoreTest):
 * mockear las interfaces de android.jar con MockK es fragil porque el stub
 * no conserva los tipos de retorno reales y rompe los chains fluidos.
 */
class OnboardingPrefsTest {

    private fun newPrefs() = FakePrefs()

    @Test
    fun `isCompleted devuelve false por defecto si no hay flag`() {
        val prefs = newPrefs()
        val onboarding = OnboardingPrefs(prefs)
        assertFalse(onboarding.isCompleted())
    }

    @Test
    fun `isCompleted devuelve true si el flag esta persistido`() {
        val prefs = newPrefs()
        prefs.edit().putBoolean(PrefsKeys.ONBOARDING_COMPLETED, true).commit()
        val onboarding = OnboardingPrefs(prefs)
        assertTrue(onboarding.isCompleted())
    }

    @Test
    fun `markCompleted persiste true en prefs`() {
        val prefs = newPrefs()
        val onboarding = OnboardingPrefs(prefs)

        onboarding.markCompleted()

        assertEquals(true, prefs.map[PrefsKeys.ONBOARDING_COMPLETED])
    }

    @Test
    fun `reset elimina el flag de prefs`() {
        val prefs = newPrefs()
        prefs.edit().putBoolean(PrefsKeys.ONBOARDING_COMPLETED, true).commit()
        val onboarding = OnboardingPrefs(prefs)
        assertTrue(onboarding.isCompleted())

        onboarding.reset()

        assertFalse(prefs.contains(PrefsKeys.ONBOARDING_COMPLETED))
        assertFalse(onboarding.isCompleted())
    }

    @Test
    fun `round-trip markCompleted luego isCompleted es true`() {
        val prefs = newPrefs()
        val onboarding = OnboardingPrefs(prefs)
        assertFalse(onboarding.isCompleted())
        onboarding.markCompleted()
        assertTrue(onboarding.isCompleted())
    }

    @Test
    fun `reset sobre prefs vacio no rompe`() {
        val prefs = newPrefs()
        val onboarding = OnboardingPrefs(prefs)
        onboarding.reset() // no-op, no debe lanzar
        assertFalse(onboarding.isCompleted())
    }

    @Test
    fun `markCompleted idempotente sigue en true`() {
        val prefs = newPrefs()
        val onboarding = OnboardingPrefs(prefs)
        onboarding.markCompleted()
        onboarding.markCompleted()
        onboarding.markCompleted()
        assertTrue(onboarding.isCompleted())
        assertEquals(true, prefs.map[PrefsKeys.ONBOARDING_COMPLETED])
    }

    /**
     * Fake minimo de SharedPreferences en memoria (mismo patron que
     * AccountStoreTest). Solo lo que usa el helper.
     */
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
                map[key] = value; return this
            }
            override fun putInt(key: String, value: Int): android.content.SharedPreferences.Editor {
                map[key] = value; return this
            }
            override fun putLong(key: String, value: Long): android.content.SharedPreferences.Editor {
                map[key] = value; return this
            }
            override fun putBoolean(key: String, value: Boolean): android.content.SharedPreferences.Editor {
                map[key] = value; return this
            }
            override fun putFloat(key: String, value: Float): android.content.SharedPreferences.Editor {
                map[key] = value; return this
            }
            override fun putStringSet(key: String, values: MutableSet<String>?): android.content.SharedPreferences.Editor {
                map[key] = values; return this
            }
            override fun remove(key: String): android.content.SharedPreferences.Editor {
                map.remove(key); return this
            }
            override fun clear(): android.content.SharedPreferences.Editor {
                map.clear(); return this
            }
            override fun commit(): Boolean = true
            override fun apply() {}
        }
    }
}
