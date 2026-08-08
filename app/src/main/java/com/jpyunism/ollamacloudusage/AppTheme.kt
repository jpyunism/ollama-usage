package com.jpyunism.ollamacloudusage

import androidx.compose.ui.graphics.Color

/** Temas de color disponibles. [seed] es el color base de la paleta. */
enum class AppTheme(val label: String, val seed: Color) {
    System("Sistema", Color(0xFF4F46E5)),
    Indigo("Índigo", Color(0xFF4F46E5)),
    Emerald("Esmeralda", Color(0xFF059669)),
    Teal("Teal", Color(0xFF0D9488)),
    Ocean("Océano", Color(0xFF0284C7)),
    Violet("Violeta", Color(0xFF7C3AED)),
    Rose("Rosa", Color(0xFFE11D48)),
    Amber("Ámbar", Color(0xFFD97706)),
}
