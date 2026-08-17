package com.jpyunism.ollamacloudusage.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpyunism.ollamacloudusage.R
import com.jpyunism.ollamacloudusage.UsageViewModel
import com.jpyunism.ollamacloudusage.DownloadState

enum class Tab(val labelRes: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    Usage(R.string.tab_usage, Icons.Outlined.Speed, Icons.Filled.Speed),
    Stats(R.string.tab_stats, Icons.Outlined.BarChart, Icons.Filled.BarChart),
    Settings(R.string.tab_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(vm: UsageViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val download by vm.download.collectAsStateWithLifecycle()
    // rememberSaveable: la pestaña sobrevive a recreate() (p.ej. cambio de
    // idioma), que de otro modo devolvería la vista a la pestaña inicial.
    var tab by rememberSaveable { mutableStateOf(Tab.Usage) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var settingsSaveJob by remember { mutableStateOf<Job?>(null) }
    val settingsSavedMessage = stringResource(R.string.settings_saved)

    // Debounce de 1 s: cada cambio de configuración reinicia el timer y al
    // final muestra el snackbar temporal "Configuración guardada".
    val onSettingsChanged: () -> Unit = {
        settingsSaveJob?.cancel()
        settingsSaveJob = scope.launch {
            delay(1000)
            snackbarHostState.showSnackbar(
                message = settingsSavedMessage,
                duration = SnackbarDuration.Short,
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            Icon(
                                if (tab == t) t.selectedIcon else t.icon,
                                contentDescription = stringResource(t.labelRes),
                            )
                        },
                        label = { Text(stringResource(t.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            Column(Modifier.fillMaxSize()) {
                if (update != null && download !is DownloadState.Downloading) {
                    UpdateBanner(
                        version = update!!.versionName,
                        onClick = { vm.startUpdateDownload(update!!) },
                    )
                }
                Box(Modifier.fillMaxSize()) {
                    when (tab) {
                        Tab.Usage -> UsageTab(vm, state)
                        Tab.Stats -> StatsTab(vm.history.collectAsStateWithLifecycle().value)
                        Tab.Settings -> SettingsTab(vm, settings, onSettingsChanged)
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateBanner(version: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.update_available, version),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onClick) {
                Text(stringResource(R.string.update_install))
            }
        }
    }
}
