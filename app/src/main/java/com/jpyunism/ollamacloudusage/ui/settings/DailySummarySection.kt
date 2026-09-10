package com.jpyunism.ollamacloudusage.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpyunism.ollamacloudusage.R
import com.jpyunism.ollamacloudusage.UsageViewModel

/**
 * Resumen diario programado (Feature B lote 2, issue #19):
 * switch + hora con time picker (Material3 TimePicker en dialogo).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DailySummarySection(vm: UsageViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showTimePicker by remember { mutableStateOf(false) }

    SettingsSection(
        titleRes = R.string.daily_summary,
        subtitleRes = R.string.daily_summary_description,
        icon = Icons.Outlined.Schedule,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBox(Icons.Outlined.Schedule)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.daily_summary), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.daily_summary_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.dailySummaryEnabled,
                onCheckedChange = { vm.updateDailySummary(enabled = it) },
            )
        }
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
        }
    }

    if (showTimePicker) {
        val initial = java.time.LocalTime.of(settings.dailySummaryHour, settings.dailySummaryMinute)
        var picked by remember { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.daily_summary_time)) },
            text = {
                TimePicker(
                    state = rememberTimePickerState(
                        initialHour = settings.dailySummaryHour,
                        initialMinute = settings.dailySummaryMinute,
                        is24Hour = true,
                    ),
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.updateDailySummary(enabled = true, hour = picked.hour, minute = picked.minute)
                    showTimePicker = false
                }) { Text(stringResource(R.string.save_generic)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
