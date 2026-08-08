package com.jpyunism.ollamacloudusage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

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
 */
object SecurePrefs {

    const val NAME = "ollama_usage_secure"
    private const val LEGACY_NAME = "ollama_usage"

    fun get(context: Context): SharedPreferences {
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
