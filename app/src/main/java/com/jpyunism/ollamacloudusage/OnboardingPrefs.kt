package com.jpyunism.ollamacloudusage

import android.content.SharedPreferences

/**
 * Helper para el flag de onboarding completado (issue #61).
 *
 * El flag NO es un secreto, asi que se persiste en claro en
 * [SecurePrefs]/SharedPreferences directamente (sin encriptar).
 *
 * - default: `false` (aun no completado).
 * - la primera vez el usuario completa el flujo (o salta) -> `true` y nunca
 *   se vuelve a mostrar, salvo que se borre desde Configuracion.
 *
 * Helper puro: recibe SharedPreferences en el constructor para testearlo
 * sin Android (mocks o fake prefs).
 */
class OnboardingPrefs(
    private val prefs: SharedPreferences,
) {

    /** true si el usuario ya paso por el onboarding alguna vez. */
    fun isCompleted(): Boolean =
        prefs.getBoolean(PrefsKeys.ONBOARDING_COMPLETED, false)

    /** Marca el onboarding como completado (persiste inmediatamente). */
    fun markCompleted() {
        prefs.edit()
            .putBoolean(PrefsKeys.ONBOARDING_COMPLETED, true)
            .apply()
    }

    /** Borra el flag (uso interno / tests / "reset onboarding" desde Settings). */
    fun reset() {
        prefs.edit()
            .remove(PrefsKeys.ONBOARDING_COMPLETED)
            .apply()
    }
}
