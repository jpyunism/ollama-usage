package com.jpyunism.ollamacloudusage

import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * SharedPreferences en memoria que no persiste nada.
 *
 * Último recurso cuando el Android Keystore del dispositivo no funciona
 * (OEMs con TEE roto, firmwares corruptos): la app sigue operando durante la
 * sesión, pero cualquier secreto (cookie/API key) se pierde al cerrar, así
 * que se vuelve a pedir. A propósito NO escribe en disco para nunca dejar
 * credenciales en claro.
 */
object InMemoryPrefs : SharedPreferences {

    private val store = ConcurrentHashMap<String, Any?>()

    override fun getAll(): MutableMap<String, *> = store
    override fun getString(key: String, defValue: String?): String? = store[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = store[key] as? Set<String> ?: defValues
    override fun getInt(key: String, defValue: Int): Int = store[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = store[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = store[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = store[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = store.containsKey(key)
    override fun edit(): SharedPreferences.Editor = EditorImpl()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private class EditorImpl : SharedPreferences.Editor {
        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            if (value == null) store.remove(key) else store[key] = value
            return this
        }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String, value: Int): SharedPreferences.Editor { store[key] = value; return this }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor { store[key] = value; return this }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor { store[key] = value; return this }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { store[key] = value; return this }
        override fun remove(key: String): SharedPreferences.Editor { store.remove(key); return this }
        override fun clear(): SharedPreferences.Editor { store.clear(); return this }
        override fun commit(): Boolean = true
        override fun apply() {}
    }
}
