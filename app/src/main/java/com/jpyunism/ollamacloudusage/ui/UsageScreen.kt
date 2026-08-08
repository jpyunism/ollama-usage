package com.jpyunism.ollamacloudusage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpyunism.ollamacloudusage.ModelUsage
import com.jpyunism.ollamacloudusage.UiState
import com.jpyunism.ollamacloudusage.UsageData
import com.jpyunism.ollamacloudusage.UsageViewModel

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun UsageScreen(vm: UsageViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state is UiState.Success) {
            val data = (state as UiState.Success).data
            Header(data)
            UsageMeterCard("Session usage", data.sessionPercent, data.sessionModels, data.sessionResetAt)
            UsageMeterCard("Weekly usage", data.weeklyPercent, data.weeklyModels, null)
            Row {
                Button(onClick = { vm.refresh() }, modifier = Modifier.weight(1f)) {
                    Text("Actualizar")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { vm.clearCookie() }) {
                    Text("Cambiar cookie")
                }
            }
        } else {
            CookieSetup(vm, state)
        }
    }
}

@Composable
private fun CookieSetup(vm: UsageViewModel, state: UiState) {
    var cookie by remember { mutableStateOf("") }

    Text("Consumo Ollama Cloud", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Pega la cookie de tu sesión de ollama.com. Abre DevTools → Application → Cookies " +
            "y copia los valores de aid y __Secure-session separados por ;",
        style = MaterialTheme.typography.bodyMedium,
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

    when (state) {
        is UiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.width(24.dp).height(24.dp))
            Spacer(Modifier.width(8.dp))
            Text("Consultando ollama.com…")
        }
        is UiState.Error -> Text(
            state.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        else -> {}
    }
}

@Composable
private fun Header(data: UsageData) {
    Column {
        Text("Consumo Ollama Cloud", style = MaterialTheme.typography.headlineSmall)
        Text("Plan: ${data.plan}", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun UsageMeterCard(
    title: String,
    percent: Double,
    models: List<ModelUsage>,
    resetAt: Instant?,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${percent}% used",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (percent >= 80) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
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
