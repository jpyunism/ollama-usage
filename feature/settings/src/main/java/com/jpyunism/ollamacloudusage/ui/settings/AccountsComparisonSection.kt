package com.jpyunism.ollamacloudusage.ui.settings

import com.jpyunism.ollamacloudusage.core.ui.R
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpyunism.ollamacloudusage.AccountUsage
import com.jpyunism.ollamacloudusage.AccountUsageState
import com.jpyunism.ollamacloudusage.TrafficLight
import com.jpyunism.ollamacloudusage.UsageError
import com.jpyunism.ollamacloudusage.UsageViewModel
import com.jpyunism.ollamacloudusage.formatPercent
import com.jpyunism.ollamacloudusage.ui.TrafficLightColors

/**
 * Comparativa entre cuentas (issue #93): una tarjeta por cuenta con label,
 * % semanal, % de sesion, indicador de activa y barra de progreso con color de
 * semaforo. El consumo se trae por cuenta (fetch aislado): una API key invalida
 * muestra su error sin ocultar las demas. Se oculta con 0-1 cuentas.
 */
@Composable
fun AccountsComparisonSection(vm: UsageViewModel) {
    val usages by vm.accountUsages.collectAsStateWithLifecycle()
    // Con 0-1 cuentas la comparativa no aporta: se oculta (issue #93).
    if (usages.isEmpty()) return
    val settings by vm.settings.collectAsStateWithLifecycle()

    SettingsSection(
        titleRes = R.string.accounts_comparison_title,
        subtitleRes = R.string.accounts_comparison_description,
        icon = Icons.Outlined.Insights,
    ) {
        usages.forEach { usage ->
            AccountComparisonCard(
                usage = usage,
                weeklyAlert = settings.weeklyAlert,
                weeklyCritical = settings.weeklyCritical,
                sessionAlert = settings.sessionAlert,
                sessionCritical = settings.sessionCritical,
                onRetry = { vm.refreshAccountUsages() },
            )
        }
    }
}

@Composable
private fun AccountComparisonCard(
    usage: AccountUsage,
    weeklyAlert: Int,
    weeklyCritical: Int,
    sessionAlert: Int,
    sessionCritical: Int,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    usage.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (usage.isActive) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.accounts_comparison_active),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.accounts_comparison_active),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            when (val state = usage.state) {
                AccountUsageState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is AccountUsageState.Success -> {
                    AccountMeter(
                        label = stringResource(R.string.accounts_comparison_week),
                        percent = state.weeklyPercent,
                        alert = weeklyAlert,
                        critical = weeklyCritical,
                    )
                    Spacer(Modifier.height(8.dp))
                    AccountMeter(
                        label = stringResource(R.string.accounts_comparison_session),
                        percent = state.sessionPercent,
                        alert = sessionAlert,
                        critical = sessionCritical,
                    )
                }

                is AccountUsageState.Failure -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.accounts_comparison_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            errorText(state.error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = onRetry) {
                        Text(stringResource(R.string.accounts_comparison_retry))
                    }
                }
            }
        }
    }
}

/** Barra de progreso de un periodo con su % y color de semaforo. */
@Composable
private fun AccountMeter(label: String, percent: Double, alert: Int, critical: Int) {
    val color = TrafficLightColors.getValue(TrafficLight.paceColor(percent, alert, critical))
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.accounts_comparison_percent, formatPercent(percent)),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = color,
        )
    }
}

/** Texto localizado del error de una cuenta. */
@Composable
private fun errorText(error: UsageError): String = when (error) {
    UsageError.NoAuth -> stringResource(R.string.api_key_invalid)
    UsageError.InvalidApiKey -> stringResource(R.string.api_key_invalid)
    UsageError.CookieExpired -> stringResource(R.string.cookie_expired)
    is UsageError.Network -> stringResource(R.string.network_error, error.detail)
}
