package com.jpyunism.ollamacloudusage

/**
 * Errores tipados del refresco de consumo. Extienden [Exception] para poder
 * viajar en `kotlin.Result.failure`. El repository mapea Throwable →
 * [UsageError]; la UI mapea [UsageError] → String con string resources.
 */
sealed class UsageError(message: String? = null) : Exception(message) {
    /** No hay cookie ni API key configuradas. */
    data object NoAuth : UsageError()

    /** La cookie de sesión expiró o es inválida. */
    data object CookieExpired : UsageError()

    /** La API key es inválida o expiró. */
    data object InvalidApiKey : UsageError()

    /** Error de red u otro fallo inesperado. */
    data class Network(val detail: String) : UsageError(detail)

    companion object {
        /** Mapea un Throwable del fetch a [UsageError]. */
        fun fromThrowable(e: Throwable): UsageError = when (e) {
            is CookieExpiredException -> CookieExpired
            is InvalidApiKeyException -> InvalidApiKey
            else -> Network(e.message ?: "")
        }
    }
}
