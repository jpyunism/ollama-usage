package com.jpyunism.ollamacloudusage.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Paleta de colores por modelo (Feature C, issue #20). Vive en :core:ui para
 * que la barra de consumo (:feature:usage) y la evolucion temporal por modelo
 * (:feature:stats, issue #92) pinten el MISMO modelo con el MISMO color.
 * M3 no expone una paleta categorica de N colores, asi que se usa esta lista
 * fija (misma excepcion justificada que el semaforo de consumo).
 */
private val modelPalette = listOf(
    Color(0xFF4F46E5), Color(0xFFF97316), Color(0xFF22C55E),
    Color(0xFF2563EB), Color(0xFFEC4899), Color(0xFF14B8A6),
    Color(0xFFEAB308), Color(0xFF8B5CF6), Color(0xFFEF4444), Color(0xFF06B6D4),
)

/** Color estable de un modelo: hash del nombre -> indice de la paleta. */
fun modelColor(model: String): Color =
    modelPalette[abs(model.hashCode()) % modelPalette.size]
