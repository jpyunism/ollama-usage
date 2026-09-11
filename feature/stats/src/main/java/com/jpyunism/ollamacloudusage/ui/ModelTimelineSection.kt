package com.jpyunism.ollamacloudusage.ui

import com.jpyunism.ollamacloudusage.core.ui.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpyunism.ollamacloudusage.MIN_MODEL_POINTS
import com.jpyunism.ollamacloudusage.ModelPoint
import com.jpyunism.ollamacloudusage.UsageSnapshot
import com.jpyunism.ollamacloudusage.formatPercent
import com.jpyunism.ollamacloudusage.hasModelHistory
import com.jpyunism.ollamacloudusage.hasOtherModels
import com.jpyunism.ollamacloudusage.modelSeries
import com.jpyunism.ollamacloudusage.modelSeriesVisible
import com.jpyunism.ollamacloudusage.othersSeries
import com.jpyunism.ollamacloudusage.topModelNames
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MODEL_CHART_HEIGHT = 200.dp
private val MODEL_Y_LABELS = listOf(0.0, 50.0, 100.0)

/** Sentinel del chip "Otros": no es un modelo real del snapshot. */
private const val OTHERS_KEY = "__others__"

/** Maximo de lineas superpuestas (issue #92: 2-3 modelos). */
private const val MAX_OVERLAY = 3

/** Una linea del grafico: etiqueta, color (null = neutro, para "Otros") y puntos. */
private data class ModelLine(
    val label: String,
    val color: Color?,
    val points: List<ModelPoint>,
)

/**
 * Seccion "Evolucion por modelo" (issue #92): selector de chips con los top N
 * modelos (+ "Otros"), toggle para ver uno solo o superponer hasta 3, y
 * grafico de linea con el % de cada modelo a lo largo del historico.
 *
 * Se oculta por completo si el historico no tiene al menos [MIN_MODEL_POINTS]
 * snapshots con desglose por modelo (historicos viejos o fuente sin modelos).
 */
@Composable
fun ModelTimelineSection(snapshots: List<UsageSnapshot>) {
    if (!hasModelHistory(snapshots)) return

    val topNames = remember(snapshots) { topModelNames(snapshots) }
    val othersAvailable = remember(snapshots) { hasOtherModels(snapshots) }
    if (topNames.isEmpty()) return

    var overlay by rememberSaveable { mutableStateOf(false) }
    // Seleccion serializada como nombres separados por salto de linea: es
    // Bundle-serializable (rememberSaveable) y sobrevive la muerte del proceso.
    var selectedRaw by rememberSaveable { mutableStateOf("") }
    val selected = remember(selectedRaw, topNames) {
        selectedRaw.split("\n").filter { it.isNotEmpty() }.ifEmpty { topNames.take(1) }
    }

    val othersLabel = stringResource(R.string.others)

    fun toggle(key: String) {
        val current = selected
        val next = when {
            overlay -> {
                if (key in current) {
                    // No dejar la seleccion vacia en modo superposicion.
                    if (current.size > 1) current - key else current
                } else if (current.size < MAX_OVERLAY) {
                    current + key
                } else {
                    current
                }
            }

            key in current -> current
            else -> listOf(key)
        }
        selectedRaw = next.joinToString("\n")
    }

    val lines = remember(selected, snapshots, othersLabel) {
        selected.mapNotNull { key ->
            val points = if (key == OTHERS_KEY) {
                othersSeries(snapshots, exclude = topNames.toSet())
            } else {
                modelSeries(snapshots, key)
            }
            val label = if (key == OTHERS_KEY) othersLabel else key
            // "Otros" no es un modelo: color neutro del tema (null).
            val color = if (key == OTHERS_KEY) null else modelColor(key)
            ModelLine(label, color, points)
        }
    }
    val visibleLines = lines.filter { modelSeriesVisible(it.points) }
    val othersColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.stats_models_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                topNames.forEach { name ->
                    val isSelected = name in selected
                    FilterChip(
                        selected = isSelected,
                        enabled = !overlay || isSelected || selected.size < MAX_OVERLAY,
                        onClick = { toggle(name) },
                        label = { Text(name) },
                    )
                }
                if (othersAvailable) {
                    val isSelected = OTHERS_KEY in selected
                    FilterChip(
                        selected = isSelected,
                        enabled = !overlay || isSelected || selected.size < MAX_OVERLAY,
                        onClick = { toggle(OTHERS_KEY) },
                        label = { Text(othersLabel) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            FilterChip(
                selected = overlay,
                onClick = {
                    overlay = !overlay
                    // Al volver a modo simple se conserva solo la primera linea.
                    if (!overlay && selected.size > 1) {
                        selectedRaw = selected.take(1).joinToString("\n")
                    }
                },
                label = { Text(stringResource(R.string.stats_models_overlay)) },
            )
            Spacer(Modifier.height(8.dp))
            if (visibleLines.isEmpty()) {
                Text(
                    stringResource(R.string.stats_models_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                ModelTimelineChart(
                    lines = visibleLines,
                    snapshots = snapshots,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    visibleLines.forEach { line ->
                        ModelLegendItem(color = line.color ?: othersColor, label = line.label)
                    }
                }
            }
        }
    }
}

/** Grafico de lineas: eje X = eje temporal del grafico semanal; Y = 0..100%. */
@Composable
private fun ModelTimelineChart(
    lines: List<ModelLine>,
    snapshots: List<UsageSnapshot>,
    othersColor: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val desc = stringResource(R.string.stats_models_desc)

    val first = snapshots.first().timestampMillis
    val last = snapshots.last().timestampMillis

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(MODEL_CHART_HEIGHT)
            .semantics { contentDescription = desc },
    ) {
        val chartLeft = with(density) { 28.dp.toPx() }
        val chartRight = size.width - with(density) { 8.dp.toPx() }
        val chartTop = with(density) { 8.dp.toPx() }
        val chartBottom = size.height - with(density) { 20.dp.toPx() }
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        fun xFor(ts: Long): Float {
            val span = (last - first).coerceAtLeast(1L)
            val frac = ((ts - first).toFloat() / span.toFloat()).coerceIn(0f, 1f)
            return chartLeft + frac * chartWidth
        }

        fun yFor(pct: Double): Float =
            chartBottom - (pct.toFloat().coerceIn(0f, 100f) / 100f) * chartHeight

        // Grid horizontal + labels Y (0 / 50 / 100)
        MODEL_Y_LABELS.forEach { pct ->
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

        // Labels X: primera y ultima fecha del historico.
        val datePaint = android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = with(density) { 10.sp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
        }
        drawContext.canvas.nativeCanvas.drawText(
            formatModelDate(first),
            chartLeft,
            chartBottom + with(density) { 14.dp.toPx() },
            datePaint,
        )
        drawContext.canvas.nativeCanvas.drawText(
            formatModelDate(last),
            chartRight,
            chartBottom + with(density) { 14.dp.toPx() },
            datePaint,
        )

        // Una linea + puntos por modelo seleccionado.
        lines.forEach { line ->
            val color = line.color ?: othersColor
            val path = Path()
            line.points.forEachIndexed { i, p ->
                val o = Offset(xFor(p.timestampMillis), yFor(p.percent))
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(
                path,
                color = color,
                style = Stroke(width = with(density) { 2.dp.toPx() }, cap = StrokeCap.Round),
            )
            line.points.forEach { p ->
                drawCircle(
                    color = color,
                    radius = with(density) { 2.5.dp.toPx() },
                    center = Offset(xFor(p.timestampMillis), yFor(p.percent)),
                )
            }
        }
    }
}

@Composable
private fun ModelLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 20.dp, height = 6.dp)) {
            val y = size.height / 2f
            drawLine(
                color = color,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 2.dp.toPx(),
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

private fun formatModelDate(ts: Long): String =
    Instant.ofEpochMilli(ts)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
