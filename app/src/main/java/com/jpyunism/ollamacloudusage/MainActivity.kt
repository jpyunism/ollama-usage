package com.jpyunism.ollamacloudusage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jpyunism.ollamacloudusage.ui.UsageScreen
import com.jpyunism.ollamacloudusage.UsageViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier) {
                    val vm: UsageViewModel = viewModel()
                    UsageScreen(vm)
                }
            }
        }
    }
}
