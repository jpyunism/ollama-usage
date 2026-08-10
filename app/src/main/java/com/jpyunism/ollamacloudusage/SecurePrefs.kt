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
 * Arranque a prueba de cuelgues: en algunos OEM (Honor/Huawei y firmwares
 * con el TEE/Keystore dañado) las operaciones del Keystore pueden lanzar o
 * colgarse indefinidamente — un try/catch no alcanza porque un cuelgue no
 * es una excepción. Por eso la inicialización corre en un hilo de fondo
 * ([startInit]) y [get] solo espera un tiempo acotado ([INIT_WAIT_MS]).
 * Si el Keystore no responde a tiempo, la app degrada a [InMemoryPrefs]
 * (nada se persiste; la cookie se vuelve a pedir) pero abre igual. Nunca se
 * escribe un secreto en claro.
 */
object SecurePrefs {

    const val NAME = "ollama_usage_secure"
    private const val LEGACY_NAME = "ollama_usage"
    private const val TAG = "SecurePrefs"
    private const val INIT_WAIT_MS = 3_000L

    @Volatile
    private var ready: SharedPreferences? = null

    @Volatile
    private var degraded = false

    @Volatile
    private var initStarted = false

    /** Inicia la inicialización en background (llamar lo antes posible). */
    fun startInit(context: Context) {
        if (initStarted) return
        initStarted = true
        val app = context.applicationContext
        Thread {
            try {
                ready = encrypted(app)
                degraded = false
                Log.i(TAG, "Preferencias cifradas listas")
            } catch (e: Exception) {
                Log.w(TAG, "Keystore falló (${e.javaClass.simpleName}: ${e.message}); regenerando clave")
                try {
                    resetKeystore(app)
                    ready = encrypted(app)
                    degraded = false
                    Log.i(TAG, "Preferencias cifradas regeneradas")
                } catch (e2: Exception) {
                    Log.w(TAG, "Keystore sigue fallando; modo degradado (sin persistencia)", e2)
                    degraded = true
                }
            }
        }.apply {
            name = "secure-prefs-init"
            start()
        }
    }

    /**
     * Devuelve las preferencias cifradas si están listas; si el Keystore
     * tarda o falla, espera a lo sumo [INIT_WAIT_MS] y degrada a
     * [InMemoryPrefs]. Nunca bloquea el hilo principal sin límite.
     */
    fun get(context: Context): SharedPreferences {
        ready?.let { return it }
        if (degraded) return InMemoryPrefs
        startInit(context) // por si nadie lo llamó antes
        val deadline = System.currentTimeMillis() + INIT_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            ready?.let { return it }
            if (degraded) return InMemoryPrefs
            try {
                Thread.sleep(25)
            } catch (_: InterruptedException) {
                break
            }
        }
        // No bloquear más el hilo principal. Si el init en background
        // termina después con éxito, ready queda disponible y los próximos
        // get() lo usan; mientras tanto, todo vive en memoria.
        degraded = true
        return InMemoryPrefs
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
