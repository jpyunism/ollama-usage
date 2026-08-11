package com.jpyunism.ollamacloudusage.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.jpyunism.ollamacloudusage.AppDarkMode
import com.jpyunism.ollamacloudusage.AppTheme

/** Genera el esquema claro a partir del color semilla. */
private fun lightScheme(seed: Color): ColorScheme = lightColorScheme(
    primary = seed,
    onPrimary = Color.White,
    primaryContainer = lerp(seed, Color.White, 0.85f),
    onPrimaryContainer = lerp(seed, Color.Black, 0.6f),
    secondary = lerp(seed, Color.Black, 0.25f),
    onSecondary = Color.White,
    secondaryContainer = lerp(seed, Color.White, 0.75f),
    onSecondaryContainer = lerp(seed, Color.Black, 0.6f),
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F5F9),
    error = Color(0xFFDC2626),
)

/** Genera el esquema oscuro a partir del color semilla. */
private fun darkScheme(seed: Color): ColorScheme = darkColorScheme(
    primary = lerp(seed, Color.White, 0.35f),
    onPrimary = lerp(seed, Color.Black, 0.7f),
    primaryContainer = lerp(seed, Color.Black, 0.65f),
    onPrimaryContainer = lerp(seed, Color.White, 0.75f),
    secondary = lerp(seed, Color.White, 0.55f),
    onSecondary = lerp(seed, Color.Black, 0.7f),
    secondaryContainer = lerp(seed, Color.Black, 0.55f),
    onSecondaryContainer = lerp(seed, Color.White, 0.8f),
    background = Color(0xFF0D0D0D),
    surface = Color(0xFF171717),
    surfaceVariant = Color(0xFF262626),
    error = Color(0xFFF87171),
)

@Composable
fun OllamaUsageTheme(
    theme: AppTheme = AppTheme.System,
    darkMode: AppDarkMode = AppDarkMode.System,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme = AppDarkMode.resolveDarkMode(darkMode, isSystemInDarkTheme())

    // "Sistema" usa Material You (colores dinámicos) en Android 12+; fallback a Índigo.
    val colorScheme = when {
        theme == AppTheme.System && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> darkScheme(theme.seed)
        else -> lightScheme(theme.seed)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
