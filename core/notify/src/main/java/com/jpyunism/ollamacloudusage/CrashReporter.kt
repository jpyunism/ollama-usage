package com.jpyunism.ollamacloudusage

import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

/**
 * Captura cualquier excepción no controlada (crash): la guarda en disco y
 * muestra [CrashActivity] con el stack trace para poder copiarlo.
 *
 * Se instala lo antes posible: en [OllamaUsageApp.attachBaseContext], antes
 * de cualquier otra inicialización, para que incluso un crash en
 * Application.onCreate quede registrado.
 *
 * IMPORTANTE (por qué se re-lanza SIEMPRE): tras una excepción no capturada el
 * looper del hilo que la sufrió queda inservible. Antes este handler se tragaba
 * la excepción para "dejar la app viva mostrando el error", pero eso sólo
 * funcionaba en teoría: el hilo principal moría igual y la UI quedaba congelada
 * en el último frame dibujado (un spinner de carga, por ejemplo), sin crash
 * visible y sin forma de recuperarse — el usuario no veía nada y tenía que
 * matar la app a mano. Android llegaba a mostrar "la app no responde" sin
 * ningún detalle.
 *
 * Ahora se registra el crash, se lanza [CrashActivity] (que corre en su PROPIO
 * proceso, así sobrevive a la muerte del proceso principal) y se re-lanza la
 * excepción para que el proceso termine limpiamente. Resultado: en vez de un
 * spinner congelado, el usuario ve la pantalla de error con el stack copiable.
 */
object CrashReporter {

    private const val TAG = "CrashReporter"
    private const val LOG_FILE = "crash.log"

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stack = Log.getStackTraceString(throwable)
            val device = StringBuilder().apply {
                appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
                appendLine("Brand: ${android.os.Build.BRAND} | Product: ${android.os.Build.PRODUCT}")
            }
            try {
                val file = File(context.filesDir, LOG_FILE)
                file.appendText(
                    "===== ${thread.name} @ ${System.currentTimeMillis()} =====\n$device\n$stack\n\n",
                )
            } catch (_: Throwable) {
                // Throwable, NO Exception: un OutOfMemoryError es un Error y NO
                // lo captura `catch (Exception)`. Justo cuando el heap está
                // agotado (cuando más falta hace el registro) el handler moría
                // aquí sin dejar rastro y sin llegar a mostrar CrashActivity.
                // Se reintenta con un texto mínimo, que apenas asigna.
                runCatching {
                    File(context.filesDir, LOG_FILE).appendText(
                        "===== ${thread.name} (log truncado: sin memoria) =====\n$throwable\n\n",
                    )
                }
            }
            try {
                val intent = Intent(context, CrashActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(CrashActivity.EXTRA_STACK, stack)
                context.startActivity(intent)
            } catch (_: Throwable) {
                // Si ni siquiera se puede abrir la pantalla de error, el crash
                // igual queda en crash.log y en logcat.
            }
            // Re-lanzar SIEMPRE: el hilo que falló ya no sirve. Tragarse la
            // excepción dejaba la UI congelada en el último frame dibujado
            // (un spinner), sin error visible y sin forma de recuperarse.
            // CrashActivity corre en su propio proceso (:crash), así que la
            // pantalla de error sobrevive a la muerte de este proceso.
            throw throwable
        }
    }

    /** Lee el último crash registrado (para adjuntar por chat). */
    fun readLastCrash(context: Context): String? =
        runCatching { File(context.filesDir, LOG_FILE).takeIf { it.exists() }?.readText() }.getOrNull()
}
