package com.jpyunism.ollamacloudusage

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

/**
 * Tests de la migración de secretos del formato legacy (cookie/API key en
 * claro en `ollama_usage`) al formato cifrado actual (`ollama_usage_secure_v2`).
 *
 * Issue #75: purgeLegacy borraba el XML viejo antes de migrar, perdiendo la
 * cookie de los usuarios que aún no habían migrado. Ahora se lee el valor
 * legacy, se cifra y se guarda en el nuevo, y solo después se borra el viejo.
 */
class SecurePrefsTest {

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

    /** Destino cifrado real: EncryptedPrefs sobre un FakePrefs con clave fija. */
    private fun encryptedTarget(base: FakePrefs): SharedPreferences {
        val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        return EncryptedPrefs(base, SecretCipher { key })
    }

    @Test
    fun `migracion copia la cookie en claro al destino cifrado`() {
        val legacy = FakePrefs()
        legacy.map[PrefsKeys.COOKIE] = "cookie-legacy"
        val base = FakePrefs()
        val target = encryptedTarget(base)

        val migrated = SecurePrefs.migrateSecrets(legacy, target)

        assertTrue(migrated)
        // El valor se lee descifrado desde el destino.
        assertEquals("cookie-legacy", target.getString(PrefsKeys.COOKIE, null))
        // En reposo el valor guardado está cifrado, no en claro.
        val stored = base.map[PrefsKeys.COOKIE] as String
        assertNotEquals("cookie-legacy", stored)
        assertTrue(stored.isNotBlank())
    }

    @Test
    fun `migracion copia la api key en claro al destino cifrado`() {
        val legacy = FakePrefs()
        legacy.map[PrefsKeys.API_KEY] = "key-legacy"
        val target = encryptedTarget(FakePrefs())

        val migrated = SecurePrefs.migrateSecrets(legacy, target)

        assertTrue(migrated)
        assertEquals("key-legacy", target.getString(PrefsKeys.API_KEY, null))
    }

    @Test
    fun `migracion no sobreescribe un secreto ya presente en el destino`() {
        val legacy = FakePrefs()
        legacy.map[PrefsKeys.COOKIE] = "cookie-legacy"
        val base = FakePrefs()
        val target = encryptedTarget(base)
        // El destino ya tiene una cookie (migrada en un arranque previo).
        target.edit().putString(PrefsKeys.COOKIE, "cookie-nueva").apply()

        val migrated = SecurePrefs.migrateSecrets(legacy, target)

        assertFalse(migrated)
        assertEquals("cookie-nueva", target.getString(PrefsKeys.COOKIE, null))
    }

    @Test
    fun `migracion sin secretos legacy no escribe nada`() {
        val legacy = FakePrefs()
        val base = FakePrefs()
        val target = encryptedTarget(base)

        val migrated = SecurePrefs.migrateSecrets(legacy, target)

        assertFalse(migrated)
        assertTrue(base.map.isEmpty())
        assertNull(target.getString(PrefsKeys.COOKIE, null))
    }

    @Test
    fun `migracion deja intactas las claves no secretas`() {
        val legacy = FakePrefs()
        legacy.map[PrefsKeys.COOKIE] = "cookie-legacy"
        legacy.map[PrefsKeys.LANGUAGE] = "es"
        val base = FakePrefs()
        val target = encryptedTarget(base)

        SecurePrefs.migrateSecrets(legacy, target)

        // La clave no secreta no se copia (solo se migran los secretos).
        assertNull(target.getString(PrefsKeys.LANGUAGE, null))
        assertEquals("cookie-legacy", target.getString(PrefsKeys.COOKIE, null))
    }
}
