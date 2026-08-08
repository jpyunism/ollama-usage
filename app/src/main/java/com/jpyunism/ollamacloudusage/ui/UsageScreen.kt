package com.jpyunism.ollamacloudusage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpyunism.ollamacloudusage.AlertSettings
import com.jpyunism.ollamacloudusage.AppTheme
import com.jpyunism.ollamacloudusage.ModelUsage
import com.jpyunism.ollamacloudusage.UiState
import com.jpyunism.ollamacloudusage.UsageData
import com.jpyunism.ollamacloudusage.UsageScheduler
import com.jpyunism.ollamacloudusage.UsageViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Usage("Uso", Icons.Outlined.Speed, Icons.Filled.Speed),
    Alerts("Alertas", Icons.Outlined.Notifications, Icons.Filled.Notifications),
    Themes("Temas", Icons.Outlined.Palette, Icons.Filled.Palette),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(vm: UsageViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.Usage) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Ollama Usage", fontWeight = FontWeight.SemiBold) },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            Icon(
                                if (tab == t) t.selectedIcon else t.icon,
                                contentDescription = t.label,
                            )
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            when (tab) {
                Tab.Usage -> UsageTab(vm, state)
                Tab.Alerts -> AlertsTab(vm, settings)
                Tab.Themes -> ThemesTab(vm)
            }
        }
    }
}

// ─────────────────────────── Tab: Uso ───────────────────────────

@Composable
private fun UsageTab(vm: UsageViewModel, state: UiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            is UiState.Success -> {
                val data = state.data
                Header(data, state.lastUpdated)
                UsageMeterCard("Session usage", data.sessionPercent, data.sessionModels, data.sessionResetAt)
                UsageMeterCard("Weekly usage", data.weeklyPercent, data.weeklyModels, null)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.refresh() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Actualizar")
                    }
                    OutlinedButton(onClick = { vm.clearCookie() }, modifier = Modifier.weight(1f)) {
                        Text("Cambiar cookie")
                    }
                }
            }
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Consultando ollama.com…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            is UiState.Error -> CookieSetup(vm, state)
            UiState.Idle -> CookieSetup(vm, state)
        }
    }
}

@Composable
private fun CookieSetup(vm: UsageViewModel, state: UiState) {
    var cookie by remember { mutableStateOf("") }

    Text("Conecta tu cuenta", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Pega la cookie de tu sesión de ollama.com. Abre DevTools → Application → Cookies " +
            "y copia los valores de aid y __Secure-session separados por ;",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = cookie,
        onValueChange = { cookie = it },
        label = { Text("Cookie (aid=...; __Secure-session=...)") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
    )
    Button(
        onClick = { vm.saveCookie(cookie) },
        enabled = cookie.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Guardar y consultar")
    }

    if (state is UiState.Error) {
        Text(
            state.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Header(data: UsageData, lastUpdated: Long?) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Plan: ${data.plan}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            if (lastUpdated != null) {
                val time = Instant.ofEpochMilli(lastUpdated)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                Text(
                    "Actualizado $time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UsageMeterCard(
    title: String,
    percent: Double,
    models: List<ModelUsage>,
    resetAt: Instant?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${percent}% used",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        percent >= 95 -> MaterialTheme.colorScheme.error
                        percent >= 80 -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearUsageBar(percent, models)
            if (resetAt != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Resets: ${resetAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (models.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                models.sortedByDescending { it.requests }.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(m.model, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            "${m.requests} req · ${m.percent}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LinearUsageBar(percent: Double, models: List<ModelUsage>) {
    val barHeight = 12.dp
    Row(Modifier.fillMaxWidth().height(barHeight)) {
        if (models.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            )
        } else {
            models.forEach { m ->
                Box(
                    Modifier
                        .weight(m.percent.toFloat().coerceAtLeast(0.1f))
                        .fillMaxHeight()
                        .background(modelColor(m.model), RoundedCornerShape(6.dp))
                )
            }
        }
    }
}

private val palette = listOf(
    Color(0xFF4F46E5), Color(0xFFF97316), Color(0xFF22C55E),
    Color(0xFF2563EB), Color(0xFFEC4899), Color(0xFF14B8A6),
    Color(0xFFEAB308), Color(0xFF8B5CF6), Color(0xFFEF4444), Color(0xFF06B6D4),
)

private fun modelColor(model: String): Color =
    palette[abs(model.hashCode()) % palette.size]

// ─────────────────────────── Tab: Alertas ───────────────────────────

@Composable
private fun AlertsTab(vm: UsageViewModel, settings: AlertSettings) {
    var enabled by remember { mutableStateOf(settings.notificationsEnabled) }
    var weeklyAlert by remember { mutableIntStateOf(settings.weeklyAlert) }
    var weeklyCritical by remember { mutableIntStateOf(settings.weeklyCritical) }
    var sessionAlert by remember { mutableIntStateOf(settings.sessionAlert) }
    var sessionCritical by remember { mutableIntStateOf(settings.sessionCritical) }
    var persistentEnabled by remember { mutableStateOf(settings.persistentEnabled) }
    var refreshInterval by remember { mutableIntStateOf(settings.refreshIntervalMinutes) }

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
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Guardar configuración")
        }
    }
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
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

// ─────────────────────────── Tab: Temas ───────────────────────────

@Composable
private fun ThemesTab(vm: UsageViewModel) {
    val current by vm.theme.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Temas de color", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Elige el color principal de la app. Se aplica al instante y se guarda " +
                "automáticamente. El tema Sistema usa los colores dinámicos de tu wallpaper " +
                "(Android 12+).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AppTheme.entries.forEach { theme ->
            ThemeRow(
                theme = theme,
                selected = theme == current,
                onClick = { vm.updateTheme(theme) },
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
                theme.label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Seleccionado",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
