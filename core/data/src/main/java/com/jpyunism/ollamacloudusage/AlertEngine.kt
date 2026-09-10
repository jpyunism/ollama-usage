package com.jpyunism.ollamacloudusage

import android.content.SharedPreferences

/**
 * Motor puro de alertas por umbral. Decide cuándo notificar según el
 * consumo y el último nivel notificado; la persistencia del nivel queda en
 * [checkThreshold] vía prefs.
 */
object AlertEngine {

    const val ALERT = 0
    const val CRITICAL = 1

    /**
     * Nivel a notificar: CRITICAL si pct >= critical y aún no se notificó el
     * nivel crítico; ALERT si pct >= alert y aún no se notificó el de alerta;
     * -1 si pct < alert (reset) o ya se notificó ese nivel.
     */
    fun nextLevel(pct: Int, alert: Int, critical: Int, lastNotified: Int): Int = when {
        pct >= critical && lastNotified < critical -> CRITICAL
        pct >= alert && lastNotified < alert -> ALERT
        else -> -1
    }

    /**
     * Notifica cuando el consumo cruza el umbral de alerta o crítico.
     * No repite la misma notificación hasta que el consumo baje del umbral
     * de alerta. [notify] recibe el porcentaje real y el nivel notificado.
     */
    fun checkThreshold(
        prefs: SharedPreferences,
        percent: Double,
        alert: Int,
        critical: Int,
        lastKey: String,
        notify: (Double, Int) -> Unit,
    ) {
        val lastNotified = prefs.getInt(lastKey, -1)
        val pct = percent.toInt()

        when (nextLevel(pct, alert, critical, lastNotified)) {
            CRITICAL -> {
                notify(percent, CRITICAL)
                prefs.edit().putInt(lastKey, critical).apply()
            }
            ALERT -> {
                notify(percent, ALERT)
                prefs.edit().putInt(lastKey, alert).apply()
            }
            else -> {
                // Reset: permite volver a notificar cuando vuelva a cruzar el umbral.
                prefs.edit().putInt(lastKey, -1).apply()
            }
        }
    }
}
