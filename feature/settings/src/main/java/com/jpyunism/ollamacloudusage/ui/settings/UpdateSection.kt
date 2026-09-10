package com.jpyunism.ollamacloudusage.ui.settings

import com.jpyunism.ollamacloudusage.core.ui.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jpyunism.ollamacloudusage.DownloadState
import com.jpyunism.ollamacloudusage.UpdateCheckOutcome
import com.jpyunism.ollamacloudusage.UpdateInfo
import com.jpyunism.ollamacloudusage.UpdaterService

/** Actualizacion: version actual, chequeo manual y descarga. */
@Composable
fun UpdateSection(
    currentVersion: String,
    update: UpdateInfo?,
    checking: Boolean,
    result: UpdateCheckOutcome?,
    download: DownloadState,
    onCheck: () -> Unit,
    onDownload: (UpdateInfo) -> Unit,
) {
    SettingsSection(
        titleRes = R.string.section_update,
        subtitleRes = R.string.update_section_description,
        icon = Icons.Outlined.SystemUpdate,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBox(Icons.Outlined.SystemUpdate)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.version_current, currentVersion),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.update_auto_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        when {
            download is DownloadState.Downloading -> {
                LinearProgressIndicator(
                    progress = { download.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.update_downloading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            download is DownloadState.Failed -> {
                Text(
                    stringResource(R.string.update_download_failed, download.message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            download is DownloadState.NeedsPermission -> {
                Text(
                    stringResource(R.string.update_needs_permission_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                val context = LocalContext.current
                FilledTonalButton(
                    onClick = { UpdaterService.openInstallPermissionSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.update_install_permission))
                }
            }

            update != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.update_available, update.versionName),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onDownload(update) }) {
                        Text(stringResource(R.string.update_install))
                    }
                }
            }

            result is UpdateCheckOutcome.UpToDate -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.update_up_to_date, currentVersion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            result is UpdateCheckOutcome.Failed -> {
                Text(
                    stringResource(R.string.update_check_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            checking -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.update_checking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                Text(
                    stringResource(R.string.update_check_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onCheck,
            enabled = !checking && download !is DownloadState.Downloading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.Outlined.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.update_check))
        }
    }
}
