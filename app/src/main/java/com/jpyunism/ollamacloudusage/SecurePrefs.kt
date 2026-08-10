package com.jpyunism.ollamacloudusage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.KeyStore

/**
 * Acceso a preferencias cifradas (AndroidX Security).
 *
 * La cookie de sesión es una credencial válida de ollama.com: nunca se guarda
 * en claro. EncryptedSharedPreferences cifra tanto las claves (AES256_SIV)
 * como los valores (AES256_GCM) con una clave maestra protegida por Android
 * Keystore.
 *
 * Se usa un archivo nuevo ([NAME]) a propósito: el archivo legacy
 * "ollama_usage" contenía la cookie en claro y no es legible por
 * EncryptedSharedPreferences (migrar en caliente crashearía). [purgeLegacy]
 * borra ese archivo del disco en el primer arranque.
 *
 * Tolerancia a fallos del Keystore: en algunos OEM (Honor/Huawei y tras
 * actualizaciones de firmware) el Keystore puede quedar corrupto y
 * EncryptedSharedPreferences lanza al arrancar, crasheando la app antes de
 * mostrar nada. [get] no lanza: intenta recuperar la clave (borrándola y
 * regenerándola, perdiendo datos locales) y, si el Keystore sigue fallando,
 * degrada a [InMemoryPrefs]: la app funciona, pero nada se persiste (la
 * cookie vuelve a pedirse). Nunca se escribe un secreto en claro.
 */
object SecurePrefs {

    const val NAME = "ollama_usage_secure"
    private const val LEGACY_NAME = "ollama_usage"
    private const val TAG = "SecurePrefs"

    @Volatile
    private var degraded = false

    fun get(context: Context): SharedPreferences {
        if (degraded) return InMemoryPrefs
        return try {
            encrypted(context)
        } catch (e: Exception) {
            Log.w(TAG, "Keystore falló (${e.javaClass.simpleName}: ${e.message}); regenerando clave")
            try {
                resetKeystore(context)
                encrypted(context)
            } catch (e2: Exception) {
                Log.w(TAG, "Keystore sigue fallando; usando modo degradado (sin persistencia)", e2)
                degraded = true
                InMemoryPrefs
            }
        }
    }

    private fun encrypted(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Borra la clave maestra y el archivo cifrado para partir de cero. */
    private fun resetKeystore(context: Context) {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        if (ks.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
            ks.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        }
        val file = File(context.applicationInfo.dataDir, "shared_prefs/$NAME.xml")
        if (file.exists()) file.delete()
    }

    /** Elimina el archivo legacy con la cookie en claro (si existe). */
    fun purgeLegacy(context: Context) {
        runCatching {
            val legacy = context.getSharedPreferences(LEGACY_NAME, Context.MODE_PRIVATE)
            if (legacy.all.isNotEmpty()) legacy.edit().clear().commit()
            val file = File(context.applicationInfo.dataDir, "shared_prefs/$LEGACY_NAME.xml")
            if (file.exists()) file.delete()
        }
    }
}
