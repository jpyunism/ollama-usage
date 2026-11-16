package com.jpyunism.ollamacloudusage.ui.settings

import com.jpyunism.ollamacloudusage.core.ui.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jpyunism.ollamacloudusage.AlertSettings
import com.jpyunism.ollamacloudusage.RefreshEngine
import com.jpyunism.ollamacloudusage.UsageScheduler

/**
 * Frecuencia de refresco del consumo en segundo plano (spec 12).
 *
 * Tres modos (atajo a un valor típico), presets y un campo para escribir
 * cualquier minuto entre 1 y 720. Debajo, qué motor ejecutará el refresco y
 * qué precisión puede prometer: el slider anterior tenía 31 posiciones con
 * saltos de ~23 min y no decía nada de esto.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RefreshSection(
    settings: AlertSettings,
    onSave: (AlertSettings) -> Unit,
) {
    val minutes = settings.refreshIntervalMinutes
    val engine = UsageScheduler.engineFor(minutes)
    var text by remember { mutableStateOf(minutes.toString()) }

    // Si el valor cambia por chips o stepper, el campo lo refleja.
    LaunchedEffect(minutes) { text = minutes.toString() }

    val commitText: () -> Unit = {
        val sanitized = RefreshOptions.sanitize(text, fallback = minutes)
        text = sanitized.toString()
        if (sanitized != minutes) onSave(settings.copy(refreshIntervalMinutes = sanitized))
    }

    SettingsSection(
        titleRes = R.string.refresh_frequency,
        subtitleRes = R.string.refresh_frequency_description,
        icon = Icons.Outlined.Refresh,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RefreshMode.entries.forEach { mode ->
                FilterChip(
                    selected = RefreshOptions.modeFor(minutes) == mode,
                    onClick = {
                        onSave(settings.copy(refreshIntervalMinutes = RefreshOptions.modeDefault(mode)))
                    },
                    label = { Text(stringResource(modeLabel(mode))) },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RefreshOptions.presets.forEach { preset ->
                FilterChip(
                    selected = preset == minutes,
                    onClick = { onSave(settings.copy(refreshIntervalMinutes = preset)) },
                    label = { Text(intervalLabel(preset)) },
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilledTonalIconButton(
                onClick = {
                    onSave(settings.copy(refreshIntervalMinutes = RefreshOptions.step(minutes, -1)))
                },
                enabled = minutes > RefreshOptions.MIN_MINUTES,
            ) {
                Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.refresh_decrease))
            }

            OutlinedTextField(
                value = text,
                onValueChange = { input -> text = input.filter { it.isDigit() }.take(3) },
                modifier = Modifier
                    .width(150.dp)
                    .onFocusChanged { focus -> if (!focus.isFocused) commitText() },
                singleLine = true,
                label = { Text(stringResource(R.string.refresh_exact_hint)) },
                suffix = { Text(stringResource(R.string.refresh_minutes_unit)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { commitText() }),
            )

            FilledTonalIconButton(
                onClick = {
                    onSave(settings.copy(refreshIntervalMinutes = RefreshOptions.step(minutes, +1)))
                },
                enabled = minutes < RefreshOptions.MAX_MINUTES,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.refresh_increase))
            }
        }

        Spacer(Modifier.height(10.dp))

        Column {
            Text(
                intervalLabel(minutes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(
                    if (engine == RefreshEngine.FOREGROUND_SERVICE) {
                        R.string.refresh_engine_foreground
                    } else {
                        R.string.refresh_engine_workmanager
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun modeLabel(mode: RefreshMode): Int = when (mode) {
    RefreshMode.REAL_TIME -> R.string.refresh_mode_realtime
    RefreshMode.BALANCED -> R.string.refresh_mode_balanced
    RefreshMode.SAVER -> R.string.refresh_mode_saver
}

@Composable
private fun intervalLabel(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.interval_minutes, minutes)
    minutes % 60 == 0 -> stringResource(R.string.interval_hours, minutes / 60)
    else -> stringResource(R.string.interval_hours_minutes, minutes / 60, minutes % 60)
}
