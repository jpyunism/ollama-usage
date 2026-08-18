package com.jpyunism.ollamacloudusage.di

import android.content.Context
import com.jpyunism.ollamacloudusage.OllamaApiUsage
import com.jpyunism.ollamacloudusage.OllamaUsageScraper
import com.jpyunism.ollamacloudusage.SecurePrefs
import com.jpyunism.ollamacloudusage.UsageHistoryStore
import com.jpyunism.ollamacloudusage.UsageRepository
import com.jpyunism.ollamacloudusage.PrefsKeys
import com.jpyunism.ollamacloudusage.UpdateRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Grafo de dependencias de la app (DI manual, patrón oficial de Android).
 * Se construye una sola vez en [com.jpyunism.ollamacloudusage.OllamaUsageApp]
 * y se expone vía companion [get]. Ningún otro lugar hace `new` de
 * dependencias de infraestructura.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Preferencias con secretos cifrados en reposo. */
    val prefs = SecurePrefs.get(appContext)

    /** Cliente HTTP compartido por API, update-check y descargas. */
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Único pipeline de refresco de consumo (VM, WorkManager y FGS). */
    val usageRepository: UsageRepository by lazy {
        UsageRepository(
            context = appContext,
            prefs = prefs,
            scraper = OllamaUsageScraper(),
            apiScraper = OllamaApiUsage(httpClient),
            historyStore = UsageHistoryStore(prefs),
        )
    }

    /** Chequeo de actualizaciones (envuelve UpdateChecker con contexto). */
    val updateRepository: UpdateRepository by lazy {
        UpdateRepository(appContext)
    }

    /** Versión instalada de la app. */
    val versionName: String = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "0"
    }.getOrElse { "0" }

    /** Intervalo de refresco guardado. */
    val refreshInterval: Int
        get() = prefs.getInt(PrefsKeys.REFRESH_INTERVAL, PrefsKeys.DEFAULT_REFRESH_MINUTES)

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun get(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context).also { instance = it }
            }

        /** Solo para tests: permite reiniciar el grafo entre casos. */
        internal fun resetForTest() {
            instance = null
        }
    }
}
