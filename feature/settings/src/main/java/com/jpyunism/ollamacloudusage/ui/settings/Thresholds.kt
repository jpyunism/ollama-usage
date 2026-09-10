package com.jpyunism.ollamacloudusage.ui.settings

import com.jpyunism.ollamacloudusage.core.ui.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jpyunism.ollamacloudusage.AlertSettings

/** Tarjeta de umbrales alerta/critico para un periodo (semanal o sesion). */
@Composable
internal fun ThresholdCard(
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
