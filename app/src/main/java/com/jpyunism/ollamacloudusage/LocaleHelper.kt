package com.jpyunism.ollamacloudusage

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Aplica el idioma elegido a la configuración de la app (persistente en el proceso). */
object LocaleHelper {

    fun apply(context: Context, language: AppLanguage) {
        val locale = language.localeTag?.let { Locale(it) } ?: Locale.getDefault()
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}
