package com.jpyunism.ollamacloudusage

import android.content.Context
import android.content.pm.PackageManager
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Info de una versión publicada detectada como actualización disponible. */
data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String,
    val sha256: String?,
)

/** Resultado de un chequeo manual de actualización. */
sealed interface UpdateCheckOutcome {
    data class Available(val info: UpdateInfo) : UpdateCheckOutcome
    data object UpToDate : UpdateCheckOutcome
    data object Failed : UpdateCheckOutcome
}

/** Estado de la descarga de la actualización. */
sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val progress: Int) : DownloadState
    data class Ready(val file: java.io.File) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * Chequeo de nuevas versiones contra el release latest de GitHub
 * (repo público: no requiere token). Descarga e instalación quedan en
 * [UsageViewModel]; acá vive solo la detección y el parseo.
 */
object UpdateChecker {

    private const val REPO = "jpyunism/ollama-usage"
    private const val RELEASES_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val KEY_LAST_CHECK = "update_last_check_ms"

    /** No consultar GitHub más de una vez por día (rate limit + batería). */
    const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun shouldCheck(context: Context): Boolean {
        val last = SecurePrefs.get(context).getLong(KEY_LAST_CHECK, 0)
        return System.currentTimeMillis() - last >= CHECK_INTERVAL_MS
    }

    fun markChecked(context: Context) {
        SecurePrefs.get(context).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }

    /** Consulta el release latest y devuelve la update si es más nueva que la instalada. */
    fun check(context: Context): UpdateInfo? {
        val request = Request.Builder()
            .url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "OllamaUsage/Android")
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string().orEmpty()
            return parseRelease(body, currentVersion(context))
        }
    }

    fun currentVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    }.getOrElse { "0" }

    /**
     * Parsea el JSON del release latest. Lógica pura (testeable):
     * devuelve null si el tag no es más nuevo que [currentVersion] o si
     * no hay asset APK.
     */
    internal fun parseRelease(json: String, currentVersion: String): UpdateInfo? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val tag = root.optString("tag_name", "").removePrefix("v")
        if (tag.isBlank() || !isNewer(tag, currentVersion)) return null

        val assets = root.optJSONArray("assets") ?: return null
        var url: String? = null
        var digest: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name", "").endsWith(".apk")) {
                url = a.optString("browser_download_url", "").takeIf { it.isNotBlank() }
                digest = a.optString("digest", "").removePrefix("sha256:").takeIf { it.isNotBlank() }
                break
            }
        }
        val downloadUrl = url ?: return null
        return UpdateInfo(tag, downloadUrl, digest)
    }

    /** Compara semver "x.y.z": true si latest > current. */
    internal fun isNewer(latest: String, current: String): Boolean {
        val l = parseSemver(latest) ?: return false
        val c = parseSemver(current) ?: return false
        return compareTriple(l, c) > 0
    }

    private fun compareTriple(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Int {
        val major = a.first.compareTo(b.first)
        if (major != 0) return major
        val minor = a.second.compareTo(b.second)
        if (minor != 0) return minor
        return a.third.compareTo(b.third)
    }

    private fun parseSemver(v: String): Triple<Int, Int, Int>? {
        val parts = v.trim().split(".")
        if (parts.size < 2) return null
        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts[1].toIntOrNull() ?: return null
        val patch = parts.getOrNull(2)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
        return Triple(major, minor, patch)
    }
}
