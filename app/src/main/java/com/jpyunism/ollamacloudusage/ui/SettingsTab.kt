package com.jpyunism.ollamacloudusage.ui

import androidx.activity.compose.LocalActivity
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpyunism.ollamacloudusage.AlertSettings
import com.jpyunism.ollamacloudusage.AppDarkMode
import com.jpyunism.ollamacloudusage.AppLanguage
import com.jpyunism.ollamacloudusage.AppTheme
import com.jpyunism.ollamacloudusage.DownloadState
import com.jpyunism.ollamacloudusage.R
import com.jpyunism.ollamacloudusage.ResetDisplayMode
import com.jpyunism.ollamacloudusage.UpdateCheckOutcome
import com.jpyunism.ollamacloudusage.UpdateInfo
import com.jpyunism.ollamacloudusage.UpdaterService
import com.jpyunism.ollamacloudusage.UsageScheduler
import com.jpyunism.ollamacloudusage.UsageViewModel

/**
 * Configuración unificada: apariencia (idioma, modo, temas), alertas de
 * consumo y actualizaciones. Cada cambio se guarda automáticamente al instante
 * (sin botón de guardar); el snackbar de confirmación lo maneja UsageScreen.
 */
@Composable
fun SettingsTab(
    vm: UsageViewModel,
    settings: AlertSettings,
    onSettingsChanged: () -> Unit,
) {
    // ── Estado del ViewModel ──
    val currentTheme by vm.theme.collectAsStateWithLifecycle()
    val currentDarkMode by vm.darkMode.collectAsStateWithLifecycle()
    val currentLanguage by vm.language.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val checkingUpdate by vm.checkingUpdate.collectAsStateWithLifecycle()
    val checkResult by vm.checkResult.collectAsStateWithLifecycle()
    val download by vm.download.collectAsStateWithLifecycle()

    // Persiste el cambio y avisa al snackbar debounced de UsageScreen.
    fun save(newSettings: AlertSettings) {
        vm.updateSettings(newSettings)
        onSettingsChanged()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ════ Apariencia ════
        SectionHeader(
            title = stringResource(R.string.appearance),
            subtitle = stringResource(R.string.appearance_description),
        )

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

        // ════ Alertas de consumo ════
        SectionHeader(
            title = stringResource(R.string.consumption_alerts),
            subtitle = stringResource(R.string.alerts_description),
        )

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
                    onCheckedChange = { save(settings.copy(notificationsEnabled = it)) },
                )
            }
        }

        ThresholdCard(
            title = stringResource(R.string.weekly_limit),
            subtitle = stringResource(R.string.weekly_limit_subtitle),
            alert = settings.weeklyAlert,
            critical = settings.weeklyCritical,
            onAlertChange = { save(settings.copy(weeklyAlert = it)) },
            onCriticalChange = { save(settings.copy(weeklyCritical = it)) },
        )

        ThresholdCard(
            title = stringResource(R.string.current_session),
            subtitle = stringResource(R.string.session_subtitle),
            alert = settings.sessionAlert,
            critical = settings.sessionCritical,
            onAlertChange = { save(settings.copy(sessionAlert = it)) },
            onCriticalChange = { save(settings.copy(sessionCritical = it)) },
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
                    onCheckedChange = { save(settings.copy(persistentEnabled = it)) },
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
                        onClick = { save(settings.copy(resetDisplayMode = ResetDisplayMode.COUNTDOWN)) },
                    )
                    ResetModeChip(
                        label = stringResource(R.string.date),
                        selected = settings.resetDisplayMode == ResetDisplayMode.DATE,
                        onClick = { save(settings.copy(resetDisplayMode = ResetDisplayMode.DATE)) },
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

        // Frecuencia de refresco
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
                    value = settings.refreshIntervalMinutes.toFloat(),
                    onValueChange = { save(settings.copy(refreshIntervalMinutes = it.toInt())) },
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
                        formatInterval(settings.refreshIntervalMinutes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(R.string.hours_12), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // ════ Actualización ════
        SectionHeader(
            title = stringResource(R.string.section_update),
            subtitle = stringResource(R.string.update_section_description),
        )

        UpdateCard(
            currentVersion = vm.appVersion,
            update = update,
            checking = checkingUpdate,
            result = checkResult,
            download = download,
            onCheck = { vm.checkForUpdateNow() },
            onDownload = { vm.startUpdateDownload(it) },
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IconBox(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** Tarjeta de actualización: versión actual, chequeo manual y descarga. */
@Composable
private fun UpdateCard(
    currentVersion: String,
    update: UpdateInfo?,
    checking: Boolean,
    result: UpdateCheckOutcome?,
    download: DownloadState,
    onCheck: () -> Unit,
    onDownload: (UpdateInfo) -> Unit,
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.version_current, currentVersion),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.update_auto_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            when {
                download is DownloadState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { download.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.update_downloading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                download is DownloadState.Failed -> {
                    Text(
                        stringResource(R.string.update_download_failed, download.message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                download is DownloadState.NeedsPermission -> {
                    Text(
                        stringResource(R.string.update_needs_permission_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    val context = LocalContext.current
                    FilledTonalButton(
                        onClick = { UpdaterService.openInstallPermissionSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.update_install_permission))
                    }
                }

                update != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.update_available, update.versionName),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { onDownload(update) }) {
                            Text(stringResource(R.string.update_install))
                        }
                    }
                }

                result is UpdateCheckOutcome.UpToDate -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.update_up_to_date, currentVersion),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                result is UpdateCheckOutcome.Failed -> {
                    Text(
                        stringResource(R.string.update_check_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                checking -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.update_checking),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    Text(
                        stringResource(R.string.update_check_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onCheck,
                enabled = !checking && download !is DownloadState.Downloading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.update_check))
            }
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
