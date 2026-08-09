package com.jpyunism.ollamacloudusage

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color

/** Temas de color disponibles. [seed] es el color base de la paleta. */
enum class AppTheme(@StringRes val labelRes: Int, val seed: Color) {
    System(R.string.theme_system, Color(0xFF4F46E5)),
    Indigo(R.string.theme_indigo, Color(0xFF4F46E5)),
    Emerald(R.string.theme_emerald, Color(0xFF059669)),
    Teal(R.string.theme_teal, Color(0xFF0D9488)),
    Ocean(R.string.theme_ocean, Color(0xFF0284C7)),
    Violet(R.string.theme_violet, Color(0xFF7C3AED)),
    Rose(R.string.theme_rose, Color(0xFFE11D48)),
    Amber(R.string.theme_amber, Color(0xFFD97706)),
}
