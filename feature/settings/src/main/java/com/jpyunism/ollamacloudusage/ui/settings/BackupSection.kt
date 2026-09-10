package com.jpyunism.ollamacloudusage.ui.settings

import com.jpyunism.ollamacloudusage.core.ui.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jpyunism.ollamacloudusage.UsageViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup/restore del historico de snapshots (Feature B).
 * Export: JSON via SAF (CreateDocument). Import: JSON con merge por timestamp.
 */
@Composable
fun BackupSection(vm: UsageViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusRes by remember { mutableStateOf<Int?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val json = vm.exportSnapshots()
            scope.launch {
                val ok = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } != null
                }.getOrDefault(false)
                statusRes = if (ok) R.string.backup_export_ok else R.string.backup_export_error
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = runCatching {
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                val ok = json != null && vm.importSnapshots(json)
                statusRes = if (ok) R.string.backup_import_ok else R.string.backup_import_error
            }
        }
    }

    SettingsSection(
        titleRes = R.string.backup_title,
        subtitleRes = R.string.backup_description,
        icon = Icons.Outlined.Backup,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = { exportLauncher.launch(backupFileName()) }) {
                Icon(Icons.Outlined.Backup, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.backup_export))
            }
            OutlinedButton(onClick = {
                importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
            }) {
                Icon(Icons.Outlined.Restore, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.backup_import))
            }
        }
        statusRes?.let { res ->
            Text(
                stringResource(res),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Timestamp legible para nombrar el archivo de backup: 2026-08-28_0930.json */
private fun backupFileName(): String =
    SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date()) + ".json"
