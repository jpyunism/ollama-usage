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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jpyunism.ollamacloudusage.AlertSettings
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
        Text("Alertas de consumo", style = MaterialTheme.typography.headlineSmall)
        Text(
            "La app revisa tu consumo en segundo plano y te avisa cuando se acerca " +
                "al límite de tu plan.",
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
                    Text("Notificaciones", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (enabled) "Activadas" else "Desactivadas",
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
            title = "Límite semanal",
            subtitle = "Alerta al cruzar el umbral.",
            alert = weeklyAlert,
            critical = weeklyCritical,
            onAlertChange = { weeklyAlert = it },
            onCriticalChange = { weeklyCritical = it },
        )

        ThresholdCard(
            title = "Sesión actual",
            subtitle = "La sesión se resetea cada ~24h.",
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
                    Text("Pantalla de bloqueo", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (persistentEnabled) "Consumo siempre visible" else "Oculto",
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
                Text("Reset de cuota", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Cómo mostrar el reinicio de la cuota en la notificación y en la app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResetModeChip(
                        label = "Tiempo restante",
                        selected = resetMode == ResetDisplayMode.COUNTDOWN,
                        onClick = { resetMode = ResetDisplayMode.COUNTDOWN },
                    )
                    ResetModeChip(
                        label = "Fecha",
                        selected = resetMode == ResetDisplayMode.DATE,
                        onClick = { resetMode = ResetDisplayMode.DATE },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (resetMode == ResetDisplayMode.COUNTDOWN) {
                        "Ejemplo: \"Sesión resetea en 36 min\""
                    } else {
                        "Ejemplo: \"Sesión resetea el 8 ago, 18:00\""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── Frecuencia de refresco ──
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Frecuencia de refresco", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Cada cuánto se actualiza el consumo en segundo plano. " +
                        "De 1 a 14 min usa un servicio en primer plano; " +
                        "de 15 min en adelante usa WorkManager.",
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
                    Text("1 min", style = MaterialTheme.typography.labelSmall)
                    Text(
                        formatInterval(refreshInterval),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("12 h", style = MaterialTheme.typography.labelSmall)
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
            Text("Guardar configuración")
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

private fun formatInterval(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
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
                label = "Alerta",
                value = alert,
                max = critical - 1,
                onValueChange = onAlertChange,
                color = MaterialTheme.colorScheme.secondary,
            )
            ThresholdSlider(
                label = "Crítico",
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
