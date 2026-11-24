package com.example.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.example.BuildConfig
import com.example.data.local.UpdateChannelPref
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class AppReleaseInfo(
    val tagName: String,
    val title: String,
    val releaseNotes: String,
    val apkDownloadUrl: String?,
    val apkSizeBytes: Long,
    val releasePageUrl: String,
    val isPrerelease: Boolean,
    val isUpdateAvailable: Boolean,
    val assetId: Long = 0L,
    val publishedAtMs: Long = 0L,
    val resolvedVersionLabel: String = tagName
)

sealed class UpdateCheckOutcome {
    data class NeedsConfiguration(val message: String) : UpdateCheckOutcome()
    data class UpToDate(
        val installedVersion: String,
        val latestTag: String?,
        val message: String,
        val latestRelease: AppReleaseInfo? = null,
        val buildInProgressNote: String? = null
    ) : UpdateCheckOutcome()

    data class UpdateAvailable(
        val installedVersion: String,
        val release: AppReleaseInfo
    ) : UpdateCheckOutcome()

    data class Error(val message: String) : UpdateCheckOutcome()
}

class AppUpdateManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "zero_play_ota_prefs"
        private const val KEY_LAST_INSTALLED_ASSET_ID = "last_installed_asset_id"
        private const val KEY_LAST_INSTALLED_PUBLISHED_MS = "last_installed_published_ms"
        private const val TIMESTAMP_GRACE_MS = 5L * 60L * 1000L // 5 minutes
        private const val OTA_PARALLEL_WORKERS = 6
        private const val OTA_SLICE_BYTES = 4L * 1024L * 1024L
        private const val OTA_BUFFER_BYTES = 128 * 1024

        private val VERSION_PATTERN = Regex("""(?i)(?:v\.?\s*|version\s*)?(\d+(?:\.\d+)+)""")
        private val SINGLE_NUM_PATTERN = Regex("""(?i)(?:v\.?\s*|zero\s*|r|build\s*)(\d+)\b""")

        /**
         * Extracts the highest semantic version list found across any release fields
         * (e.g., Release Title "Zero play V.2.0.10", Tag "Zero2", or APK name "ZeroPlay-v2.1.0.4.apk").
         */
        internal fun extractBestVersionSegments(vararg candidates: String?): List<Int> {
            val allParsed = mutableListOf<List<Int>>()
            for (raw in candidates) {
                if (raw.isNullOrBlank()) continue
                // Multi-segment versions like "V.2.0.10", "v2.1.0.4", "2.0.10"
                VERSION_PATTERN.findAll(raw).forEach { match ->
                    val segs = match.groupValues[1]
                        .split(".")
                        .mapNotNull { it.toIntOrNull() }
                    if (segs.size >= 2) {
                        allParsed += segs
                    }
                }
                // Fallback single integer tag like "Zero2" or "v3"
                if (allParsed.isEmpty()) {
                    SINGLE_NUM_PATTERN.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { num ->
                        allParsed += listOf(num)
                    }
                }
            }
            return allParsed.maxWithOrNull { a, b -> compareSegments(a, b) } ?: emptyList()
        }

        internal fun parseIso8601UtcMillis(raw: String?): Long {
            if (raw.isNullOrBlank()) return 0L
            val patterns = listOf(
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
            )
            for (pattern in patterns) {
                val parsed = runCatching {
                    SimpleDateFormat(pattern, Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.parse(raw.trim())?.time
                }.getOrNull()
                if (parsed != null && parsed > 0L) return parsed
            }
            return 0L
        }

        internal fun isReleaseNewer(
            tagName: String,
            title: String,
            apkFileName: String?,
            manifestVersionName: String?,
            manifestVersionCode: Long,
            publishedAtMs: Long,
            assetId: Long,
            installedName: String,
            installedCode: Long,
            installedTimestampMs: Long,
            lastInstalledAssetId: Long
        ): Boolean {
            // 1. Explicit manifest versionCode always wins when present
            if (manifestVersionCode > 0L && manifestVersionCode > installedCode) {
                return true
            }

            // 2. Compare highest parsed semver across manifest, title, tag, and APK filename
            val remoteSegments = extractBestVersionSegments(manifestVersionName, title, tagName, apkFileName)
            val localSegments = extractBestVersionSegments(installedName)

            if (remoteSegments.isNotEmpty() && localSegments.isNotEmpty()) {
                // Support 4-segment CI tags like v2.1.0.5 over 3-segment 2.1.0 or v2.0.10 over 2.0
                if (remoteSegments.size == localSegments.size + 1 && remoteSegments.size >= 2) {
                    val baseCmp = compareSegments(remoteSegments.dropLast(1), localSegments)
                    if (baseCmp != 0) return baseCmp > 0
                    val trailing = remoteSegments.last().toLong()
                    // For e.g. "2.0.10" vs installed "2.0" (code 2): 10 > 2 -> true!
                    if (trailing > installedCode || trailing > 0L) {
                        // Only treat as equal if trailing matches exact installedCode AND asset already installed
                        if (trailing > installedCode) return true
                        if (compareSegments(remoteSegments, localSegments) > 0 && assetId != lastInstalledAssetId) {
                            return true
                        }
                    }
                }
                val cmp = compareSegments(remoteSegments, localSegments)
                if (cmp > 0) return true
                if (cmp < 0) return false
            }

            // 3. Timestamp & Asset ID fallback: if a brand new GitHub Release / APK was uploaded
            // after this installed APK was built/updated, offer the OTA update even with custom tags like "Zero2"
            if (publishedAtMs > 0L && installedTimestampMs > 0L) {
                val isNewerByTime = publishedAtMs > (installedTimestampMs + TIMESTAMP_GRACE_MS)
                val isDifferentAsset = assetId == 0L || assetId != lastInstalledAssetId
                if (isNewerByTime && isDifferentAsset) {
                    return true
                }
            }
            return false
        }

        internal fun compareSegments(a: List<Int>, b: List<Int>): Int {
            val max = maxOf(a.size, b.size)
            for (i in 0 until max) {
                val av = a.getOrElse(i) { 0 }
                val bv = b.getOrElse(i) { 0 }
                if (av != bv) return av.compareTo(bv)
            }
            return 0
        }
    }

    private val prefs by lazy {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val httpClient: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 12
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun getInstalledVersionLabel(): String {
        val pkgInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val vName = pkgInfo?.versionName ?: BuildConfig.VERSION_NAME
        val vCode = pkgInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: BuildConfig.VERSION_CODE.toLong()
        return "v$vName ($vCode)"
    }

    private data class InstalledAppMeta(
        val versionName: String,
        val versionCode: Long,
        val baselineTimestampMs: Long
    )

    private fun getInstalledMeta(): InstalledAppMeta {
        val pkgInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val vName = pkgInfo?.versionName ?: BuildConfig.VERSION_NAME
        val vCode = pkgInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: BuildConfig.VERSION_CODE.toLong()
        val installTime = pkgInfo?.lastUpdateTime ?: 0L
        val buildTime = runCatching { BuildConfig.BUILD_TIMESTAMP_MS }.getOrDefault(0L)
        val storedPublished = prefs.getLong(KEY_LAST_INSTALLED_PUBLISHED_MS, 0L)
        val baseline = maxOf(installTime, buildTime, storedPublished)
        return InstalledAppMeta(vName, vCode, baseline)
    }

    fun markReleaseInstallLaunched(release: AppReleaseInfo) {
        prefs.edit()
            .putLong(KEY_LAST_INSTALLED_ASSET_ID, release.assetId)
            .putLong(KEY_LAST_INSTALLED_PUBLISHED_MS, release.publishedAtMs)
            .apply()
    }

    suspend fun checkForUpdate(
        repoInput: String,
        directFeedUrl: String,
        channel: UpdateChannelPref
    ): UpdateCheckOutcome = withContext(Dispatchers.IO) {
        val meta = getInstalledMeta()
        val installedLabel = "v${meta.versionName} (${meta.versionCode})"
        val lastAssetId = prefs.getLong(KEY_LAST_INSTALLED_ASSET_ID, -1L)

        val cleanFeedUrl = directFeedUrl.trim()
        if (cleanFeedUrl.isNotBlank()) {
            return@withContext checkDirectFeedOrApk(cleanFeedUrl, meta, installedLabel, lastAssetId)
        }

        val slug = parseGitHubSlug(repoInput)
        if (slug == null) {
            return@withContext UpdateCheckOutcome.NeedsConfiguration(
                "Enter a public GitHub repository (e.g. nilimadas567345-sudo/Zero-play-) or a direct APK / version.json URL below."
            )
        }

        try {
            // Always fetch releases list (up to 10) so we can gracefully handle draft/prerelease/latest
            val (code, body) = httpGetString("https://api.github.com/repos/$slug/releases?per_page=10")
            if (code == 404) {
                return@withContext UpdateCheckOutcome.UpToDate(
                    installedVersion = installedLabel,
                    latestTag = null,
                    message = "Repository $slug is reachable, but no GitHub Releases have been published yet. Push to main to trigger the OTA APK workflow.",
                    buildInProgressNote = checkRunningWorkflowNote(slug)
                )
            }
            if (code !in 200..299) {
                return@withContext UpdateCheckOutcome.Error(
                    "GitHub returned HTTP $code for $slug. Verify the repository is public."
                )
            }

            val arr = JSONArray(body)
            val candidates = (0 until arr.length())
                .map { arr.getJSONObject(it) }
                .filter { !it.optBoolean("draft", false) }

            val chosenJson = if (channel == UpdateChannelPref.STABLE) {
                candidates.firstOrNull { !it.optBoolean("prerelease", false) }
                    ?: candidates.firstOrNull()
            } else {
                candidates.firstOrNull()
            }

            if (chosenJson == null) {
                return@withContext UpdateCheckOutcome.UpToDate(
                    installedVersion = installedLabel,
                    latestTag = null,
                    message = "No published GitHub Releases found in $slug yet.",
                    buildInProgressNote = checkRunningWorkflowNote(slug)
                )
            }

            val release = parseGitHubRelease(chosenJson, meta, lastAssetId)
            if (release.isUpdateAvailable) {
                UpdateCheckOutcome.UpdateAvailable(installedLabel, release)
            } else {
                val buildNote = checkRunningWorkflowNote(slug)
                val upToDateMsg = buildString {
                    append("Zero Play $installedLabel is up to date (Latest: ${release.resolvedVersionLabel}).")
                    if (!buildNote.isNullOrBlank()) {
                        append("\n$buildNote")
                    }
                }
                UpdateCheckOutcome.UpToDate(
                    installedVersion = installedLabel,
                    latestTag = release.resolvedVersionLabel,
                    message = upToDateMsg,
                    latestRelease = release,
                    buildInProgressNote = buildNote
                )
            }
        } catch (t: Throwable) {
            UpdateCheckOutcome.Error(t.message ?: "Unable to check GitHub releases")
        }
    }

    private fun checkRunningWorkflowNote(slug: String): String? {
        return runCatching {
            val (code, body) = httpGetString("https://api.github.com/repos/$slug/actions/runs?per_page=1")
            if (code !in 200..299 || body.isBlank()) return null
            val runs = JSONObject(body).optJSONArray("workflow_runs") ?: return null
            if (runs.length() == 0) return null
            val latestRun = runs.getJSONObject(0)
            val status = latestRun.optString("status", "")
            val conclusion = latestRun.optString("conclusion", "")
            when {
                status == "in_progress" || status == "queued" ->
                    "A new GitHub Actions APK build is currently running — tap Check again in ~2 min."
                status == "completed" && conclusion == "failure" ->
                    "The latest GitHub Actions build failed. Open GitHub Actions to view logs."
                else -> null
            }
        }.getOrNull()
    }

    private fun checkDirectFeedOrApk(
        url: String,
        meta: InstalledAppMeta,
        installedLabel: String,
        lastAssetId: Long
    ): UpdateCheckOutcome {
        return try {
            val cleanNoQuery = url.substringBefore('?').lowercase()
            if (cleanNoQuery.endsWith(".apk")) {
                val release = AppReleaseInfo(
                    tagName = "Latest APK",
                    title = "Direct APK Build",
                    releaseNotes = "Direct APK update package ready to download and install.",
                    apkDownloadUrl = url,
                    apkSizeBytes = 0L,
                    releasePageUrl = url,
                    isPrerelease = false,
                    isUpdateAvailable = true,
                    resolvedVersionLabel = "Direct APK"
                )
                return UpdateCheckOutcome.UpdateAvailable(installedLabel, release)
            }

            val (code, body) = httpGetString(url)
            if (code !in 200..299) {
                return UpdateCheckOutcome.Error("Update URL returned HTTP $code")
            }
            val json = JSONObject(body)
            val remoteName = json.optString("versionName", json.optString("tag_name", "1.0"))
            val remoteCode = json.optLong("versionCode", 0L)
            val apkUrl = json.optString("apkUrl", json.optString("downloadUrl", "")).takeIf { it.isNotBlank() }
            val notes = json.optString("notes", json.optString("body", "New version available."))
            val pageUrl = json.optString("releaseUrl", url)
            val publishedMs = parseIso8601UtcMillis(json.optString("publishedAt", ""))
            val newer = isReleaseNewer(
                tagName = remoteName,
                title = json.optString("title", remoteName),
                apkFileName = apkUrl?.substringAfterLast('/'),
                manifestVersionName = remoteName,
                manifestVersionCode = remoteCode,
                publishedAtMs = publishedMs,
                assetId = publishedMs,
                installedName = meta.versionName,
                installedCode = meta.versionCode,
                installedTimestampMs = meta.baselineTimestampMs,
                lastInstalledAssetId = lastAssetId
            )

            val label = if (remoteName.startsWith("v", ignoreCase = true)) remoteName else "v$remoteName"
            val info = AppReleaseInfo(
                tagName = label,
                title = json.optString("title", "Zero Play $remoteName").ifBlank { "Zero Play $remoteName" },
                releaseNotes = notes,
                apkDownloadUrl = apkUrl,
                apkSizeBytes = json.optLong("apkSizeBytes", 0L),
                releasePageUrl = pageUrl,
                isPrerelease = json.optBoolean("prerelease", false),
                isUpdateAvailable = newer,
                assetId = publishedMs,
                publishedAtMs = publishedMs,
                resolvedVersionLabel = label
            )
            if (newer) {
                UpdateCheckOutcome.UpdateAvailable(installedLabel, info)
            } else {
                UpdateCheckOutcome.UpToDate(
                    installedVersion = installedLabel,
                    latestTag = info.resolvedVersionLabel,
                    message = "Zero Play $installedLabel is up to date (Latest: ${info.resolvedVersionLabel}).",
                    latestRelease = info
                )
            }
        } catch (t: Throwable) {
            UpdateCheckOutcome.Error(t.message ?: "Failed to read update URL")
        }
    }

    private fun parseGitHubRelease(
        json: JSONObject,
        meta: InstalledAppMeta,
        lastAssetId: Long
    ): AppReleaseInfo {
        val tagName = json.optString("tag_name", "v1.0")
        val name = json.optString("name", tagName).ifBlank { tagName }
        val body = json.optString("body", "").ifBlank { "New Zero Play release published on GitHub." }
        val prerelease = json.optBoolean("prerelease", false)
        val htmlUrl = json.optString("html_url", "")
        val releasePublishedMs = parseIso8601UtcMillis(json.optString("published_at", ""))
        val assets = json.optJSONArray("assets")

        var apkUrl: String? = null
        var apkFileName: String? = null
        var apkSize = 0L
        var apkAssetId = 0L
        var apkUpdatedMs = 0L
        var versionJsonUrl: String? = null

        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val assetName = asset.optString("name", "")
                val lower = assetName.lowercase()
                if (lower == "version.json" || lower == "ota.json") {
                    versionJsonUrl = asset.optString("browser_download_url", "").takeIf { it.isNotBlank() }
                } else if (lower.endsWith(".apk")) {
                    val url = asset.optString("browser_download_url", "")
                    val size = asset.optLong("size", 0L)
                    val id = asset.optLong("id", 0L)
                    val updated = parseIso8601UtcMillis(asset.optString("updated_at", ""))
                    if (apkUrl == null || lower.contains("release") || lower.contains("zero")) {
                        apkUrl = url
                        apkFileName = assetName
                        apkSize = size
                        apkAssetId = id
                        apkUpdatedMs = updated
                    }
                }
            }
        }

        // Optional version.json manifest attached by CI gives exact versionName + versionCode
        var manifestVersionName: String? = null
        var manifestVersionCode = 0L
        if (!versionJsonUrl.isNullOrBlank()) {
            runCatching {
                val (mCode, mBody) = httpGetString(versionJsonUrl)
                if (mCode in 200..299 && mBody.trimStart().startsWith("{")) {
                    val mJson = JSONObject(mBody)
                    manifestVersionName = mJson.optString("versionName", "").takeIf { it.isNotBlank() }
                    manifestVersionCode = mJson.optLong("versionCode", 0L)
                }
            }
        }

        val effectivePublishedMs = maxOf(releasePublishedMs, apkUpdatedMs)
        val bestSegs = extractBestVersionSegments(manifestVersionName, name, tagName, apkFileName)
        val resolvedLabel = when {
            !manifestVersionName.isNullOrBlank() -> "v${manifestVersionName!!.removePrefix("v")}"
            bestSegs.size >= 2 -> "v${bestSegs.joinToString(".")}"
            else -> tagName
        }

        val isNewer = isReleaseNewer(
            tagName = tagName,
            title = name,
            apkFileName = apkFileName,
            manifestVersionName = manifestVersionName,
            manifestVersionCode = manifestVersionCode,
            publishedAtMs = effectivePublishedMs,
            assetId = apkAssetId,
            installedName = meta.versionName,
            installedCode = meta.versionCode,
            installedTimestampMs = meta.baselineTimestampMs,
            lastInstalledAssetId = lastAssetId
        )

        return AppReleaseInfo(
            tagName = tagName,
            title = name,
            releaseNotes = body,
            apkDownloadUrl = apkUrl,
            apkSizeBytes = apkSize,
            releasePageUrl = htmlUrl,
            isPrerelease = prerelease,
            isUpdateAvailable = isNewer,
            assetId = apkAssetId,
            publishedAtMs = effectivePublishedMs,
            resolvedVersionLabel = resolvedLabel
        )
    }

    /**
     * High-speed OTA APK downloader using OkHttp + parallel byte-range workers (6× 4 MB chunks)
     * with automatic fallback to fast 128 KB single-stream download.
     */
    suspend fun downloadApk(
        apkUrl: String,
        versionTag: String,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.cacheDir, "updates")
            updatesDir.mkdirs()
            val safeTag = versionTag.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val destFile = File(updatesDir, "ZeroPlay-$safeTag.apk")
            if (destFile.exists()) destFile.delete()

            // Probe for range support + final redirected CDN URL
            val probe = probeApkRange(apkUrl)
            val downloadedOk = if (probe != null && probe.supportsRanges && probe.totalBytes >= 1_500_000L) {
                downloadApkParallel(probe.finalUrl, destFile, probe.totalBytes, onProgress)
            } else {
                false
            }

            if (!downloadedOk) {
                if (destFile.exists()) destFile.delete()
                downloadApkSingleStream(apkUrl, destFile, onProgress)
            }

            // Validate APK ZIP magic header ('P', 'K')
            val isValidZip = destFile.length() > 1024L && destFile.inputStream().use { s ->
                val header = ByteArray(2)
                s.read(header) == 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
            }
            if (!isValidZip) {
                destFile.delete()
                return@withContext Result.failure(
                    IllegalArgumentException("Downloaded file is not a valid APK package.")
                )
            }

            onProgress(1f)
            Result.success(destFile)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private data class ApkRangeProbe(val finalUrl: String, val totalBytes: Long, val supportsRanges: Boolean)

    private fun probeApkRange(apkUrl: String): ApkRangeProbe? {
        return runCatching {
            val req = Request.Builder()
                .url(apkUrl)
                .header("User-Agent", "ZeroPlay-Updater/2.1")
                .header("Range", "bytes=0-0")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val finalUrl = resp.request.url.toString()
                if (resp.code == 206) {
                    val cr = resp.header("Content-Range").orEmpty()
                    val total = cr.substringAfter('/', "").trim().toLongOrNull() ?: -1L
                    if (total > 0L) return ApkRangeProbe(finalUrl, total, true)
                }
                null
            }
        }.getOrNull()
    }

    private suspend fun downloadApkParallel(
        finalUrl: String,
        destFile: File,
        totalBytes: Long,
        onProgress: (Float) -> Unit
    ): Boolean {
        return runCatching {
            val ranges = mutableListOf<LongRange>()
            val slice = minOf(OTA_SLICE_BYTES, maxOf(512L * 1024L, (totalBytes + OTA_PARALLEL_WORKERS - 1) / OTA_PARALLEL_WORKERS))
            var start = 0L
            while (start < totalBytes) {
                val end = minOf(totalBytes - 1L, start + slice - 1L)
                ranges += start..end
                start = end + 1L
            }

            val queue = ConcurrentLinkedQueue(ranges)
            val downloaded = AtomicLong(0L)
            val failed = AtomicBoolean(false)

            RandomAccessFile(destFile, "rw").use { raf ->
                raf.setLength(totalBytes)
                val channel = raf.channel
                coroutineScope {
                    val jobs = (0 until minOf(OTA_PARALLEL_WORKERS, ranges.size)).map {
                        async(Dispatchers.IO) {
                            val buf = ByteArray(OTA_BUFFER_BYTES)
                            while (!failed.get()) {
                                val range = queue.poll() ?: break
                                val req = Request.Builder()
                                    .url(finalUrl)
                                    .header("User-Agent", "ZeroPlay-Updater/2.1")
                                    .header("Range", "bytes=${range.first}-${range.last}")
                                    .build()
                                httpClient.newCall(req).execute().use { resp ->
                                    if (resp.code != 206 && resp.code != 200) {
                                        failed.set(true)
                                        error("HTTP ${resp.code}")
                                    }
                                    val body = resp.body ?: error("Empty APK range")
                                    var pos = range.first
                                    body.byteStream().use { input ->
                                        while (!failed.get()) {
                                            val read = input.read(buf)
                                            if (read <= 0) break
                                            val bb = ByteBuffer.wrap(buf, 0, read)
                                            while (bb.hasRemaining()) {
                                                pos += channel.write(bb, pos)
                                            }
                                            val done = downloaded.addAndGet(read.toLong())
                                            onProgress((done.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    jobs.awaitAll()
                }
            }
            !failed.get() && destFile.length() >= totalBytes
        }.getOrDefault(false)
    }

    private fun downloadApkSingleStream(
        apkUrl: String,
        destFile: File,
        onProgress: (Float) -> Unit
    ) {
        val req = Request.Builder()
            .url(apkUrl)
            .header("User-Agent", "ZeroPlay-Updater/2.1")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                error("Download failed (HTTP ${resp.code})")
            }
            val body = resp.body ?: error("Empty APK response")
            val totalBytes = body.contentLength().coerceAtLeast(1L)
            var downloadedBytes = 0L
            val buf = ByteArray(OTA_BUFFER_BYTES)
            body.byteStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        output.write(buf, 0, read)
                        downloadedBytes += read
                        onProgress((downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.99f))
                    }
                }
            }
        }
    }

    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun installApk(apkFile: File): Result<Unit> {
        return runCatching {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun parseGitHubSlug(raw: String): String? {
        val trimmed = raw.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
        val parts = trimmed.split("/").filter { it.isNotBlank() }
        return if (parts.size >= 2) "${parts[0]}/${parts[1]}" else null
    }

    private fun httpGetString(urlString: String): Pair<Int, String> {
        val req = Request.Builder()
            .url(urlString)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ZeroPlay-Updater/2.1")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            return resp.code to body
        }
    }
}
