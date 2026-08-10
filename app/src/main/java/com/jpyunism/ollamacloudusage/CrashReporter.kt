package com.jpyunism.ollamacloudusage

import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

/**
 * Captura cualquier excepción no controlada (crash) y la guarda en disco +
 * muestra [CrashActivity] con el stack trace para poder copiarlo.
 *
 * Se instala lo antes posible: en [OllamaUsageApp.attachBaseContext], antes
 * de cualquier otra inicialización, para que incluso un crash en
 * Application.onCreate quede registrado.
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
            } catch (_: Exception) {
            }
            try {
                val intent = Intent(context, CrashActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(CrashActivity.EXTRA_STACK, stack)
                context.startActivity(intent)
            } catch (_: Exception) {
            }
            // No llamamos a prev: la app queda viva mostrando el error para que
            // el usuario pueda copiarlo. CrashActivity ofrece "Salir".
        }
    }

    /** Lee el último crash registrado (para adjuntar por chat). */
    fun readLastCrash(context: Context): String? =
        runCatching { File(context.filesDir, LOG_FILE).takeIf { it.exists() }?.readText() }.getOrNull()
}
