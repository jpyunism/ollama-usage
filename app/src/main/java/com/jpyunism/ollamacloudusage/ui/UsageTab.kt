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
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpyunism.ollamacloudusage.BalanceStatus
import com.jpyunism.ollamacloudusage.ModelUsage
import com.jpyunism.ollamacloudusage.R
import com.jpyunism.ollamacloudusage.ResetDisplayMode
import com.jpyunism.ollamacloudusage.UiState
import com.jpyunism.ollamacloudusage.UsageData
import com.jpyunism.ollamacloudusage.UsageViewModel
import com.jpyunism.ollamacloudusage.balanceLabel
import com.jpyunism.ollamacloudusage.computeBalance
import com.jpyunism.ollamacloudusage.formatPercent
import com.jpyunism.ollamacloudusage.formatReset
import com.jpyunism.ollamacloudusage.groupModels
import com.jpyunism.ollamacloudusage.sortedByUsage
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val SESSION_DURATION: Duration = Duration.ofHours(24)
private val WEEK_DURATION: Duration = Duration.ofHours(168)

@Composable
fun UsageTab(vm: UsageViewModel, state: UiState) {
    val showAuthSetup by vm.showAuthSetup.collectAsStateWithLifecycle()
    val showCookieWebView by vm.showCookieWebView.collectAsStateWithLifecycle()
    if (showCookieWebView) {
        CookieWebView(
            onCookieCaptured = { vm.saveCookieFromWebView(it) },
            onClose = { vm.closeCookieWebView() },
        )
        return
    }
    if (showAuthSetup) {
        CookieSetup(vm, state)
        return
    }
    when (state) {
        // Loading va fuera de cualquier scrollable (evita anidar constraints).
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
            }
        }
        // Idle/Error renderizan el setup de acceso (que ya es scrollable por
        // sí mismo): anidarlo aquí dentro del Column.verticalScroll crasheaba
        // con "infinity maximum height constraints" en algunos dispositivos.
        is UiState.Error -> CookieSetup(vm, state)
        UiState.Idle -> CookieSetup(vm, state)
        is UiState.Success -> SuccessContent(state.data, state.lastUpdated, onRefresh = { vm.refresh() }, onChangeAuth = { vm.openAuthSetup() })
    }
}

@Composable
private fun SuccessContent(
    data: UsageData,
    lastUpdated: Long?,
    onRefresh: () -> Unit,
    onChangeAuth: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(data, lastUpdated)
        UsageMeterCard(stringResource(R.string.session_usage), data.sessionPercent, data.sessionModels, data.sessionResetAt, SESSION_DURATION)
        UsageMeterCard(stringResource(R.string.weekly_usage), data.weeklyPercent, data.weeklyModels, data.weeklyResetAt, WEEK_DURATION)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.refresh))
            }
            OutlinedButton(onClick = onChangeAuth, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.change_auth))
            }
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
    duration: Duration,
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
                    stringResource(R.string.percent_used, formatPercent(percent)),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatReset(resetAt, ResetDisplayMode.COUNTDOWN)?.replaceFirstChar { it.uppercase() }
                            ?: stringResource(R.string.resets_at, resetAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val balance = computeBalance(
                        percent,
                        resetAt,
                        Instant.now(),
                        duration,
                    )
                    val balanceText = balanceLabel(
                        balance,
                        stringResource(R.string.balance_deficit),
                        stringResource(R.string.balance_surplus),
                    )
                    if (balanceText != null) {
                        Text(
                            " · $balanceText",
                            style = MaterialTheme.typography.bodySmall,
                            color = when (balance!!.status) {
                                BalanceStatus.DEFICIT -> MaterialTheme.colorScheme.error
                                BalanceStatus.SURPLUS -> MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }
            }
            if (models.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                sortedByUsage(models).forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(m.model, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.requests_percent, m.requests, formatPercent(m.percent)),
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
    val othersLabel = stringResource(R.string.others)
    val segments = groupModels(models, othersLabel = othersLabel)
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(shape)
            .semantics {
                contentDescription = segments.joinToString { s ->
                    "${s.label} ${formatPercent(s.percent)}%"
                }
            },
    ) {
        if (segments.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        } else {
            segments.forEach { s ->
                val color = s.colorKey?.let { modelColor(it) }
                    ?: MaterialTheme.colorScheme.outlineVariant
                Box(
                    Modifier
                        .weight(s.percent.toFloat().coerceAtLeast(0.1f))
                        .fillMaxHeight()
                        .background(color)
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
