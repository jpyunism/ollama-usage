package com.jpyunism.ollamacloudusage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jpyunism.ollamacloudusage.AlertSettings
import com.jpyunism.ollamacloudusage.R
import com.jpyunism.ollamacloudusage.ResetDisplayMode
import com.jpyunism.ollamacloudusage.UsageScheduler
import com.jpyunism.ollamacloudusage.UsageViewModel

@Composable
fun AlertsTab(vm: UsageViewModel, settings: AlertSettings) {
    var enabled by remember { mutableStateOf(settings.notificationsEnabled) }
    var weeklyAlert by remember { mutableIntStateOf(settings.weeklyAlert) }
    var weeklyCritical by remember { mutableIntStateOf(settings.weeklyCritical) }
    var sessionAlert by remember { mutableIntStateOf(settings.sessionAlert) }
    var sessionCritical by remember { mutableIntStateOf(settings.sessionCritical) }
    var persistentEnabled by remember { mutableStateOf(settings.persistentEnabled) }
    var refreshInterval by remember { mutableIntStateOf(settings.refreshIntervalMinutes) }
    var resetMode by remember { mutableStateOf(settings.resetDisplayMode) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.consumption_alerts), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.alerts_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Master switch
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Row(
                Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.notifications), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (enabled) R.string.enabled else R.string.disabled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                )
            }
        }

        ThresholdCard(
            title = stringResource(R.string.weekly_limit),
            subtitle = stringResource(R.string.weekly_limit_subtitle),
            alert = weeklyAlert,
            critical = weeklyCritical,
            onAlertChange = { weeklyAlert = it },
            onCriticalChange = { weeklyCritical = it },
        )

        ThresholdCard(
            title = stringResource(R.string.current_session),
            subtitle = stringResource(R.string.session_subtitle),
            alert = sessionAlert,
            critical = sessionCritical,
            onAlertChange = { sessionAlert = it },
            onCriticalChange = { sessionCritical = it },
        )

        // ── Pantalla de bloqueo ──
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Row(
                Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.lock_screen), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (persistentEnabled) R.string.always_visible else R.string.hidden),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = persistentEnabled,
                    onCheckedChange = { persistentEnabled = it },
                )
            }
        }

        // ── Reset de cuota ──
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
                        selected = resetMode == ResetDisplayMode.COUNTDOWN,
                        onClick = { resetMode = ResetDisplayMode.COUNTDOWN },
                    )
                    ResetModeChip(
                        label = stringResource(R.string.date),
                        selected = resetMode == ResetDisplayMode.DATE,
                        onClick = { resetMode = ResetDisplayMode.DATE },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        if (resetMode == ResetDisplayMode.COUNTDOWN) R.string.countdown_example else R.string.date_example
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── Frecuencia de refresco ──
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.refresh_frequency), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.refresh_frequency_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Slider(
                    value = refreshInterval.toFloat(),
                    onValueChange = { refreshInterval = it.toInt() },
                    valueRange = UsageScheduler.MIN_REFRESH_MINUTES.toFloat()..
                        UsageScheduler.MAX_REFRESH_MINUTES.toFloat(),
                    steps = 30,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.min_1), style = MaterialTheme.typography.labelSmall)
                    Text(
                        formatInterval(refreshInterval),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(R.string.hours_12), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        FilledTonalButton(
            onClick = {
                vm.updateSettings(
                    AlertSettings(
                        notificationsEnabled = enabled,
                        weeklyAlert = weeklyAlert,
                        weeklyCritical = weeklyCritical,
                        sessionAlert = sessionAlert,
                        sessionCritical = sessionCritical,
                        persistentEnabled = persistentEnabled,
                        refreshIntervalMinutes = refreshInterval,
                        resetDisplayMode = resetMode,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.save_settings))
        }
    }
}

@Composable
private fun ResetModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

@Composable
private fun formatInterval(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.interval_minutes, minutes)
    minutes % 60 == 0 -> stringResource(R.string.interval_hours, minutes / 60)
    else -> stringResource(R.string.interval_hours_minutes, minutes / 60, minutes % 60)
}

@Composable
private fun ThresholdCard(
    title: String,
    subtitle: String,
    alert: Int,
    critical: Int,
    onAlertChange: (Int) -> Unit,
    onCriticalChange: (Int) -> Unit,
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            ThresholdSlider(
                label = stringResource(R.string.alert),
                value = alert,
                max = critical - 1,
                onValueChange = onAlertChange,
                color = MaterialTheme.colorScheme.secondary,
            )
            ThresholdSlider(
                label = stringResource(R.string.critical),
                value = critical,
                min = alert + 1,
                onValueChange = onCriticalChange,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ThresholdSlider(
    label: String,
    value: Int,
    min: Int = AlertSettings.MIN_THRESHOLD,
    max: Int = AlertSettings.MAX_THRESHOLD,
    color: Color,
    onValueChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(56.dp),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            "$value%",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.End,
        )
    }
}
