package com.jpyunism.ollamacloudusage.ui

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.jpyunism.ollamacloudusage.AuthSource
import com.jpyunism.ollamacloudusage.R
import com.jpyunism.ollamacloudusage.UiState
import com.jpyunism.ollamacloudusage.UsageViewModel

/** Setup inicial o cambio de acceso: elegir cómo autenticar (API key o cookie). */
@Composable
fun CookieSetup(vm: UsageViewModel, state: UiState) {
    val isChangeAccess = vm.hasAuth()
    var source by remember { mutableStateOf(if (isChangeAccess) vm.authSource.value else AuthSource.API_KEY) }
    var apiKey by remember { mutableStateOf(vm.currentSecret(AuthSource.API_KEY)) }
    var cookie by remember { mutableStateOf(vm.currentSecret(AuthSource.COOKIE)) }
    var showApiKey by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Cabecera ──
        if (isChangeAccess) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.closeAuthSetup() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
                Text(
                    stringResource(R.string.change_auth),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Text(
                stringResource(R.string.change_auth_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                stringResource(R.string.connect_account),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(R.string.auth_instructions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Método: API key ──
        AuthMethodCard(
            selected = source == AuthSource.API_KEY,
            onClick = { source = AuthSource.API_KEY },
            icon = Icons.Outlined.Key,
            title = stringResource(R.string.auth_api_key),
            subtitle = stringResource(R.string.auth_api_key_hint),
        )
        if (source == AuthSource.API_KEY) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(stringResource(R.string.api_key_label)) },
                leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            if (showApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = stringResource(
                                if (showApiKey) R.string.hide_key else R.string.show_key
                            ),
                        )
                    }
                },
                visualTransformation = if (showApiKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ── Método: cookie ──
        AuthMethodCard(
            selected = source == AuthSource.COOKIE,
            onClick = { source = AuthSource.COOKIE },
            icon = Icons.Outlined.Cookie,
            title = stringResource(R.string.auth_cookie),
            subtitle = stringResource(R.string.auth_cookie_hint),
        )
        if (source == AuthSource.COOKIE) {
            OutlinedTextField(
                value = cookie,
                onValueChange = { cookie = it },
                label = { Text(stringResource(R.string.cookie_label)) },
                leadingIcon = { Icon(Icons.Outlined.Cookie, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.cookie_instructions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f))
                Text(
                    stringResource(R.string.or_separator),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                HorizontalDivider(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = { vm.openCookieWebView() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Language, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.cookie_login_button))
            }
            Text(
                stringResource(R.string.cookie_login_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Error ──
        if (state is UiState.Error) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        // ── Guardar ──
        Button(
            onClick = {
                when (source) {
                    AuthSource.API_KEY -> vm.saveApiKey(apiKey)
                    AuthSource.COOKIE -> vm.saveCookie(cookie)
                }
            },
            enabled = when (source) {
                AuthSource.API_KEY -> apiKey.isNotBlank()
                AuthSource.COOKIE -> cookie.isNotBlank()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.save_and_check), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Tarjeta seleccionable para un método de autenticación. */
@Composable
private fun AuthMethodCard(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    title: String,
    subtitle: String,
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
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
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
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}
