package com.jpyunism.ollamacloudusage.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jpyunism.ollamacloudusage.AuthSource
import com.jpyunism.ollamacloudusage.R
import com.jpyunism.ollamacloudusage.UiState
import com.jpyunism.ollamacloudusage.UsageViewModel

/** Setup inicial: elegir cómo autenticar (API key de Ollama Cloud o cookie). */
@Composable
fun CookieSetup(vm: UsageViewModel, state: UiState) {
    var source by remember { mutableStateOf(AuthSource.API_KEY) }
    var apiKey by remember { mutableStateOf("") }
    var cookie by remember { mutableStateOf("") }

    Text(stringResource(R.string.connect_account), style = MaterialTheme.typography.headlineSmall)
    Text(
        stringResource(R.string.auth_instructions),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = source == AuthSource.API_KEY,
            onClick = { source = AuthSource.API_KEY },
        )
        Column {
            Text(stringResource(R.string.auth_api_key), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.auth_api_key_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (source == AuthSource.API_KEY) {
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text(stringResource(R.string.api_key_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = source == AuthSource.COOKIE,
            onClick = { source = AuthSource.COOKIE },
        )
        Column {
            Text(stringResource(R.string.auth_cookie), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.auth_cookie_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (source == AuthSource.COOKIE) {
        OutlinedTextField(
            value = cookie,
            onValueChange = { cookie = it },
            label = { Text(stringResource(R.string.cookie_label)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
    }

    Spacer(Modifier.height(8.dp))
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
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.save_and_check))
    }

    if (state is UiState.Error) {
        Text(
            state.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
