package com.jpyunism.ollamacloudusage.ui.settings

import com.jpyunism.ollamacloudusage.core.ui.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpyunism.ollamacloudusage.Account
import com.jpyunism.ollamacloudusage.UsageViewModel

/**
 * Gestion de cuentas (Feature A lote 2, issue #25): agregar cuenta API key,
 * renombrar, eliminar y elegir la activa. REQ-106.
 */
@Composable
fun AccountsSection(vm: UsageViewModel) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val activeId by vm.activeAccountId.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Account?>(null) }

    SettingsSection(
        titleRes = R.string.accounts_title,
        subtitleRes = R.string.accounts_description,
        icon = Icons.Outlined.ManageAccounts,
    ) {
        accounts.forEach { account ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = account.id == activeId,
                        onClick = { vm.switchAccount(account.id) },
                        label = { Text(account.label) },
                    )
                }
                IconButton(onClick = { renameTarget = account }) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.accounts_rename))
                }
                IconButton(onClick = { vm.removeAccount(account.id) }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.accounts_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        FilledTonalButton(onClick = { showAdd = true }) {
            Icon(Icons.Outlined.Add, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.accounts_add))
        }
    }

    if (showAdd) {
        AccountTextDialog(
            title = stringResource(R.string.accounts_add),
            labelLabel = stringResource(R.string.accounts_label),
            keyLabel = stringResource(R.string.accounts_apikey),
            confirmText = stringResource(R.string.accounts_add),
            onConfirm = { label, key ->
                if (label.isNotBlank() && key.isNotBlank()) vm.addAccount(label.trim(), key.trim())
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
    renameTarget?.let { account ->
        AccountTextDialog(
            title = stringResource(R.string.accounts_rename),
            labelLabel = stringResource(R.string.accounts_label),
            keyLabel = "",
            confirmText = stringResource(R.string.save_generic),
            initialLabel = account.label,
            initialKey = "",
            onConfirm = { label, _ ->
                if (label.isNotBlank()) vm.renameAccount(account.id, label.trim())
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }
}

/** Dialogo con campos label + (opcional) API key. */
@Composable
private fun AccountTextDialog(
    title: String,
    labelLabel: String,
    keyLabel: String,
    confirmText: String,
    initialLabel: String = "",
    initialKey: String = "",
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf(initialLabel) }
    var apiKey by remember { mutableStateOf(initialKey) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(labelLabel) },
                    singleLine = true,
                )
                if (keyLabel.isNotEmpty()) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text(keyLabel) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(label, apiKey) }) { Text(confirmText) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
