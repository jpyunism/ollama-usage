package com.jpyunism.ollamacloudusage

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jpyunism.ollamacloudusage.AppDarkMode
import com.jpyunism.ollamacloudusage.UsageScheduler
import com.jpyunism.ollamacloudusage.ui.OllamaUsageTheme
import com.jpyunism.ollamacloudusage.ui.UsageScreen

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* ignora */ }

    /** Aplica el idioma guardado antes de inflar la UI (arranque en frío). */
    override fun attachBaseContext(newBase: android.content.Context) {
        val prefs = SecurePrefs.get(newBase)
        val language = prefs.getString(UsageViewModel.KEY_LANGUAGE, null)
            ?.let { name -> AppLanguage.entries.firstOrNull { it.name == name } }
            ?: AppLanguage.System
        LocaleHelper.apply(newBase, language)
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        // Si el FGS de refresco rápido quedó pendiente (Android lo denegó por
        // arrancar en background), reintentarlo ahora que la app está visible.
        UsageScheduler.retryPending(applicationContext)
        setContent {
            val vm: UsageViewModel = viewModel(
                factory = UsageViewModel.factory(applicationContext),
            )
            val theme by vm.theme.collectAsStateWithLifecycle()
            val darkMode by vm.darkMode.collectAsStateWithLifecycle()

            OllamaUsageTheme(theme = theme, darkMode = darkMode) {
                Surface(modifier = Modifier) {
                    UsageScreen(vm)
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
