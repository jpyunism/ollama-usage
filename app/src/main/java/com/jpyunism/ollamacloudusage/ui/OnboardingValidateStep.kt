package com.jpyunism.ollamacloudusage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jpyunism.ollamacloudusage.OnboardingViewModel
import com.jpyunism.ollamacloudusage.R

/**
 * Paso 3/3 del onboarding (issue #61, spec 01).
 * Pantalla de confirmacion / validacion final:
 *  - Si eligio API key: muestra el estado de la validacion + boton "Validar"
 *    y CTA "Continuar" cuando es Success.
 *  - Si eligio WebView: la captura de cookie ocurre dentro del flujo real de
 *    auth (no del onboarding); aqui solo se confirma y se envia al Main.
 */
@Composable
fun OnboardingValidateStep(
    method: OnboardingViewModel.Method,
    apiKeyInput: String,
    validation: OnboardingViewModel.ValidationState,
    onValidate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboarding_validate_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        val subtitle = when (method) {
            OnboardingViewModel.Method.ApiKey ->
                stringResource(R.string.onboarding_validate_subtitle_api_key)
            OnboardingViewModel.Method.WebView ->
                stringResource(R.string.onboarding_validate_subtitle_webview)
            OnboardingViewModel.Method.None ->
                stringResource(R.string.onboarding_validate_subtitle_webview)
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (method) {
            OnboardingViewModel.Method.ApiKey -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(R.string.onboarding_method_apikey_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            // Mostramos solo los primeros 4 y ultimos 4 chars
                            // para confirmar visualmente sin exponer el secreto.
                            maskedKey(apiKeyInput),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        ValidationFeedback(validation = validation, onRetry = onValidate)
                        if (validation !is OnboardingViewModel.ValidationState.Success) {
                            Button(
                                onClick = onValidate,
                                enabled = apiKeyInput.isNotBlank() &&
                                    validation !is OnboardingViewModel.ValidationState.InProgress,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.onboarding_validate_button))
                            }
                        }
                    }
                }
            }
            OnboardingViewModel.Method.WebView -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_validate_cookie_success),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            OnboardingViewModel.Method.None -> {
                // Sin metodo elegido: volver atras.
                Text(
                    text = stringResource(R.string.onboarding_method_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Muestra la key enmascarada para confirmacion visual sin exponer el secreto. */
private fun maskedKey(key: String): String {
    val trimmed = key.trim()
    if (trimmed.length <= 8) return trimmed
    return trimmed.take(4) + "..." + trimmed.takeLast(4)
}
