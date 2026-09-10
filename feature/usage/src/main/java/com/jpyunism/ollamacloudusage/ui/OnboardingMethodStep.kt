package com.jpyunism.ollamacloudusage.ui

import com.jpyunism.ollamacloudusage.core.ui.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jpyunism.ollamacloudusage.OnboardingViewModel

/**
 * Paso 2/3 del onboarding (issue #61, spec 01).
 * Pantalla de eleccion del metodo de autenticacion:
 *  - WebView (captura automatica de cookie via CookieWebView)
 *  - API key (pegar + validar contra ollama.com)
 */
@Composable
fun OnboardingMethodStep(
    method: OnboardingViewModel.Method,
    apiKeyInput: String,
    validation: OnboardingViewModel.ValidationState,
    onPickWebView: () -> Unit,
    onPickApiKey: () -> Unit,
    onApiKeyChange: (String) -> Unit,
    onValidate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_method_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.onboarding_method_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        MethodCard(
            selected = method == OnboardingViewModel.Method.WebView,
            icon = Icons.Outlined.Cookie,
            title = stringResource(R.string.onboarding_method_webview),
            description = stringResource(R.string.onboarding_method_webview_desc),
            onClick = onPickWebView,
        )

        MethodCard(
            selected = method == OnboardingViewModel.Method.ApiKey,
            icon = Icons.Outlined.Key,
            title = stringResource(R.string.onboarding_method_apikey),
            description = stringResource(R.string.onboarding_method_apikey_desc),
            onClick = onPickApiKey,
        )

        // Si eligio API key, mostrar el input para que pueda tipear.
        if (method == OnboardingViewModel.Method.ApiKey) {
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = onApiKeyChange,
                label = { Text(stringResource(R.string.onboarding_method_apikey_label)) },
                placeholder = { Text(stringResource(R.string.onboarding_method_apikey_placeholder)) },
                leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Estado de validacion inline para que el usuario vea feedback
            // inmediato al pulsar "Validar" en este paso.
            ValidationFeedback(
                validation = validation,
                onRetry = onValidate,
            )
        }
    }
}

@Composable
private fun MethodCard(
    selected: Boolean,
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            },
        ),
        border = BorderStroke(
            width = 2.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
internal fun ValidationFeedback(
    validation: OnboardingViewModel.ValidationState,
    onRetry: () -> Unit,
) {
    when (validation) {
        is OnboardingViewModel.ValidationState.InProgress -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.onboarding_validate_validating),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        is OnboardingViewModel.ValidationState.Success -> {
            Text(
                stringResource(R.string.onboarding_validate_success),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        is OnboardingViewModel.ValidationState.Failed -> {
            val msg = when (validation.reason) {
                OnboardingViewModel.FailureReason.Invalid ->
                    stringResource(R.string.onboarding_validate_error_invalid)
                OnboardingViewModel.FailureReason.Inconclusive ->
                    stringResource(R.string.onboarding_validate_error_inconclusive)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        OnboardingViewModel.ValidationState.Idle -> {
            // Sin feedback: el usuario aun no pulso "Validar".
            Spacer(Modifier.height(0.dp))
        }
    }
}
