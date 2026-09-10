package com.jpyunism.ollamacloudusage

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
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
        PrefsKeys.COOKIE,
        PrefsKeys.API_KEY,
    )

    fun get(context: Context): SharedPreferences {
        val base = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return EncryptedPrefs(base, SecretCipher(context))
    }

    /**
     * Migra los secretos del formato legacy (cookie/API key en claro en
     * `ollama_usage`) al formato cifrado actual (`ollama_usage_secure_v2`).
     *
     * Antes esto borraba directo el XML legacy, lo que hacía perder la cookie
     * a los usuarios que aún no habían migrado: el viejo se borraba y el
     * nuevo cifrado no existía todavía (issue #75). Ahora se lee el valor
     * legacy, se cifra y se guarda en el nuevo, y SOLO después se borran los
     * archivos de formatos anteriores.
     *
     * Se usa SharedPreferences (no File.delete()) para leer el viejo, y la
     * escritura pasa por [EncryptedPrefs] para cifrar los secretos.
     */
    fun migrateLegacy(context: Context) {
        runCatching {
            val legacy = context.getSharedPreferences(LEGACY_NAME, Context.MODE_PRIVATE)
            migrateSecrets(legacy, get(context))
            // Solo después de migrar, borra los archivos de formatos anteriores
            // (cookie en claro y prefs cifradas viejas).
            File(context.applicationInfo.dataDir, "shared_prefs/$LEGACY_NAME.xml").delete()
            File(context.applicationInfo.dataDir, "shared_prefs/$OLD_ENCRYPTED_NAME.xml").delete()
        }
    }

    /**
     * Copia los secretos del formato legacy (en claro) al destino cifrado.
     * Función pura testeable: recibe las prefs legacy y el destino (que cifra
     * al escribir). Devuelve true si migró algún secreto.
     */
    internal fun migrateSecrets(
        legacy: SharedPreferences,
        target: SharedPreferences,
    ): Boolean {
        val editor = target.edit()
        var changed = false
        SECRET_KEYS.forEach { key ->
            val value = legacy.getString(key, null)
            if (value != null && !target.contains(key)) {
                editor.putString(key, value)
                changed = true
            }
        }
        if (changed) editor.apply()
        return changed
    }
}

/** Cifra/descifra secretos con AES-256-GCM; clave derivada del ANDROID_ID. */
internal class SecretCipher(private val keyProvider: () -> SecretKeySpec) {

    // La clave se deriva de forma PERZOSA y se cachea por proceso: leer
    // ANDROID_ID es una query al content resolver que, si se hace en el
    // main thread durante el arranque tras un update (SettingsProvider
    // ocupado), puede bloquear la app y dejarla "pegada" sin iniciar.
    // Con lazy, la primera crypto real ocurre en background (refresh/worker),
    // nunca en attachBaseContext/onCreate.
    private val key: SecretKeySpec by lazy { keyProvider() }

    constructor(context: Context) : this({ deriveKeyFor(context) })

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + ct)
    }

    fun decrypt(encoded: String): String? = runCatching {
        val raw = Base64.getDecoder().decode(encoded)
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
internal class EncryptedPrefs(
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
