package com.jpyunism.ollamacloudusage

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/** Aplica el idioma elegido a la configuración de la app (persistente en el proceso). */
object LocaleHelper {

    /**
     * Locale real del dispositivo, leído de los recursos del sistema. NO usar
     * [Locale.getDefault]: si en este proceso se aplicó antes otro idioma
     * (p.ej. español), el default queda "contaminado" y elegir Sistema
     * volvería a ese idioma en vez del del dispositivo.
     */
    private val systemLocale: Locale
        get() {
            val locales = Resources.getSystem().configuration.locales
            return if (locales.isEmpty) Locale.getDefault() else locales[0]
        }

    fun apply(context: Context, language: AppLanguage) {
        val locale = resolveLocale(language, systemLocale)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    /**
     * Decisión pura del locale: un idioma explícito (es/en) manda; Sistema
     * sigue al locale del dispositivo. Separada para poder testearla sin
     * framework Android.
     */
    internal fun resolveLocale(language: AppLanguage, system: Locale): Locale =
        language.localeTag?.let { Locale.forLanguageTag(it) } ?: system
}
