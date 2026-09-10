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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpyunism.ollamacloudusage.Account
import com.jpyunism.ollamacloudusage.BalanceStatus
import com.jpyunism.ollamacloudusage.ModelUsage
import com.jpyunism.ollamacloudusage.R
import com.jpyunism.ollamacloudusage.ResetDisplayMode
import com.jpyunism.ollamacloudusage.ResetStrings
import com.jpyunism.ollamacloudusage.UiState
import com.jpyunism.ollamacloudusage.UsageData
import com.jpyunism.ollamacloudusage.HistoryPeriod
import com.jpyunism.ollamacloudusage.UsageViewModel
import com.jpyunism.ollamacloudusage.UsageSnapshot
import com.jpyunism.ollamacloudusage.ProjectionEngine
import com.jpyunism.ollamacloudusage.modelPercent
import com.jpyunism.ollamacloudusage.balanceLabel
import com.jpyunism.ollamacloudusage.computeBalance
import com.jpyunism.ollamacloudusage.formatPercent
import com.jpyunism.ollamacloudusage.shareSummaryText
import com.jpyunism.ollamacloudusage.formatReset
import com.jpyunism.ollamacloudusage.groupModels
import com.jpyunism.ollamacloudusage.TrafficLight
import com.jpyunism.ollamacloudusage.othersGroup
import com.jpyunism.ollamacloudusage.sortedByUsage
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.launch


@Composable
fun UsageTab(vm: UsageViewModel, state: UiState, isRefreshing: Boolean = false) {
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
        is UiState.Success -> {
            // Feature C (issue #20): bottom sheet con la evolución del modelo.
            var modelSheet by rememberSaveable { mutableStateOf<String?>(null) }
            val context = LocalContext.current
            val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            val scope = rememberCoroutineScope()
            val snackbarHostState = remember { SnackbarHostState() }
            val copiedMessage = stringResource(R.string.copied)
            PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { vm.refresh(fromPull = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            // Umbrales del semáforo = los mismos de las alertas (prefs,
            // default 80/95); el semáforo es visual, no depende de NOTIF_ENABLED.
            val alertSettings by vm.settings.collectAsStateWithLifecycle()
            val accounts by vm.accounts.collectAsStateWithLifecycle()
            val activeAccountId by vm.activeAccountId.collectAsStateWithLifecycle()
            val historySnapshots by vm.history.collectAsStateWithLifecycle()
            SuccessContent(
                data = state.data,
                lastUpdated = state.lastUpdated,
                weeklyProjection = state.weeklyProjection,
                sessionProjection = state.sessionProjection,
                alertThreshold = alertSettings.weeklyAlert,
                criticalThreshold = alertSettings.weeklyCritical,
                accounts = accounts,
                activeAccountId = activeAccountId,
                onSelectAccount = { vm.switchAccount(it) },
                onModelClick = { modelSheet = it },
                onRefresh = { vm.refresh(fromPull = true) },
                onChangeAuth = { vm.openAuthSetup() },
                onShare = {
                    // Feature E (#22): copiar al portapapeles + share sheet.
                    val text = shareSummaryText(state.data)
                    clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("ollama-usage", text))
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(android.content.Intent.createChooser(send, null))
                    scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                },
            )
            SnackbarHost(snackbarHostState)
            modelSheet?.let { name ->
                ModelEvolutionSheet(
                    modelName = name,
                    snapshots = historySnapshots.snapshots,
                    onDismiss = { modelSheet = null },
                )
            }
            }
        }
    }
}

@Composable
private fun SuccessContent(
    data: UsageData,
    lastUpdated: Long?,
    weeklyProjection: ProjectionEngine.Result?,
    sessionProjection: ProjectionEngine.Result?,
    alertThreshold: Int,
    criticalThreshold: Int,
    accounts: List<Account>,
    activeAccountId: String?,
    onSelectAccount: (String) -> Unit,
    onModelClick: (String) -> Unit = {},
    onRefresh: () -> Unit,
    onChangeAuth: () -> Unit,
    onShare: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(data, lastUpdated)
        // Proyeccion de agotamiento (issue #54): solo si hay datos suficientes.
        if (weeklyProjection != null) {
            ProjectionCard(
                title = stringResource(R.string.projection_weekly_title),
                result = weeklyProjection,
                resetAt = data.weeklyResetAt,
            )
        }
        if (sessionProjection != null) {
            ProjectionCard(
                title = stringResource(R.string.projection_session_title),
                result = sessionProjection,
                resetAt = data.sessionResetAt,
            )
        }
        if (accounts.size > 1) {
            // Switcher de cuentas (Feature A): solo aparece con más de una.
            AccountSwitcherRow(accounts, activeAccountId, onSelectAccount)
        }
        UsageMeterCard(stringResource(R.string.session_usage), data.sessionPercent, data.sessionModels, data.sessionResetAt, HistoryPeriod.SESSION.duration, alertThreshold, criticalThreshold, onModelClick)
        UsageMeterCard(stringResource(R.string.weekly_usage), data.weeklyPercent, data.weeklyModels, data.weeklyResetAt, HistoryPeriod.WEEK.duration, alertThreshold, criticalThreshold, onModelClick)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // maxLines=1 + autoSize: en pantallas angostas el texto de los
            // botones se quebraba a mitad de palabra ("Refres h", "Change
            // access" en 2 líneas). AutoSize reduce el texto para que cada
            // botón quede siempre en una sola línea.
            Button(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.refresh),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 14.sp),
                )
            }
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.share),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 14.sp),
                )
            }
            OutlinedButton(onClick = onChangeAuth, modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.change_auth),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 14.sp),
                )
            }
        }
    }
}

@Composable
private fun AccountSwitcherRow(
    accounts: List<Account>,
    activeAccountId: String?,
    onSelectAccount: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        accounts.forEach { account ->
            FilterChip(
                selected = account.id == activeAccountId,
                onClick = { onSelectAccount(account.id) },
                label = { Text(account.label) },
            )
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
    alertThreshold: Int,
    criticalThreshold: Int,
    onModelClick: (String) -> Unit = {},
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
                    color = TrafficLightColors.getValue(TrafficLight.paceColor(percent, alertThreshold, criticalThreshold)),
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearUsageBar(percent, models)
            if (resetAt != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatReset(
                            resetAt,
                            ResetDisplayMode.COUNTDOWN,
                            ResetStrings(
                                resetsSoon = stringResource(R.string.reset_soon),
                                resetsIn = stringResource(R.string.reset_in),
                                lessThanMin = stringResource(R.string.reset_less_than_min),
                                resetsOn = stringResource(R.string.reset_on),
                            ),
                        )?.replaceFirstChar { it.uppercase() }
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
                val others = othersGroup(sortedByUsage(models))
                sortedByUsage(models).forEach { m ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            // Feature C (issue #20): tap en el modelo → evolución.
                            .clickable { onModelClick(m.model) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .background(
                                        if (others?.contains(m) == true) {
                                            MaterialTheme.colorScheme.outlineVariant
                                        } else {
                                            modelColor(m.model)
                                        },
                                        CircleShape,
                                    )
                            )
                            Text(m.model, style = MaterialTheme.typography.bodyMedium)
                        }
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

/**
 * Bottom sheet con la evolución del % de un modelo sobre el histórico
 * (Feature C, issue #20). Reusa el criterio de dibujo de UsageChart pero
 * simplificado: línea del % del modelo en los snapshots que lo reportan.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ModelEvolutionSheet(
    modelName: String,
    snapshots: List<UsageSnapshot>,
    onDismiss: () -> Unit,
) {
    val points = snapshots.mapNotNull { s ->
        modelPercent(s, modelName)?.let { s.timestampMillis to it }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp).padding(bottom = 24.dp)) {
            Text(modelName, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            if (points.size < 2) {
                Text(
                    stringResource(R.string.model_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                Spacer(Modifier.height(12.dp))
                ModelEvolutionChart(points)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        R.string.model_history_current,
                        formatPercent(points.last().second),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Gráfico de línea simple (Canvas) de la evolución del % de un modelo. */
@Composable
private fun ModelEvolutionChart(points: List<Pair<Long, Double>>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val w = size.width
        val h = size.height
        val minX = points.first().first.toFloat()
        val maxX = points.last().first.toFloat()
        val rangeX = (maxX - minX).coerceAtLeast(1f)
        // Escala Y: 0..100 fijo (los % de modelos no deberían superar 100).
        fun x(t: Long) = ((t - minX) / rangeX) * w
        fun y(pct: Double) = h - (pct.toFloat().coerceIn(0f, 100f) / 100f) * h
        // Grid 0/50/100
        listOf(0f, 0.5f, 1f).forEach { f ->
            drawLine(gridColor, Offset(0f, h * f), Offset(w, h * f), strokeWidth = 1.dp.toPx())
        }
        val path = Path()
        points.forEachIndexed { i, (t, pct) ->
            val p = Offset(x(t), y(pct))
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}
