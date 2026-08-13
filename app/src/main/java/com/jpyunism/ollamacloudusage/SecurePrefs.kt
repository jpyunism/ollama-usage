package com.jpyunism.ollamacloudusage

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Base64
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Preferencias con secretos cifrados SIN depender del Android Keystore.
 *
 * EncryptedSharedPreferences (security-crypto) usa el Keystore, que en
 * algunos OEM (Honor/Huawei, firmwares rotos) se cuelga o falla y crasheaba
 * la app al arrancar. Esta implementación cifra solo los secretos (cookie y
 * API key) con AES-256-GCM usando una clave derivada del ANDROID_ID + salt
 * fijo de la app:
 *
 * - La clave nunca se guarda en disco (se deriva en cada arranque).
 * - El blob cifrado es inútil fuera de este dispositivo/app.
 * - No hay llamadas al Keystore: no puede colgarse ni lanzar.
 * - Los secretos persisten entre reinicios (y entre updates, misma firma).
 *
 * El resto de preferencias (ajustes, tema, idioma, widget) se guardan en
 * claro como siempre. Los secretos se descifran al leerlos para usarlos
 * (scraper/API); la UI no los muestra de vuelta.
 */
object SecurePrefs {

    const val NAME = "ollama_usage_secure_v2"
    private const val LEGACY_NAME = "ollama_usage"
    private const val OLD_ENCRYPTED_NAME = "ollama_usage_secure"
    private const val TAG = "SecurePrefs"

    /** Claves cuyo valor se cifra en reposo. */
    internal val SECRET_KEYS = setOf(
        UsageViewModel.KEY_COOKIE,
        UsageViewModel.KEY_API_KEY,
    )

    fun get(context: Context): SharedPreferences {
        val base = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return EncryptedPrefs(base, SecretCipher(context))
    }

    /** Elimina archivos de formatos anteriores (cookie en claro y prefs cifradas viejas). */
    fun purgeLegacy(context: Context) {
        runCatching {
            // Borra directo los archivos XML: leer `legacy.all` (getAll) fuerza
            // a descifrar TODOS los secretos en el main thread durante el
            // arranque tras un update — la causa más probable del arranque
            // "pegado" (SettingsProvider/Keystore ocupados justo tras instalar).
            File(context.applicationInfo.dataDir, "shared_prefs/$LEGACY_NAME.xml").delete()
            File(context.applicationInfo.dataDir, "shared_prefs/$OLD_ENCRYPTED_NAME.xml").delete()
        }
    }
}

/** Cifra/descifra secretos con AES-256-GCM; clave derivada del ANDROID_ID. */
private class SecretCipher(context: Context) {

    // La clave se deriva de forma PERZOSA y se cachea por proceso: leer
    // ANDROID_ID es una query al content resolver que, si se hace en el
    // main thread durante el arranque tras un update (SettingsProvider
    // ocupado), puede bloquear la app y dejarla "pegada" sin iniciar.
    // Con lazy, la primera crypto real ocurre en background (refresh/worker),
    // nunca en attachBaseContext/onCreate.
    private val key: SecretKeySpec by lazy { deriveKeyFor(context) }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String? = runCatching {
        val raw = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = raw.copyOfRange(0, 12)
        val ct = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        String(cipher.doFinal(ct), Charsets.UTF_8)
    }.getOrNull()

    companion object {
        @Volatile
        private var cachedKey: SecretKeySpec? = null

        private fun deriveKeyFor(context: Context): SecretKeySpec {
            cachedKey?.let { return it }
            synchronized(this) {
                cachedKey?.let { return it }
                val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                    ?: "unknown-device"
                return deriveKey(androidId).also { cachedKey = it }
            }
        }
    }
}

/**
 * Deriva la clave AES-256 de la app a partir del ANDROID_ID.
 * Función pura (testeable sin Android): misma entrada → misma clave.
 */
internal fun deriveKey(androidId: String): SecretKeySpec {
    val material = "ollama-usage|$androidId|v2".toByteArray(Charsets.UTF_8)
    val digest = MessageDigest.getInstance("SHA-256").digest(material)
    return SecretKeySpec(digest, "AES")
}

/** Wrapper que cifra solo [SecurePrefs.SECRET_KEYS]; el resto pasa directo. */
private class EncryptedPrefs(
    private val base: SharedPreferences,
    private val cipher: SecretCipher,
) : SharedPreferences by base {

    override fun getString(key: String, defValue: String?): String? {
        val stored = base.getString(key, null) ?: return defValue
        if (key !in SecurePrefs.SECRET_KEYS) return stored
        return cipher.decrypt(stored) ?: defValue
    }

    override fun getAll(): MutableMap<String, *> {
        val all = base.all.toMutableMap()
        SecurePrefs.SECRET_KEYS.forEach { key ->
            (all[key] as? String)?.let { stored ->
                all[key] = cipher.decrypt(stored) ?: stored
            }
        }
        return all
    }

    override fun edit(): SharedPreferences.Editor = EncryptedEditor(base.edit(), cipher)
}

private class EncryptedEditor(
    private val base: SharedPreferences.Editor,
    private val cipher: SecretCipher,
) : SharedPreferences.Editor by base {

    override fun putString(key: String, value: String?): SharedPreferences.Editor {
        val toStore = if (value != null && key in SecurePrefs.SECRET_KEYS) {
            cipher.encrypt(value)
        } else {
            value
        }
        base.putString(key, toStore)
        return this
    }
}
