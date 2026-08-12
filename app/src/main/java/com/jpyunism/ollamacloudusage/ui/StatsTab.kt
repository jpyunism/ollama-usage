package com.jpyunism.ollamacloudusage.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpyunism.ollamacloudusage.HistoryPeriod
import com.jpyunism.ollamacloudusage.HistoryState
import com.jpyunism.ollamacloudusage.R
import com.jpyunism.ollamacloudusage.UsageSnapshot
import com.jpyunism.ollamacloudusage.formatPercent
import com.jpyunism.ollamacloudusage.nearestSnapshot
import com.jpyunism.ollamacloudusage.resetMarkers
import com.jpyunism.ollamacloudusage.summarize
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CHART_HEIGHT = 220.dp
private val Y_LABELS = listOf(0.0, 50.0, 100.0)

@Composable
fun StatsTab(history: HistoryState) {
    val snapshots = history.snapshots
    if (snapshots.isEmpty()) {
        EmptyStats()
        return
    }

    var period by remember { mutableStateOf(HistoryPeriod.WEEK) }
    val weeklyReset = history.weeklyResetAt?.toEpochMilli()
    val selector: (UsageSnapshot) -> Double = { s ->
        if (period == HistoryPeriod.WEEK) s.weeklyPercent else s.sessionPercent
    }

    val now = System.currentTimeMillis()
    val summary = summarize(snapshots, period, weeklyReset, now, selector)
    val markers = resetMarkers(snapshots, period, weeklyReset)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.tab_stats),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    PeriodToggle(period, onSelect = { period = it })
                }
                Spacer(Modifier.height(12.dp))
                UsageChart(
                    snapshots = snapshots,
                    selector = selector,
                    markers = markers,
                )
            }
        }

        summary.lastClosed?.let { closed ->
            val label = if (period == HistoryPeriod.WEEK) {
                stringResource(R.string.stats_week_of, formatDate(closed.start))
            } else {
                stringResource(R.string.stats_session_of, formatDate(closed.start))
            }
            SummaryRow(
                text = stringResource(R.string.stats_last_closed, label, formatPercent(closed.peakPercent)),
            )
        }
        summary.current?.let { current ->
            val periodLabel = stringResource(
                if (period == HistoryPeriod.WEEK) R.string.stats_period_week else R.string.stats_period_session,
            ).lowercase()
            SummaryRow(
                text = stringResource(R.string.stats_current, formatPercent(current.peakPercent), periodLabel),
            )
        }
    }
}

@Composable
private fun PeriodToggle(selected: HistoryPeriod, onSelect: (HistoryPeriod) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        SegmentedButton(
            selected = selected == HistoryPeriod.WEEK,
            onClick = { onSelect(HistoryPeriod.WEEK) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
            Text(stringResource(R.string.stats_period_week))
        }
        SegmentedButton(
            selected = selected == HistoryPeriod.SESSION,
            onClick = { onSelect(HistoryPeriod.SESSION) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
            Text(stringResource(R.string.stats_period_session))
        }
    }
}

@Composable
private fun SummaryRow(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.BarChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyStats() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.BarChart,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.stats_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.stats_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Gráfico de línea + área con marcadores de reset y tooltip al tocar. */
@Composable
private fun UsageChart(
    snapshots: List<UsageSnapshot>,
    selector: (UsageSnapshot) -> Double,
    markers: List<Long>,
) {
    var tooltip by remember { mutableStateOf<UsageSnapshot?>(null) }
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val markerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
    val bubbleColor = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current

    // Texto del tooltip precomputado (stringResource no es válido en DrawScope).
    val tooltipText = tooltip?.let { s ->
        stringResource(
            R.string.stats_tooltip,
            formatDateTime(s.timestampMillis),
            formatPercent(selector(s)),
        )
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .pointerInput(snapshots, selector) {
                detectTapGestures { offset ->
                    tooltip = nearestSnapshot(snapshots, xToTimestamp(offset.x, size.width.toFloat(), snapshots, density))
                }
            },
    ) {
        val chartLeft = with(density) { 28.dp.toPx() }
        val chartRight = size.width - with(density) { 8.dp.toPx() }
        val chartTop = with(density) { 8.dp.toPx() }
        val chartBottom = size.height - with(density) { 20.dp.toPx() }
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        fun xFor(ts: Long): Float {
            val first = snapshots.first().timestampMillis
            val last = snapshots.last().timestampMillis
            val span = (last - first).coerceAtLeast(1L)
            return chartLeft + ((ts - first).toFloat() / span.toFloat()) * chartWidth
        }

        fun yFor(pct: Double): Float =
            chartBottom - (pct.toFloat().coerceIn(0f, 100f) / 100f) * chartHeight

        // Grid horizontal + labels Y (0 / 50 / 100)
        Y_LABELS.forEach { pct ->
            val y = yFor(pct)
            drawLine(gridColor, Offset(chartLeft, y), Offset(chartRight, y), strokeWidth = 1f)
            drawContext.canvas.nativeCanvas.drawText(
                formatPercent(pct),
                chartLeft - with(density) { 4.dp.toPx() },
                y + with(density) { 4.dp.toPx() },
                android.graphics.Paint().apply {
                    color = labelColor.toArgb()
                    textSize = with(density) { 10.sp.toPx() }
                },
            )
        }

        // Marcadores de reset (líneas punteadas verticales)
        markers.forEach { ts ->
            val x = xFor(ts)
            drawLine(
                markerColor,
                Offset(x, chartTop),
                Offset(x, chartBottom),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
        }

        // Área + línea de la serie
        if (snapshots.size >= 2) {
            val linePath = Path()
            val areaPath = Path()
            snapshots.forEachIndexed { i, s ->
                val x = xFor(s.timestampMillis)
                val y = yFor(selector(s))
                if (i == 0) {
                    linePath.moveTo(x, y)
                    areaPath.moveTo(x, chartBottom)
                    areaPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    areaPath.lineTo(x, y)
                }
            }
            areaPath.lineTo(xFor(snapshots.last().timestampMillis), chartBottom)
            areaPath.close()

            drawPath(
                areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent),
                    startY = chartTop,
                    endY = chartBottom,
                ),
            )
            drawPath(linePath, color = lineColor, style = Stroke(width = with(density) { 2.dp.toPx() }, cap = StrokeCap.Round))
        }

        // Puntos de los snapshots
        snapshots.forEach { s ->
            drawCircle(
                color = lineColor,
                radius = with(density) { 2.5.dp.toPx() },
                center = Offset(xFor(s.timestampMillis), yFor(selector(s))),
            )
        }

        // Tooltip: marcador + burbuja con fecha y %
        if (tooltip != null && tooltipText != null) {
            val s = tooltip!!
            val x = xFor(s.timestampMillis)
            val y = yFor(selector(s))
            drawCircle(
                color = lineColor,
                radius = with(density) { 4.dp.toPx() },
                center = Offset(x, y),
            )
            val paint = android.graphics.Paint().apply {
                color = labelColor.toArgb()
                textSize = with(density) { 11.sp.toPx() }
            }
            val textWidth = paint.measureText(tooltipText)
            val bubbleW = textWidth + with(density) { 16.dp.toPx() }
            val bubbleH = with(density) { 22.dp.toPx() }
            val bubbleX = (x - bubbleW / 2).coerceIn(chartLeft, chartRight - bubbleW)
            val bubbleY = (y - bubbleH - with(density) { 10.dp.toPx() }).coerceAtLeast(chartTop)
            drawRoundRect(
                color = bubbleColor,
                topLeft = Offset(bubbleX, bubbleY),
                size = Size(bubbleW, bubbleH),
                cornerRadius = CornerRadius(with(density) { 8.dp.toPx() }),
            )
            drawContext.canvas.nativeCanvas.drawText(
                tooltipText,
                bubbleX + with(density) { 8.dp.toPx() },
                bubbleY + bubbleH / 2 + with(density) { 4.dp.toPx() },
                paint,
            )
        }
    }
}

/** Convierte una X del canvas a un timestamp (para el tooltip). */
private fun xToTimestamp(
    x: Float,
    width: Float,
    snapshots: List<UsageSnapshot>,
    density: androidx.compose.ui.unit.Density,
): Long {
    if (snapshots.isEmpty()) return 0L
    val chartLeft = with(density) { 28.dp.toPx() }
    val chartRight = width - with(density) { 8.dp.toPx() }
    val chartWidth = (chartRight - chartLeft).coerceAtLeast(1f)
    val first = snapshots.first().timestampMillis
    val last = snapshots.last().timestampMillis
    val span = (last - first).coerceAtLeast(1L)
    val fraction = ((x - chartLeft) / chartWidth).coerceIn(0f, 1f)
    return first + (fraction * span).toLong()
}

private fun formatDate(ts: Long): String =
    Instant.ofEpochMilli(ts)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

private fun formatDateTime(ts: Long): String =
    Instant.ofEpochMilli(ts)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault()))
