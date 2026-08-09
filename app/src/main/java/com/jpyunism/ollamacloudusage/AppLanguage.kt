package com.jpyunism.ollamacloudusage

import androidx.annotation.StringRes

/** Idiomas disponibles para la UI. [localeTag] es null para seguir al sistema. */
enum class AppLanguage(@StringRes val labelRes: Int, val localeTag: String?) {
    System(R.string.language_system, null),
    Spanish(R.string.language_es, "es"),
    English(R.string.language_en, "en"),
}
