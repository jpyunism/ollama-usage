package com.jpyunism.ollamacloudusage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jpyunism.ollamacloudusage.AlertSettings
import com.jpyunism.ollamacloudusage.UsageViewModel
import com.jpyunism.ollamacloudusage.ui.settings.AccountsSection
import com.jpyunism.ollamacloudusage.ui.settings.AlertSection
import com.jpyunism.ollamacloudusage.ui.settings.BackupSection
import com.jpyunism.ollamacloudusage.ui.settings.DailySummarySection
import com.jpyunism.ollamacloudusage.ui.settings.RefreshSection
import com.jpyunism.ollamacloudusage.ui.settings.ThemeSection
import com.jpyunism.ollamacloudusage.ui.settings.UpdateSection

/**
 * Configuracion unificada: apariencia (idioma, modo, temas), alertas de
 * consumo y actualizaciones. Cada cambio se guarda automaticamente al instante
 * (sin boton de guardar); el snackbar de confirmacion lo maneja UsageScreen.
 * Orquesta las secciones colapsables definidas en ui/settings/.
 */
@Composable
fun SettingsTab(
    vm: UsageViewModel,
    settings: AlertSettings,
    onSettingsChanged: () -> Unit,
) {
    // Estado del ViewModel
    val update by vm.update.collectAsStateWithLifecycle()
    val checkingUpdate by vm.checkingUpdate.collectAsStateWithLifecycle()
    val checkResult by vm.checkResult.collectAsStateWithLifecycle()
    val download by vm.download.collectAsStateWithLifecycle()

    // Persiste el cambio y avisa al snackbar debounced de UsageScreen.
    fun save(newSettings: AlertSettings) {
        vm.updateSettings(newSettings)
        onSettingsChanged()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ThemeSection(vm, onSettingsChanged)
        AlertSection(settings, onSave = ::save)
        DailySummarySection(vm)
        RefreshSection(settings, onSave = ::save)
        AccountsSection(vm)
        BackupSection(vm)
        UpdateSection(
            currentVersion = vm.appVersion,
            update = update,
            checking = checkingUpdate,
            result = checkResult,
            download = download,
            onCheck = { vm.checkForUpdateNow() },
            onDownload = { vm.startUpdateDownload(it) },
        )
    }
}
