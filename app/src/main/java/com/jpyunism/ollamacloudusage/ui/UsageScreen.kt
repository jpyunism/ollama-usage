package com.jpyunism.ollamacloudusage.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpyunism.ollamacloudusage.R
import com.jpyunism.ollamacloudusage.UsageViewModel

enum class Tab(val labelRes: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    Usage(R.string.tab_usage, Icons.Outlined.Speed, Icons.Filled.Speed),
    Alerts(R.string.tab_alerts, Icons.Outlined.Notifications, Icons.Filled.Notifications),
    Themes(R.string.tab_themes, Icons.Outlined.Palette, Icons.Filled.Palette),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(vm: UsageViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.Usage) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
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
            when (tab) {
                Tab.Usage -> UsageTab(vm, state)
                Tab.Alerts -> AlertsTab(vm, settings)
                Tab.Themes -> ThemesTab(vm)
            }
        }
    }
}
