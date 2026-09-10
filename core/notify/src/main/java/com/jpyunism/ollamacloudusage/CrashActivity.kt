package com.jpyunism.ollamacloudusage

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jpyunism.ollamacloudusage.ui.OllamaUsageTheme

/**
 * Pantalla de error: muestra el stack trace del crash para poder copiarlo y
 * enviarlo (por Telegram, chat, etc.) sin necesidad de adb.
 */
class CrashActivity : ComponentActivity() {

    companion object {
        const val EXTRA_STACK = "extra_stack"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val stack = intent.getStringExtra(EXTRA_STACK) ?: "Sin stack trace."
        setContent {
            OllamaUsageTheme {
                CrashScreen(
                    stack = stack,
                    onCopy = { copyToClipboard(stack) },
                    onExit = { finishAffinity() },
                )
            }
        }
    }

    private fun copyToClipboard(stack: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("ollama-usage-crash", stack))
        Toast.makeText(this, "Stack trace copiado", Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrashScreen(stack: String, onCopy: () -> Unit, onExit: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Ollama Usage — Error") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "La app tuvo un error al iniciar. Copia el detalle y envíalo al desarrollador:",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stack,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                Text("Copiar detalle")
            }
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                Text("Salir")
            }
        }
    }
}
