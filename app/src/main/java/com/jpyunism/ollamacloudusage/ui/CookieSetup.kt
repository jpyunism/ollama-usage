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
import androidx.compose.ui.res.stringResource
import com.jpyunism.ollamacloudusage.R
import com.jpyunism.ollamacloudusage.UiState
import com.jpyunism.ollamacloudusage.UsageViewModel

@Composable
fun CookieSetup(vm: UsageViewModel, state: UiState) {
    var cookie by remember { mutableStateOf("") }

    Text(stringResource(R.string.connect_account), style = MaterialTheme.typography.headlineSmall)
    Text(
        stringResource(R.string.cookie_instructions),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = cookie,
        onValueChange = { cookie = it },
        label = { Text(stringResource(R.string.cookie_label)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
    )
    Button(
        onClick = { vm.saveCookie(cookie) },
        enabled = cookie.isNotBlank(),
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
