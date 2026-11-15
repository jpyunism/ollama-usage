package com.jpyunism.ollamacloudusage

import com.jpyunism.ollamacloudusage.core.ui.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Servicio en primer plano que descarga el APK de la última versión desde
 * GitHub y lanza el instalador del sistema. Muestra el progreso en una
 * notificación y falla de forma controlada (notificación de error, sin crash).
 */
class UpdaterService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var expectedSha256: String? = null
    private val httpClient: okhttp3.OkHttpClient by lazy {
        com.jpyunism.ollamacloudusage.di.AppContainer.get(this).httpClient
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground() PRIMERO y sin condiciones: si el servicio arrancó
        // con startForegroundService() y no lo llama en ~5 s, el sistema mata
        // el proceso entero con ForegroundServiceDidNotStartInTimeException
        // (RemoteServiceException en el hilo main). Antes esto se validaba
        // después del permiso de instalación, así que sin el permiso la app
        // moría al tocar "Actualizar".
        startForeground(NOTIFICATION_ID, buildProgress(this, 0))

        val url = intent?.getStringExtra(EXTRA_URL)
        if (url == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        expectedSha256 = intent.getStringExtra(EXTRA_SHA256)
        // Sin el permiso "Instalar apps desconocidas" el instalador del sistema
        // rechaza el APK; avisamos antes de descargar. El foreground ya está
        // publicado, así que este camino no puede tumbar el proceso.
        if (!canRequestPackageInstalls(this)) {
            state.value = DownloadState.NeedsPermission
            stopForeground(STOP_FOREGROUND_REMOVE)
            notifyNeedsPermission(this)
            stopSelf()
            return START_NOT_STICKY
        }
        state.value = DownloadState.Idle
        scope.launch { downloadAndInstall(url) }
        return START_NOT_STICKY
    }

    private suspend fun downloadAndInstall(url: String) {
        val result = withContext(Dispatchers.IO) {
            runCatching { downloadApk(url) }
        }
        result.fold(
            onSuccess = { file ->
                // REQ-016: verificar la integridad del APK antes de instalar.
                val expected = expectedSha256
                if (expected != null && !verifySha256(file, expected)) {
                    val msg = "Firma no coincide (sha256)"
                    state.value = DownloadState.Failed(msg)
                    notifyError(this, msg)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return
                }
                state.value = DownloadState.Ready(file)
                install(this, file)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            },
            onFailure = { e ->
                state.value = DownloadState.Failed(e.message ?: "Error")
                notifyError(this, e.message ?: "Error")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            },
        )
    }

    /** Descarga a cacheDir con progreso 0..100. Devuelve el APK o lanza. */
    private fun downloadApk(url: String): File {
        val request = okhttp3.Request.Builder().url(url).build()
        val resp = httpClient.newCall(request).execute()
        if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
        val body = resp.body ?: throw RuntimeException("Sin cuerpo de respuesta")
        val total = body.contentLength()
        val file = File(cacheDir, "update.apk")
        body.byteStream().use { input ->
            file.outputStream().use { output ->
                val buf = ByteArray(8192)
                var read: Int
                var done = 0L
                var lastPct = -1
                while (input.read(buf).also { read = it } != -1) {
                    output.write(buf, 0, read)
                    done += read
                    if (total > 0) {
                        val pct = ((done * 100) / total).toInt().coerceIn(0, 100)
                        if (pct != lastPct) {
                            lastPct = pct
                            updateNotification(this, pct)
                            state.value = DownloadState.Downloading(pct)
                        }
                    }
                }
            }
        }
        return file
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URL = "update_url"
        private const val EXTRA_SHA256 = "update_sha256"
        private const val NOTIFICATION_ID = 2001
        const val CHANNEL_ID = "updates"

        /** Progreso de la descarga, observable desde la UI (SettingsTab). */
        val state = MutableStateFlow<DownloadState>(DownloadState.Idle)

        /**
         * Arranca la descarga del APK. Devuelve false (sin arrancar el
         * servicio) si falta el permiso "Instalar apps desconocidas": en ese
         * caso deja [state] en [DownloadState.NeedsPermission] y publica la
         * notificación con el aviso.
         *
         * No arrancar el servicio en ese caso es deliberado: un
         * `startForegroundService()` que retorna sin `startForeground()` hace
         * que el sistema mate el proceso del usuario a los ~5 s.
         */
        fun start(context: Context, url: String, sha256: String? = null): Boolean {
            if (!canRequestPackageInstalls(context)) {
                state.value = DownloadState.NeedsPermission
                notifyNeedsPermission(context)
                // El usuario ya expresó la intención de instalar: lo llevamos
                // directo al ajuste del sistema en vez de dejarlo sin feedback.
                openInstallPermissionSettings(context)
                return false
            }
            val intent = Intent(context, UpdaterService::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_SHA256, sha256)
            ContextCompat.startForegroundService(context, intent)
            return true
        }

        /** SHA-256 en hex del archivo; true si coincide con [expected]. */
        fun verifySha256(file: File, expected: String): Boolean = runCatching {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(8192)
                while (true) {
                    val read = input.read(buf)
                    if (read < 0) break
                    digest.update(buf, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }.equals(expected.lowercase(), ignoreCase = true)
        }.getOrDefault(false)

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.channel_updates_name),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = context.getString(R.string.channel_updates_description)
                        setShowBadge(false)
                    }
                )
            }
        }

        private fun buildProgress(context: Context, progress: Int): android.app.Notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(context.getString(R.string.update_downloading))
                .setContentText(context.getString(R.string.update_download_progress, progress))
                .setProgress(100, progress, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()

        private fun updateNotification(context: Context, progress: Int) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            runCatching { manager.notify(NOTIFICATION_ID, buildProgress(context, progress)) }
        }

        /** Lanza el instalador del sistema con el APK descargado. */
        private fun install(context: Context, apk: File) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        /** true si el usuario habilitó "Instalar apps desconocidas" para este paquete. */
        fun canRequestPackageInstalls(context: Context): Boolean =
            context.packageManager.canRequestPackageInstalls()

        /** Abre la pantalla del sistema para habilitar la instalación de apps. */
        fun openInstallPermissionSettings(context: Context) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }

        private fun notifyNeedsPermission(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(context.getString(R.string.update_needs_permission_title))
                .setContentText(context.getString(R.string.update_needs_permission_message))
                .setAutoCancel(true)
                .build()
            runCatching { manager.notify(NOTIFICATION_ID, notification) }
        }

        private fun notifyError(context: Context, message: String) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(context.getString(R.string.update_failed))
                .setContentText(message)
                .setAutoCancel(true)
                .build()
            runCatching { manager.notify(NOTIFICATION_ID, notification) }
        }
    }
}
