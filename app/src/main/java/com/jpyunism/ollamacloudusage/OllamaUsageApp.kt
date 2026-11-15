package com.jpyunism.ollamacloudusage

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.jpyunism.ollamacloudusage.PrefsKeys
import com.jpyunism.ollamacloudusage.di.AppContainer

class OllamaUsageApp : Application() {

    /**
     * Instala el CrashReporter lo antes posible (antes de onCreate), para
     * capturar cualquier excepción del arranque y mostrarla en
     * CrashActivity en vez de cerrar la app en silencio.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashReporter.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        // CrashActivity corre en su propio proceso (:crash) y esta Application
        // se inicializa también ahí. Agendar WorkManager en ese proceso lanza
        // IllegalStateException ("WorkManager is not initialized properly") y
        // mata la pantalla de error, tapando el crash original. El pipeline de
        // la app solo tiene sentido en el proceso principal.
        if (!isMainProcess()) return
        val container = AppContainer.get(this)
        val prefs = container.prefs
        // Aplica el idioma guardado (o el del sistema) antes de crear la UI.
        val language = prefs.getString(PrefsKeys.LANGUAGE, null)
            ?.let { name -> AppLanguage.entries.firstOrNull { it.name == name } }
            ?: AppLanguage.System
        LocaleHelper.apply(this, language)
        // Migra los secretos del formato legacy (cookie/API key en claro) al
        // formato cifrado actual, y solo después borra los archivos viejos.
        SecurePrefs.migrateLegacy(this)
        // Crea los canales de notificación y programa el refresh de fondo.
        val interval = prefs.getInt(PrefsKeys.REFRESH_INTERVAL, PrefsKeys.DEFAULT_REFRESH_MINUTES)
        // Notificación proactiva de reset de sesión (issue #57): conecta el
        // programador real (SessionResetWorker) al pipeline de refresco.
        container.usageRepository.connectSessionResetScheduler { ctx, resetAt, percent ->
            SessionResetWorker.schedule(ctx, resetAt, percent)
        }
        Handler(Looper.getMainLooper()).post {
            UsageNotifier.ensureChannels(this)
            UsageScheduler.schedule(this, interval)
            // Resumen diario programado (Feature B lote 2): reprograma en cada
            // arranque de la app (cubre reinicio del dispositivo, REQ-114).
            DailySummaryWorker.schedule(this)
        }
    }

    /**
     * true si esta instancia corre en el proceso principal de la app.
     * `Application.getProcessName()` es API 28+; en 26-27 se resuelve por
     * `ActivityManager` con el pid actual, con fallback a true (nunca dejar la
     * app sin pipeline por un fallo de la consulta).
     */
    private fun isMainProcess(): Boolean = runCatching {
        val name = if (android.os.Build.VERSION.SDK_INT >= 28) {
            Application.getProcessName()
        } else {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.runningAppProcesses?.firstOrNull { it.pid == android.os.Process.myPid() }?.processName
        }
        name == null || name == packageName
    }.getOrDefault(true)
}
