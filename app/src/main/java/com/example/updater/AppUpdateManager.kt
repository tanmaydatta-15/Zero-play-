package com.example.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
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
import java.io.FileInputStream
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
    val resolvedVersionLabel: String = tagName,
    val baseVersionCode: Long = 0L,
    val patchDownloadUrl: String? = null,
    val patchSizeBytes: Long = 0L,
    val fullApkUrl: String? = apkDownloadUrl,
    val expectedSha256: String? = null,
    val isDeltaPatchApplicable: Boolean = false
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
        private const val TAG = "ZeroPlayUpdater"
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
         * Determines whether a binary VCDIFF delta patch can be applied against the installed APK.
         * Requires matching baseVersionCode, a non-blank patch URL, and an arm64-v8a device ABI.
         */
        internal fun shouldUseDeltaPatch(
            baseVersionCode: Long,
            installedVersionCode: Long,
            patchUrl: String?,
            supportedAbis: List<String>
        ): Boolean {
            if (baseVersionCode <= 0L || baseVersionCode != installedVersionCode) return false
            if (patchUrl.isNullOrBlank()) return false
            return supportedAbis.any { it.equals("arm64-v8a", ignoreCase = true) }
        }

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
                "Enter a public GitHub repository (e.g. tanmaydatta-15/Zero-play-) or a direct APK / version.json URL below."
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
            val supportsArm64 = Build.SUPPORTED_ABIS.any { it.equals("arm64-v8a", ignoreCase = true) }
            val fullApk = json.optString("fullApkUrl", "").takeIf { it.isNotBlank() }
                ?: json.optString("apkUrl", "").takeIf { it.isNotBlank() }
                ?: json.optString("downloadUrl", "").takeIf { it.isNotBlank() }
            val universalApk = json.optString("universalApkUrl", "").takeIf { it.isNotBlank() }
            val apkUrl = if (supportsArm64) (fullApk ?: universalApk) else (universalApk ?: fullApk)
            val baseVersionCode = json.optLong("baseVersionCode", 0L)
            val patchUrl = json.optString("patchUrl", "").takeIf { it.isNotBlank() }
            val patchSize = json.optLong("patchSize", 0L)
            val expectedSha256 = json.optString("sha256", "").takeIf { it.isNotBlank() }
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

            val useDelta = shouldUseDeltaPatch(
                baseVersionCode = baseVersionCode,
                installedVersionCode = meta.versionCode,
                patchUrl = patchUrl,
                supportedAbis = Build.SUPPORTED_ABIS.toList()
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
                resolvedVersionLabel = label,
                baseVersionCode = baseVersionCode,
                patchDownloadUrl = patchUrl,
                patchSizeBytes = patchSize,
                fullApkUrl = apkUrl,
                expectedSha256 = expectedSha256,
                isDeltaPatchApplicable = useDelta
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
        val supportsArm64 = Build.SUPPORTED_ABIS.any { it.equals("arm64-v8a", ignoreCase = true) }

        var arm64ApkUrl: String? = null
        var arm64ApkName: String? = null
        var arm64ApkSize = 0L
        var arm64AssetId = 0L
        var arm64UpdatedMs = 0L

        var universalApkUrl: String? = null
        var universalApkName: String? = null
        var universalApkSize = 0L
        var universalAssetId = 0L
        var universalUpdatedMs = 0L

        var fallbackApkUrl: String? = null
        var fallbackApkName: String? = null
        var fallbackApkSize = 0L
        var fallbackAssetId = 0L
        var fallbackUpdatedMs = 0L

        var assetPatchUrl: String? = null
        var assetPatchSize = 0L
        var versionJsonUrl: String? = null

        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val assetName = asset.optString("name", "")
                val lower = assetName.lowercase()
                val url = asset.optString("browser_download_url", "").takeIf { it.isNotBlank() }
                val size = asset.optLong("size", 0L)
                val id = asset.optLong("id", 0L)
                val updated = parseIso8601UtcMillis(asset.optString("updated_at", ""))

                when {
                    lower == "version.json" || lower == "ota.json" -> {
                        versionJsonUrl = url
                    }
                    lower.endsWith(".patch") && (lower.contains("arm64") || assetPatchUrl == null) -> {
                        assetPatchUrl = url
                        assetPatchSize = size
                    }
                    lower.endsWith(".apk") && url != null -> {
                        when {
                            lower.contains("arm64") -> {
                                arm64ApkUrl = url
                                arm64ApkName = assetName
                                arm64ApkSize = size
                                arm64AssetId = id
                                arm64UpdatedMs = updated
                            }
                            lower.contains("universal") -> {
                                universalApkUrl = url
                                universalApkName = assetName
                                universalApkSize = size
                                universalAssetId = id
                                universalUpdatedMs = updated
                            }
                            fallbackApkUrl == null || lower.contains("release") || lower.contains("zero") -> {
                                fallbackApkUrl = url
                                fallbackApkName = assetName
                                fallbackApkSize = size
                                fallbackAssetId = id
                                fallbackUpdatedMs = updated
                            }
                        }
                    }
                }
            }
        }

        // Choose best per-ABI APK for this device
        var apkUrl: String?
        var apkFileName: String?
        var apkSize: Long
        var apkAssetId: Long
        var apkUpdatedMs: Long

        if (supportsArm64 && arm64ApkUrl != null) {
            apkUrl = arm64ApkUrl
            apkFileName = arm64ApkName
            apkSize = arm64ApkSize
            apkAssetId = arm64AssetId
            apkUpdatedMs = arm64UpdatedMs
        } else if (universalApkUrl != null) {
            apkUrl = universalApkUrl
            apkFileName = universalApkName
            apkSize = universalApkSize
            apkAssetId = universalAssetId
            apkUpdatedMs = universalUpdatedMs
        } else {
            apkUrl = fallbackApkUrl ?: arm64ApkUrl
            apkFileName = fallbackApkName ?: arm64ApkName
            apkSize = if (fallbackApkUrl != null) fallbackApkSize else arm64ApkSize
            apkAssetId = if (fallbackApkUrl != null) fallbackAssetId else arm64AssetId
            apkUpdatedMs = if (fallbackApkUrl != null) fallbackUpdatedMs else arm64UpdatedMs
        }

        // Optional version.json manifest attached by CI gives exact versionName, versionCode, delta patch URL & SHA-256
        var manifestVersionName: String? = null
        var manifestVersionCode = 0L
        var baseVersionCode = 0L
        var manifestPatchUrl: String? = null
        var manifestPatchSize = 0L
        var manifestFullApkUrl: String? = null
        var manifestUniversalApkUrl: String? = null
        var manifestSha256: String? = null

        if (!versionJsonUrl.isNullOrBlank()) {
            runCatching {
                val (mCode, mBody) = httpGetString(versionJsonUrl)
                if (mCode in 200..299 && mBody.trimStart().startsWith("{")) {
                    val mJson = JSONObject(mBody)
                    manifestVersionName = mJson.optString("versionName", "").takeIf { it.isNotBlank() }
                    manifestVersionCode = mJson.optLong("versionCode", 0L)
                    baseVersionCode = mJson.optLong("baseVersionCode", 0L)
                    manifestPatchUrl = mJson.optString("patchUrl", "").takeIf { it.isNotBlank() }
                    manifestPatchSize = mJson.optLong("patchSize", 0L)
                    manifestFullApkUrl = mJson.optString("fullApkUrl", "").takeIf { it.isNotBlank() }
                        ?: mJson.optString("apkUrl", "").takeIf { it.isNotBlank() }
                    manifestUniversalApkUrl = mJson.optString("universalApkUrl", "").takeIf { it.isNotBlank() }
                    manifestSha256 = mJson.optString("sha256", "").takeIf { it.isNotBlank() }
                }
            }
        }

        val resolvedFullApkUrl = if (supportsArm64) {
            manifestFullApkUrl ?: apkUrl ?: manifestUniversalApkUrl
        } else {
            manifestUniversalApkUrl ?: apkUrl ?: manifestFullApkUrl
        }
        if (apkUrl == null) {
            apkUrl = resolvedFullApkUrl
        }

        val resolvedPatchUrl = manifestPatchUrl ?: assetPatchUrl
        val resolvedPatchSize = if (manifestPatchSize > 0L) manifestPatchSize else assetPatchSize
        val useDelta = shouldUseDeltaPatch(
            baseVersionCode = baseVersionCode,
            installedVersionCode = meta.versionCode,
            patchUrl = resolvedPatchUrl,
            supportedAbis = Build.SUPPORTED_ABIS.toList()
        )

        val effectivePublishedMs = maxOf(releasePublishedMs, apkUpdatedMs)
        val bestSegs = extractBestVersionSegments(manifestVersionName, name, tagName, apkFileName)
        val mName = manifestVersionName
        val resolvedLabel = when {
            !mName.isNullOrBlank() -> "v${mName.removePrefix("v")}"
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
            apkDownloadUrl = resolvedFullApkUrl,
            apkSizeBytes = apkSize,
            releasePageUrl = htmlUrl,
            isPrerelease = prerelease,
            isUpdateAvailable = isNewer,
            assetId = apkAssetId,
            publishedAtMs = effectivePublishedMs,
            resolvedVersionLabel = resolvedLabel,
            baseVersionCode = baseVersionCode,
            patchDownloadUrl = resolvedPatchUrl,
            patchSizeBytes = resolvedPatchSize,
            fullApkUrl = resolvedFullApkUrl,
            expectedSha256 = manifestSha256,
            isDeltaPatchApplicable = useDelta
        )
    }

    /**
     * Play Store-style OTA download & reconstruction pipeline:
     * 1. When baseVersionCode matches the installed APK on arm64-v8a, downloads only the small
     *    VCDIFF binary delta patch (~1-3 MB), reconstructs the new signed APK inside cacheDir
     *    against context.applicationInfo.sourceDir, and verifies its SHA-256 digest.
     * 2. Automatically falls back to downloading the full per-ABI APK if the user skipped a
     *    version, the base APK cannot be read, or patch/SHA-256 verification fails.
     */
    suspend fun downloadAndPrepareUpdate(
        release: AppReleaseInfo,
        onProgress: (Float) -> Unit,
        onStage: (String) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val safeTag = release.tagName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val patchDir = File(context.cacheDir, "updates").apply { mkdirs() }

        if (release.isDeltaPatchApplicable && !release.patchDownloadUrl.isNullOrBlank()) {
            val baseApk = runCatching { File(context.applicationInfo.sourceDir) }.getOrNull()
            if (baseApk != null && baseApk.exists() && baseApk.canRead()) {
                val patchFile = File(patchDir, "ZeroPlay-$safeTag.patch")
                val reconstructedApk = File(patchDir, "ZeroPlay-$safeTag-patched.apk")
                if (patchFile.exists()) patchFile.delete()
                if (reconstructedApk.exists()) reconstructedApk.delete()

                val deltaResult = runCatching {
                    onStage("Downloading delta patch...")
                    downloadApkSingleStream(release.patchDownloadUrl, patchFile) { p ->
                        onProgress((p * 0.72f).coerceIn(0f, 0.72f))
                    }

                    onStage("Applying binary delta patch...")
                    onProgress(0.76f)
                    VcdiffPatchApplier.applyPatch(
                        baseApk = baseApk,
                        patchFile = patchFile,
                        outputFile = reconstructedApk
                    ).getOrThrow()

                    onStage("Verifying reconstructed APK...")
                    onProgress(0.92f)
                    if (!isValidApkZip(reconstructedApk)) {
                        error("Reconstructed APK failed ZIP integrity check")
                    }
                    val expectedHash = release.expectedSha256?.trim().orEmpty()
                    if (expectedHash.isNotBlank()) {
                        val actualHash = VcdiffPatchApplier.computeSha256(reconstructedApk)
                        if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                            error("SHA-256 mismatch (expected $expectedHash, got $actualHash)")
                        }
                    }
                    patchFile.delete()
                    onProgress(1f)
                    reconstructedApk
                }

                if (deltaResult.isSuccess) {
                    return@withContext deltaResult
                } else {
                    Log.w(TAG, "Delta patch failed, falling back to full APK", deltaResult.exceptionOrNull())
                    patchFile.delete()
                    reconstructedApk.delete()
                    onStage("Patch verification failed — downloading full APK...")
                    onProgress(0f)
                }
            }
        }

        val fullUrl = release.fullApkUrl ?: release.apkDownloadUrl
        if (fullUrl.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("No downloadable APK URL in release"))
        }
        onStage("Downloading APK package...")
        val fullResult = downloadApk(fullUrl, release.tagName, onProgress)
        if (fullResult.isSuccess) {
            val file = fullResult.getOrThrow()
            val expectedHash = release.expectedSha256?.trim().orEmpty()
            val isArm64Build = fullUrl.contains("arm64", ignoreCase = true)
            if (expectedHash.isNotBlank() && isArm64Build) {
                onStage("Verifying SHA-256 signature...")
                val actualHash = VcdiffPatchApplier.computeSha256(file)
                if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                    file.delete()
                    return@withContext Result.failure(
                        SecurityException("Downloaded APK SHA-256 verification failed.")
                    )
                }
            }
        }
        fullResult
    }

    private fun isValidApkZip(file: File): Boolean {
        return file.length() > 1024L && runCatching {
            file.inputStream().use { s ->
                val header = ByteArray(2)
                s.read(header) == 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
            }
        }.getOrDefault(false)
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
        val sessionInstall = runCatching {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(apkFile.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                FileInputStream(apkFile).use { input ->
                    session.openWrite("zero_play_update.apk", 0L, apkFile.length()).use { output ->
                        input.copyTo(output, OTA_BUFFER_BYTES)
                        session.fsync(output)
                    }
                }
                val callbackIntent = Intent(context, PackageInstallResultReceiver::class.java).apply {
                    action = PackageInstallResultReceiver.ACTION_INSTALL_RESULT
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    callbackIntent,
                    flags
                )
                session.commit(pendingIntent.intentSender)
            }
        }
        if (sessionInstall.isSuccess) return sessionInstall

        Log.w(TAG, "PackageInstaller commit failed; falling back to ACTION_VIEW", sessionInstall.exceptionOrNull())
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
