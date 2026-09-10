package com.jpyunism.ollamacloudusage.ui.settings

import com.jpyunism.ollamacloudusage.core.ui.R
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpyunism.ollamacloudusage.AppDarkMode
import com.jpyunism.ollamacloudusage.AppLanguage
import com.jpyunism.ollamacloudusage.AppTheme
import com.jpyunism.ollamacloudusage.UsageViewModel

/** Apariencia: idioma, modo claro/oscuro y temas de color. */
@Composable
fun ThemeSection(
    vm: UsageViewModel,
    onSettingsChanged: () -> Unit,
) {
    val currentTheme by vm.theme.collectAsStateWithLifecycle()
    val currentDarkMode by vm.darkMode.collectAsStateWithLifecycle()
    val currentLanguage by vm.language.collectAsStateWithLifecycle()

    SettingsSection(
        titleRes = R.string.appearance,
        subtitleRes = R.string.appearance_description,
        icon = Icons.Outlined.Palette,
        initiallyExpanded = true,
    ) {
        // Idioma
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.language_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                val activity = LocalActivity.current
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { language ->
                        val label = stringResource(language.labelRes)
                        ResetModeChip(
                            label = label,
                            selected = language == currentLanguage,
                            onClick = {
                                vm.updateLanguage(language)
                                // Recrea la activity para que todos los strings se recarguen
                                // con el nuevo locale al instante.
                                activity?.recreate()
                            },
                        )
                    }
                }
            }
        }

        // Modo claro/oscuro
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.dark_mode), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppDarkMode.entries.forEach { mode ->
                        ResetModeChip(
                            label = stringResource(mode.labelRes),
                            selected = mode == currentDarkMode,
                            onClick = {
                                vm.updateDarkMode(mode)
                                onSettingsChanged()
                            },
                        )
                    }
                }
            }
        }

        Text(stringResource(R.string.color_themes), style = MaterialTheme.typography.titleMedium)

        AppTheme.entries.forEach { theme ->
            ThemeRow(
                theme = theme,
                selected = theme == currentTheme,
                onClick = {
                    vm.updateTheme(theme)
                    onSettingsChanged()
                },
            )
        }
    }
}

@Composable
private fun ThemeRow(
    theme: AppTheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
        ),
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Círculo con el color semilla del tema
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(theme.seed),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(theme.labelRes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.selected_theme),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
