package com.jpyunism.ollamacloudusage.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jpyunism.ollamacloudusage.ModelUsage
import com.jpyunism.ollamacloudusage.R
import com.jpyunism.ollamacloudusage.ResetDisplayMode
import com.jpyunism.ollamacloudusage.UiState
import com.jpyunism.ollamacloudusage.UsageData
import com.jpyunism.ollamacloudusage.UsageViewModel
import com.jpyunism.ollamacloudusage.formatReset
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun UsageTab(vm: UsageViewModel, state: UiState) {
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
                UsageMeterCard(stringResource(R.string.session_usage), data.sessionPercent, data.sessionModels, data.sessionResetAt)
                UsageMeterCard(stringResource(R.string.weekly_usage), data.weeklyPercent, data.weeklyModels, data.weeklyResetAt)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.refresh() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.refresh))
                    }
                    OutlinedButton(onClick = { vm.clearAuth() }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.change_auth))
                    }
                }
            }
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
                }
            }
            is UiState.Error -> CookieSetup(vm, state)
            UiState.Idle -> CookieSetup(vm, state)
        }
    }
}

@Composable
private fun Header(data: UsageData, lastUpdated: Long?) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.plan_format, data.plan), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            if (lastUpdated != null) {
                val time = Instant.ofEpochMilli(lastUpdated)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                Text(
                    stringResource(R.string.updated_at, time),
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
                    stringResource(R.string.percent_used, "${percent}"),
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
                    formatReset(resetAt, ResetDisplayMode.COUNTDOWN)?.replaceFirstChar { it.uppercase() }
                        ?: stringResource(R.string.resets_at, resetAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))),
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
                            stringResource(R.string.requests_percent, m.requests, "${m.percent}"),
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
