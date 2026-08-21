package org.sovereignhq.nightjar.update

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.sovereignhq.nightjar.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub for a newer release and installs it.
 *
 * This is the only code in Nightjar that touches the network, and it is deliberately the dullest
 * possible use of one: an unauthenticated GET to a public URL. It sends no identifier, no device
 * information and no usage data - there is nothing to send it to. Everything else in the app,
 * including all audio, stays on the phone.
 *
 * It can be switched off, in which case the app makes no network requests whatsoever.
 */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val REPO = "Sovereignhq-aviv/nightjar"
    private const val LATEST_RELEASE = "https://api.github.com/repos/$REPO/releases/latest"

    /** GitHub rejects requests with no User-Agent, so this is not optional. */
    private const val USER_AGENT = "Nightjar-Updater"

    private val json = Json { ignoreUnknownKeys = true }

    data class Release(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val sizeBytes: Long
    )

    /**
     * Compares two version strings by their numeric parts, tolerating a leading "v" and ignoring any
     * suffix. Deliberately conservative: anything unparseable counts as "not newer", so a malformed
     * tag can never prompt someone to install something.
     */
    fun isNewer(remote: String, installed: String): Boolean {
        val r = numericParts(remote)
        val i = numericParts(installed)
        if (r.isEmpty()) return false
        for (index in 0 until maxOf(r.size, i.size)) {
            val a = r.getOrElse(index) { 0 }
            val b = i.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun numericParts(version: String): List<Int> =
        version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .takeWhile { it.isDigit() || it == '.' }
            .split('.')
            .mapNotNull { it.toIntOrNull() }

    /** The newest release, or null if there is no newer one, or if anything at all went wrong. */
    suspend fun findUpdate(): Release? = withContext(Dispatchers.IO) {
        val body = runCatching { get(LATEST_RELEASE) }.getOrElse {
            Log.w(TAG, "Update check failed", it)
            return@withContext null
        }

        val release = runCatching { json.decodeFromString(GhRelease.serializer(), body) }
            .getOrElse {
                Log.w(TAG, "Could not read the release feed", it)
                return@withContext null
            }

        if (!isNewer(release.tag, BuildConfig.VERSION_NAME)) return@withContext null

        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: return@withContext null

        Release(
            version = release.tag.removePrefix("v"),
            notes = release.body.trim(),
            apkUrl = apk.url,
            sizeBytes = apk.size
        )
    }

    /**
     * Downloads the APK into the cache. Returns null on any failure, and deletes a partial file
     * rather than leaving something half-downloaded to be installed.
     */
    suspend fun download(
        context: Context,
        release: Release,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // One file at a time; an abandoned older download is just dead weight.
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "Nightjar-${release.version}.apk")

        try {
            openConnection(release.apkUrl).use { stream ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    var reportedAt = 0L
                    val step = if (release.sizeBytes > 0) release.sizeBytes / 50 else Long.MAX_VALUE
                    while (true) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        total += read
                        // Throttled to ~50 updates, and marshalled onto the main thread because the
                        // receiver is Compose state and this loop is on an IO dispatcher.
                        if (total - reportedAt >= step) {
                            reportedAt = total
                            val fraction = (total.toFloat() / release.sizeBytes).coerceIn(0f, 1f)
                            withContext(Dispatchers.Main) { onProgress(fraction) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Download failed", e)
            target.delete()
            return@withContext null
        }

        if (release.sizeBytes > 0 && target.length() != release.sizeBytes) {
            Log.w(TAG, "Size mismatch: got ${target.length()}, expected ${release.sizeBytes}")
            target.delete()
            return@withContext null
        }

        withContext(Dispatchers.Main) { onProgress(1f) }
        target
    }

    /**
     * Hands the APK to Android's installer. It always shows its own confirmation - nothing here can
     * install anything silently, and that is correct: an app that could replace itself without
     * asking would be indistinguishable from malware.
     */
    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.clips", apk)
        return Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, false)
        }
    }

    // ---- plumbing ----

    private fun get(url: String): String =
        openConnection(url).use { it.readBytes().toString(Charsets.UTF_8) }

    /**
     * Redirects are followed by hand because GitHub sends release downloads off to a different host,
     * and HttpURLConnection will not follow a cross-host redirect on its own.
     */
    private fun openConnection(url: String, depth: Int = 0): java.io.InputStream {
        if (depth > MAX_REDIRECTS) throw IllegalStateException("Too many redirects")

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/vnd.github+json")
        }

        return when (val code = connection.responseCode) {
            in 200..299 -> connection.inputStream

            in 300..399 -> {
                val next = connection.getHeaderField("Location")
                connection.disconnect()
                if (next.isNullOrBlank()) throw IllegalStateException("Redirect with no target")
                openConnection(next, depth + 1)
            }

            else -> {
                connection.disconnect()
                throw IllegalStateException("HTTP $code")
            }
        }
    }

    private const val MAX_REDIRECTS = 5

    @Serializable
    private data class GhRelease(
        @SerialName("tag_name") val tag: String = "",
        @SerialName("body") val body: String = "",
        @SerialName("assets") val assets: List<GhAsset> = emptyList()
    )

    @Serializable
    private data class GhAsset(
        @SerialName("name") val name: String = "",
        @SerialName("browser_download_url") val url: String = "",
        @SerialName("size") val size: Long = 0
    )
}
