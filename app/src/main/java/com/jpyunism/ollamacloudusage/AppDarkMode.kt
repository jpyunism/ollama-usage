package com.jpyunism.ollamacloudusage

import androidx.annotation.StringRes

/** Modo claro/oscuro de la UI. [System] sigue al sistema. */
enum class AppDarkMode(@StringRes val labelRes: Int) {
    System(R.string.dark_mode_system),
    Light(R.string.dark_mode_light),
    Dark(R.string.dark_mode_dark);

    companion object {
        /** Resuelve el modo en un booleano: true = oscuro. [systemDark] es el modo del sistema. */
        fun resolveDarkMode(mode: AppDarkMode, systemDark: Boolean): Boolean = when (mode) {
            System -> systemDark
            Light -> false
            Dark -> true
        }
    }
}
