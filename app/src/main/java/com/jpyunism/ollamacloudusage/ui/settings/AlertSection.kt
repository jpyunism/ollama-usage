package com.jpyunism.ollamacloudusage.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jpyunism.ollamacloudusage.AlertSettings
import com.jpyunism.ollamacloudusage.R
import com.jpyunism.ollamacloudusage.ResetDisplayMode

/** Alertas de consumo: notificaciones, umbrales, pantalla de bloqueo y reset de cuota. */
@Composable
fun AlertSection(
    settings: AlertSettings,
    onSave: (AlertSettings) -> Unit,
) {
    SettingsSection(
        titleRes = R.string.consumption_alerts,
        subtitleRes = R.string.alerts_description,
        icon = Icons.Filled.Notifications,
    ) {
        // Master switch
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Row(
                Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBox(Icons.Filled.Notifications)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.notifications), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (settings.notificationsEnabled) R.string.enabled else R.string.disabled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { onSave(settings.copy(notificationsEnabled = it)) },
                )
            }
        }

        ThresholdCard(
            title = stringResource(R.string.weekly_limit),
            subtitle = stringResource(R.string.weekly_limit_subtitle),
            alert = settings.weeklyAlert,
            critical = settings.weeklyCritical,
            onAlertChange = { onSave(settings.copy(weeklyAlert = it)) },
            onCriticalChange = { onSave(settings.copy(weeklyCritical = it)) },
        )

        ThresholdCard(
            title = stringResource(R.string.current_session),
            subtitle = stringResource(R.string.session_subtitle),
            alert = settings.sessionAlert,
            critical = settings.sessionCritical,
            onAlertChange = { onSave(settings.copy(sessionAlert = it)) },
            onCriticalChange = { onSave(settings.copy(sessionCritical = it)) },
        )

        // Pantalla de bloqueo
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Row(
                Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBox(Icons.Filled.Lock)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.lock_screen), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (settings.persistentEnabled) R.string.always_visible else R.string.hidden),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.persistentEnabled,
                    onCheckedChange = { onSave(settings.copy(persistentEnabled = it)) },
                )
            }
        }

        // Reset de cuota
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.quota_reset), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.quota_reset_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResetModeChip(
                        label = stringResource(R.string.countdown),
                        selected = settings.resetDisplayMode == ResetDisplayMode.COUNTDOWN,
                        onClick = { onSave(settings.copy(resetDisplayMode = ResetDisplayMode.COUNTDOWN)) },
                    )
                    ResetModeChip(
                        label = stringResource(R.string.date),
                        selected = settings.resetDisplayMode == ResetDisplayMode.DATE,
                        onClick = { onSave(settings.copy(resetDisplayMode = ResetDisplayMode.DATE)) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        if (settings.resetDisplayMode == ResetDisplayMode.COUNTDOWN) R.string.countdown_example else R.string.date_example
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
