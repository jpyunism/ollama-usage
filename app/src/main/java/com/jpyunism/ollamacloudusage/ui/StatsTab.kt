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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpyunism.ollamacloudusage.HistoryPeriod
import com.jpyunism.ollamacloudusage.HistoryState
import com.jpyunism.ollamacloudusage.PeriodBar
import com.jpyunism.ollamacloudusage.CurrentPeriod
import com.jpyunism.ollamacloudusage.R
import com.jpyunism.ollamacloudusage.UsageSnapshot
import com.jpyunism.ollamacloudusage.currentPeriod
import com.jpyunism.ollamacloudusage.fallbackResetAnchor
import com.jpyunism.ollamacloudusage.formatPercent
import com.jpyunism.ollamacloudusage.nearestSnapshot
import com.jpyunism.ollamacloudusage.periodBars
import com.jpyunism.ollamacloudusage.resetMarkers
import com.jpyunism.ollamacloudusage.summarize
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CHART_HEIGHT = 220.dp
private val Y_LABELS = listOf(0.0, 50.0, 100.0)

@Composable
fun StatsTab(history: HistoryState, isRefreshing: Boolean = false, onRefresh: () -> Unit = {}) {
    val snapshots = history.snapshots
    if (snapshots.isEmpty()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            EmptyStats()
        }
        return
    }

    var period by remember { mutableStateOf(HistoryPeriod.WEEK) }
    val weeklyReset = history.weeklyResetAt?.toEpochMilli()
    val sessionReset = history.sessionResetAt?.toEpochMilli()
    val resetAnchor = if (period == HistoryPeriod.WEEK) weeklyReset else sessionReset
    val selector: (UsageSnapshot) -> Double = { s ->
        if (period == HistoryPeriod.WEEK) s.weeklyPercent else s.sessionPercent
    }

    val now = System.currentTimeMillis()
    // Si la fuente no entrega el reset (p.ej. método API key), se sintetiza
    // el ancla: semana = próximo domingo 21:00 CLT, sesión = ventana móvil 24 h.
    val anchor = resetAnchor ?: fallbackResetAnchor(period, now)
    val summary = summarize(snapshots, period, anchor, now, selector)
    val markers = resetMarkers(snapshots, period, resetAnchor)
    val current = currentPeriod(snapshots, period, anchor, now, selector)

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
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
                    current = current,
                )
                current?.let { cp ->
                    val idealLabel = stringResource(R.string.stats_ideal)
                    val projLabel = cp.projection?.let {
                        stringResource(R.string.stats_projection, formatPercent(it.toPercent))
                    }
                    ChartLegend(
                        showIdeal = cp.snapshotCount > 0,
                        idealLabel = idealLabel,
                        projectionLabel = projLabel,
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.stats_bars_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(12.dp))
                val bars = periodBars(snapshots, period, weeklyReset, now, selector)
                PeriodBarsChart(bars = bars)
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
        current?.projection?.let { proj ->
            SummaryRow(
                text = stringResource(R.string.stats_projection_summary, formatPercent(proj.toPercent)),
            )
        }
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
            .verticalScroll(rememberScrollState())
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
    current: CurrentPeriod?,
) {
    var tooltip by remember { mutableStateOf<UsageSnapshot?>(null) }
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val markerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
    val bubbleColor = MaterialTheme.colorScheme.surface
    val idealColor = MaterialTheme.colorScheme.tertiary
    val projectionColor = MaterialTheme.colorScheme.secondary
    val dangerColor = MaterialTheme.colorScheme.error
    val density = LocalDensity.current

    // Fin del eje X: último snapshot, o el fin de la proyección si existe.
    val xEnd = current?.projection?.toTimestamp?.coerceAtLeast(snapshots.last().timestampMillis)
        ?: snapshots.last().timestampMillis

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
            .pointerInput(snapshots, selector, xEnd) {
                detectTapGestures { offset ->
                    tooltip = nearestSnapshot(snapshots, xToTimestamp(offset.x, size.width.toFloat(), snapshots, density, xEndMillis = xEnd))
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
            val span = (xEnd - first).coerceAtLeast(1L)
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

        // Línea ideal: rampa lineal 0% → 100% del período actual (si hay datos).
        if (current != null && current.snapshotCount > 0) {
            val idealStartX = xFor(current.start)
            val idealEndX = xFor(current.end)
            val visibleStartX = idealStartX.coerceIn(chartLeft, chartRight)
            val visibleEndX = idealEndX.coerceIn(chartLeft, chartRight)
            if (visibleEndX > visibleStartX) {
                // Recorta la rampa al rango visible del eje X.
                val fracStart = (visibleStartX - idealStartX) / (idealEndX - idealStartX).coerceAtLeast(1f)
                val fracEnd = (visibleEndX - idealStartX) / (idealEndX - idealStartX).coerceAtLeast(1f)
                val yStart = yFor(fracStart * 100.0)
                val yEnd = yFor(fracEnd * 100.0)
                drawLine(
                    idealColor,
                    Offset(visibleStartX, yStart),
                    Offset(visibleEndX, yEnd),
                    strokeWidth = with(density) { 1.5.dp.toPx() },
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(with(density) { 6.dp.toPx() }, with(density) { 6.dp.toPx() })),
                )
            }
        }

        // Línea de proyección: último snapshot → próximo reset.
        current?.projection?.let { proj ->
            val fromX = xFor(proj.fromTimestamp)
            val toX = xFor(proj.toTimestamp)
            val fromY = yFor(proj.fromPercent)
            val toY = yFor(proj.toPercent)
            val projColor2 = if (proj.toPercent > 100) dangerColor else projectionColor
            drawLine(
                projColor2,
                Offset(fromX, fromY),
                Offset(toX, toY),
                strokeWidth = with(density) { 2.dp.toPx() },
            )
            // Punto en el extremo de la proyección.
            drawCircle(
                color = projColor2,
                radius = with(density) { 3.dp.toPx() },
                center = Offset(toX, toY),
            )
            // Etiqueta del % proyectado (reubicada si sale del borde derecho).
            val projText = formatPercent(proj.toPercent)
            val paint = android.graphics.Paint().apply {
                color = projColor2.toArgb()
                textSize = with(density) { 10.sp.toPx() }
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val textX = (toX + with(density) { 14.dp.toPx() }).coerceAtMost(chartRight - with(density) { 2.dp.toPx() })
            val textY = (toY - with(density) { 6.dp.toPx() }).coerceAtLeast(chartTop + with(density) { 10.dp.toPx() })
            drawContext.canvas.nativeCanvas.drawText(projText, textX, textY, paint)
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
    xEndMillis: Long,
): Long {
    if (snapshots.isEmpty()) return 0L
    val chartLeft = with(density) { 28.dp.toPx() }
    val chartRight = width - with(density) { 8.dp.toPx() }
    val chartWidth = (chartRight - chartLeft).coerceAtLeast(1f)
    val first = snapshots.first().timestampMillis
    val span = (xEndMillis - first).coerceAtLeast(1L)
    val fraction = ((x - chartLeft) / chartWidth).coerceIn(0f, 1f)
    return first + (fraction * span).toLong()
}

/** Gráfico de barras: una barra por período con el % consumido antes del reset. */
@Composable
private fun PeriodBarsChart(bars: List<PeriodBar>) {
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val inProgressLabel = stringResource(R.string.stats_bars_in_progress)
    val desc = stringResource(R.string.stats_bars_desc)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .semantics { contentDescription = desc },
    ) {
        val chartLeft = with(density) { 28.dp.toPx() }
        val chartRight = size.width - with(density) { 8.dp.toPx() }
        val chartTop = with(density) { 8.dp.toPx() }
        val chartBottom = size.height - with(density) { 20.dp.toPx() }
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

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

        if (bars.isEmpty()) return@Canvas

        val slot = chartWidth / bars.size
        val barWidth = (slot * 0.6f).coerceAtLeast(with(density) { 4.dp.toPx() })
        val showDateLabels = bars.size <= 8
        val showPercentLabels = bars.size <= 14
        val corner = with(density) { 4.dp.toPx() }

        bars.forEachIndexed { i, bar ->
            val x = chartLeft + slot * i + (slot - barWidth) / 2f
            val y = yFor(bar.peakPercent)
            val barFill = if (bar.inProgress) barColor.copy(alpha = 0.45f) else barColor

            // Barra con esquinas superiores redondeadas
            val top = y.coerceAtLeast(chartTop)
            val rect = Rect(
                left = x,
                top = top,
                right = x + barWidth,
                bottom = chartBottom,
            )
            drawRoundRect(
                color = barFill,
                topLeft = rect.topLeft,
                size = Size(rect.width, rect.height),
                cornerRadius = CornerRadius(corner, corner),
            )

            // % encima de la barra
            if (showPercentLabels) {
                val pctText = formatPercent(bar.peakPercent)
                val paint = android.graphics.Paint().apply {
                    color = labelColor.toArgb()
                    textSize = with(density) { 10.sp.toPx() }
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawContext.canvas.nativeCanvas.drawText(
                    pctText,
                    x + barWidth / 2f,
                    top - with(density) { 4.dp.toPx() },
                    paint,
                )
            }

            // Label de fecha (o "En curso") bajo la barra
            val label = if (bar.inProgress) inProgressLabel else formatDate(bar.start)
            if (showDateLabels || i == 0 || i == bars.lastIndex) {
                val paint = android.graphics.Paint().apply {
                    color = labelColor.toArgb()
                    textSize = with(density) { 10.sp.toPx() }
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    x + barWidth / 2f,
                    chartBottom + with(density) { 14.dp.toPx() },
                    paint,
                )
            }
        }
    }
}

private fun formatDate(ts: Long): String =
    Instant.ofEpochMilli(ts)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

private fun formatDateTime(ts: Long): String =
    Instant.ofEpochMilli(ts)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault()))
/** Leyenda bajo el gráfico: swatch + etiqueta para Ideal y Proyección. */
@Composable
private fun ChartLegend(
    showIdeal: Boolean,
    idealLabel: String,
    projectionLabel: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIdeal) {
            LegendItem(
                color = MaterialTheme.colorScheme.tertiary,
                label = idealLabel,
                dashed = true,
            )
        }
        if (projectionLabel != null) {
            LegendItem(
                color = MaterialTheme.colorScheme.secondary,
                label = projectionLabel,
                dashed = false,
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, dashed: Boolean) {
    val density = LocalDensity.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Swatch dibujado como línea (continua o discontinua) igual que en el gráfico.
        Canvas(Modifier.size(width = 20.dp, height = 6.dp)) {
            val y = size.height / 2f
            val swatchWidth = with(density) { 2.dp.toPx() }
            val dash = with(density) { 5.dp.toPx() }
            val gap = with(density) { 4.dp.toPx() }
            drawLine(
                color = color,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = swatchWidth,
                pathEffect = if (dashed) {
                    PathEffect.dashPathEffect(floatArrayOf(dash, gap))
                } else {
                    null
                },
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

