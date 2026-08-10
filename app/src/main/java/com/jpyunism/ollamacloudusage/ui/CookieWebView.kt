package com.jpyunism.ollamacloudusage.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jpyunism.ollamacloudusage.CookieExtractor
import com.jpyunism.ollamacloudusage.R

private const val OLLAMA_URL = "https://ollama.com"

/** Estado del flujo de captura de cookie vía WebView. */
private enum class WebViewStatus { Loading, Waiting, Captured, Error }

/**
 * WebView full-screen para iniciar sesión en ollama.com y capturar la cookie
 * de sesión automáticamente.
 *
 * Excepción justificada a la regla Material 3 del repo: WebView es la única
 * vía para renderizar el login real de ollama.com (no existe equivalente
 * Compose); todo el chrome de la pantalla (barra superior, banner de estado)
 * es Material 3.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CookieWebView(
    onCookieCaptured: (String) -> Unit,
    onClose: () -> Unit,
) {
    var status by remember { mutableStateOf(WebViewStatus.Loading) }
    val captured = remember { mutableStateOf(false) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    // Al salir, no dejar la sesión en el almacenamiento del WebView.
    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }

    // Back del sistema: navega atrás dentro del WebView; si no hay historial, cierra.
    BackHandler {
        val wv = webViewRef.value
        if (wv != null && wv.canGoBack()) wv.goBack() else onClose()
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef.value = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            if (!captured.value) status = WebViewStatus.Loading
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (captured.value) return
                            val raw = CookieManager.getInstance().getCookie(OLLAMA_URL)
                            val cookie = CookieExtractor.extract(raw)
                            if (cookie != null) {
                                captured.value = true
                                status = WebViewStatus.Captured
                                onCookieCaptured(cookie)
                            } else {
                                status = WebViewStatus.Waiting
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            if (request?.isForMainFrame == true && !captured.value) {
                                status = WebViewStatus.Error
                            }
                        }
                    }
                    loadUrl(OLLAMA_URL)
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxSize(),
        )

        // ── Chrome de la pantalla (Material 3) ──
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.webview_close),
                        )
                    }
                    Text(
                        stringResource(R.string.login_with_webview),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            StatusBanner(status)
        }
    }
}

@Composable
private fun StatusBanner(status: WebViewStatus) {
    val (text, showProgress) = when (status) {
        WebViewStatus.Loading -> stringResource(R.string.webview_loading) to true
        WebViewStatus.Waiting -> stringResource(R.string.webview_waiting) to false
        WebViewStatus.Captured -> stringResource(R.string.webview_captured) to true
        WebViewStatus.Error -> stringResource(R.string.webview_error) to false
    }
    Surface(
        color = when (status) {
            WebViewStatus.Error -> MaterialTheme.colorScheme.errorContainer
            WebViewStatus.Captured -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
            } else if (status == WebViewStatus.Error) {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = when (status) {
                    WebViewStatus.Error -> MaterialTheme.colorScheme.onErrorContainer
                    WebViewStatus.Captured -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
