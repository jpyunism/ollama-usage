package com.jpyunism.ollamacloudusage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jpyunism.ollamacloudusage.ProjectionEngine
import com.jpyunism.ollamacloudusage.R
import com.jpyunism.ollamacloudusage.formatPercent
import java.time.Duration
import java.time.Instant

/**
 * Tarjeta de proyeccion de agotamiento (issue #54). Muestra, para un
 * periodo (semana o sesion), a que ritmo se consume y cuando se agotaria la
 * cuota. Solo informativa: no dispara alertas.
 *
 * [title] es el titulo del periodo ("Cuota semanal" / "Sesion").
 * [result] es la proyeccion calculada por [ProjectionEngine].
 * [resetAt] (opcional) se usa para la linea de sesion "se reinicia en Y,
 * llegaras al Z%".
 */
@Composable
fun ProjectionCard(
    title: String,
    result: ProjectionEngine.Result,
    modifier: Modifier = Modifier,
    resetAt: Instant? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (result.trend) {
                        ProjectionEngine.Trend.RISING -> Icons.AutoMirrored.Filled.TrendingUp
                        ProjectionEngine.Trend.FALLING -> Icons.AutoMirrored.Filled.TrendingDown
                        ProjectionEngine.Trend.STABLE -> Icons.Filled.Remove
                    },
                    contentDescription = stringResource(
                        when (result.trend) {
                            ProjectionEngine.Trend.RISING -> R.string.projection_trend_rising
                            ProjectionEngine.Trend.FALLING -> R.string.projection_trend_falling
                            ProjectionEngine.Trend.STABLE -> R.string.projection_trend_stable
                        },
                    ),
                    tint = when (result.trend) {
                        ProjectionEngine.Trend.RISING -> MaterialTheme.colorScheme.error
                        ProjectionEngine.Trend.FALLING -> MaterialTheme.colorScheme.primary
                        ProjectionEngine.Trend.STABLE -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.projection_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                projectionText(result),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (resetAt != null && result.percentAtReset != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(
                        R.string.projection_session_reset,
                        formatResetDuration(resetAt),
                        formatPercent(result.percentAtReset),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Texto principal de la tarjeta segun la tendencia. */
@Composable
private fun projectionText(result: ProjectionEngine.Result): String = when (result.trend) {
    ProjectionEngine.Trend.RISING -> {
        val target = result.hoursToTarget
        if (target != null) {
            stringResource(R.string.projection_rising, formatHours(target))
        } else {
            stringResource(R.string.projection_rising, "?")
        }
    }
    ProjectionEngine.Trend.STABLE -> stringResource(R.string.projection_stable)
    ProjectionEngine.Trend.FALLING -> stringResource(R.string.projection_falling)
}

/** Formatea horas a "X dias" o "X h" segun la magnitud. */
@Composable
private fun formatHours(hours: Double): String {
    val rounded = hours.toInt()
    return if (rounded >= 24) {
        stringResource(R.string.projection_days, (rounded / 24).toString())
    } else {
        stringResource(R.string.projection_hours, rounded.toString())
    }
}

/** Formatea el tiempo restante hasta el reset (ej. "5 h 30 min"). */
private fun formatResetDuration(resetAt: Instant): String {
    val diff = Duration.between(Instant.now(), resetAt)
    return when {
        diff.isNegative || diff.isZero -> "<1 min"
        diff.toMinutes() < 1 -> "<1 min"
        diff.toHours() < 1 -> "${diff.toMinutes()} min"
        diff.toHours() < 24 -> "${diff.toHours()} h ${diff.toMinutes() % 60} min"
        else -> "${diff.toDays()} d ${diff.toHours() % 24} h"
    }
}
