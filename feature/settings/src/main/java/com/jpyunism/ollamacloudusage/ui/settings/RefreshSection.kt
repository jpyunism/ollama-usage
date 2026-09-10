package com.jpyunism.ollamacloudusage.ui.settings

import com.jpyunism.ollamacloudusage.core.ui.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jpyunism.ollamacloudusage.AlertSettings
import com.jpyunism.ollamacloudusage.UsageScheduler

/** Frecuencia de refresco del consumo en segundo plano. */
@Composable
fun RefreshSection(
    settings: AlertSettings,
    onSave: (AlertSettings) -> Unit,
) {
    SettingsSection(
        titleRes = R.string.refresh_frequency,
        subtitleRes = R.string.refresh_frequency_description,
        icon = Icons.Outlined.Refresh,
    ) {
        Slider(
            value = settings.refreshIntervalMinutes.toFloat(),
            onValueChange = { onSave(settings.copy(refreshIntervalMinutes = it.toInt())) },
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
            Text(stringResource(R.string.min_1), style = MaterialTheme.typography.labelSmall)
            Text(
                formatInterval(settings.refreshIntervalMinutes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(R.string.hours_12), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun formatInterval(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.interval_minutes, minutes)
    minutes % 60 == 0 -> stringResource(R.string.interval_hours, minutes / 60)
    else -> stringResource(R.string.interval_hours_minutes, minutes / 60, minutes % 60)
}
