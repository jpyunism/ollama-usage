package com.jpyunism.ollamacloudusage.ui.settings

import com.jpyunism.ollamacloudusage.core.ui.R
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpyunism.ollamacloudusage.UsageViewModel

/**
 * Resumen diario programado (Feature B lote 2, issue #19):
 * switch en la cabecera + hora con time picker (Material3 TimePicker en dialogo).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DailySummarySection(vm: UsageViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    SettingsSection(
        titleRes = R.string.daily_summary,
        subtitleRes = R.string.daily_summary_description,
        icon = Icons.Outlined.Schedule,
        trailing = {
            Switch(
                checked = settings.dailySummaryEnabled,
                onCheckedChange = { vm.updateDailySummary(enabled = it) },
            )
        },
    ) {
        if (settings.dailySummaryEnabled) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.daily_summary_time), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                FilledTonalButton(onClick = { showTimePicker = true }) {
                    Text("%02d:%02d".format(settings.dailySummaryHour, settings.dailySummaryMinute))
                }
            }
        } else {
            Text(
                stringResource(R.string.daily_summary_disabled_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = settings.dailySummaryHour,
            initialMinute = settings.dailySummaryMinute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.daily_summary_time)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                Button(onClick = {
                    vm.updateDailySummary(
                        enabled = true,
                        hour = timeState.hour,
                        minute = timeState.minute,
                    )
                    showTimePicker = false
                }) { Text(stringResource(R.string.save_generic)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
