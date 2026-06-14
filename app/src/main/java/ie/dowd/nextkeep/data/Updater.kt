package ie.dowd.nextkeep.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import ie.dowd.nextkeep.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * In-app updater. NextKeep ships outside any app store, so it updates itself: it
 * checks the GitHub Releases of the CI mirror, downloads the release APK, and
 * hands it to the system package installer.
 *
 * Where releases come from: the Gitea origin (git.henrydowd.dev/henry/NextKeep)
 * is mirrored to GitHub, where Actions builds the APKs and attaches them to a
 * Release on every version tag. An in-place update only succeeds when the new
 * APK is signed with the same key as the installed one, so we always pick
 * `app-release.apk` (the stable-signed artifact), never `app-debug.apk`.
 */
class Updater(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** A release worth offering: its tag, notes, and the release-APK asset. */
    data class Release(
        val tag: String,
        val notes: String,
        val apkUrl: String,
        val apkName: String,
        val sizeBytes: Long,
    )

    /** The latest GitHub release, or null if unreachable or it has no release APK. */
    suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body?.string() ?: return@withContext null
            val dto = json.decodeFromString<ReleaseDto>(body)
            val asset = dto.assets.firstOrNull { isReleaseApkName(it.name) } ?: return@withContext null
            Release(
                tag = dto.tagName,
                notes = dto.body.orEmpty().trim(),
                apkUrl = asset.downloadUrl,
                apkName = asset.name,
                sizeBytes = asset.size,
            )
        }
    }

    /** True when [release] is a newer version series than the build we're running. */
    fun isUpdateAvailable(release: Release): Boolean =
        compareVersions(release.tag, BuildConfig.VERSION_NAME) > 0

    /**
     * Download the release APK into cacheDir/updates, reporting progress in
     * `0f..1f` (or -1f when the server sends no length). Returns the saved file.
     */
    suspend fun download(release: Release, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            // We only ever need the file we're about to write; drop any older one.
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, release.apkName)
            val request = Request.Builder().url(release.apkUrl).build()
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("Download failed (HTTP ${resp.code})")
                val body = resp.body ?: error("Empty download")
                val total = body.contentLength()
                onProgress(if (total > 0) 0f else -1f)
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        var lastPct = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (total > 0) {
                                val pct = ((copied * 100) / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    onProgress(pct / 100f)
                                }
                            }
                        }
                    }
                }
            }
            target
        }

    /** Whether the user has granted NextKeep permission to install apps. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Settings screen where the user grants "install unknown apps" to NextKeep. */
    fun unknownSourcesSettingsIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )

    /** Hand a downloaded APK to the system package installer. */
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    @Serializable
    private data class ReleaseDto(
        @SerialName("tag_name") val tagName: String,
        val body: String? = null,
        val assets: List<AssetDto> = emptyList(),
    )

    @Serializable
    private data class AssetDto(
        val name: String,
        val size: Long = 0,
        @SerialName("browser_download_url") val downloadUrl: String,
    )

    companion object {
        // The Gitea origin is mirrored to this GitHub repo, where CI builds and
        // attaches the release APKs. Change this one line if the host ever moves.
        const val GITHUB_REPO = "hpdowd/nextkeep"

        /** The stable-signed release APK — never the debug or an unsigned one. */
        fun isReleaseApkName(name: String): Boolean =
            name.endsWith(".apk", ignoreCase = true) &&
                name.contains("release", ignoreCase = true) &&
                !name.contains("unsigned", ignoreCase = true)

        /**
         * Compare two versions by their leading dotted-number series, ignoring any
         * suffix (a `-rc1` pre-release, git-describe's `-<n>-g<hash>`, or `-dev`).
         * Returns >0 if [a] is newer than [b], 0 for the same series, <0 if older.
         *
         * This deliberately reads "1.1-rc1" and "1.1" as the same series. The
         * rc→stable bump is also the debug→stable signing change, which can't
         * update in place anyway (the user reinstalls once), so distinguishing
         * them here would only offer an update that couldn't be applied.
         */
        fun compareVersions(a: String, b: String): Int {
            val pa = numericSeries(a)
            val pb = numericSeries(b)
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val cmp = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
                if (cmp != 0) return cmp
            }
            return 0
        }

        private fun numericSeries(raw: String): List<Int> =
            raw.trim().removePrefix("v").removePrefix("V")
                .takeWhile { it.isDigit() || it == '.' }
                .split('.')
                .mapNotNull { it.toIntOrNull() }
    }
}
