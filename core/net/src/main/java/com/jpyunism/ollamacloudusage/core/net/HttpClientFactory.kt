package com.jpyunism.ollamacloudusage.core.net

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP compartido por la app (API, update-check y descargas).
 * Centraliza la configuracion del OkHttpClient para que todos los modulos
 * usen los mismos timeouts.
 */
object HttpClientFactory {

    /** Cliente por defecto con timeouts de 15s (mismo que el AppContainer previo). */
    fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}
