package com.jpyunism.ollamacloudusage.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jpyunism.ollamacloudusage.UiState
import com.jpyunism.ollamacloudusage.UsageViewModel

@Composable
fun CookieSetup(vm: UsageViewModel, state: UiState) {
    var cookie by remember { mutableStateOf("") }

    Text("Conecta tu cuenta", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Pega la cookie de tu sesión de ollama.com. Abre DevTools → Application → Cookies " +
            "y copia los valores de aid y __Secure-session separados por ;",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = cookie,
        onValueChange = { cookie = it },
        label = { Text("Cookie (aid=...; __Secure-session=...)") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
    )
    Button(
        onClick = { vm.saveCookie(cookie) },
        enabled = cookie.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Guardar y consultar")
    }

    if (state is UiState.Error) {
        Text(
            state.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
