package com.example.diagnostics

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import androidx.core.content.pm.PackageInfoCompat
import com.example.BuildConfig
import com.example.data.local.VideoCodecPreference
import java.io.File
import kotlin.math.roundToInt

enum class CodecSupportLevel(val label: String) {
    HARDWARE("Hardware"),
    SOFTWARE_ONLY("Software only"),
    UNAVAILABLE("Unavailable")
}

data class CodecCapability(
    val displayName: String,
    val shortBadge: String,
    val mimeType: String,
    val isAudio: Boolean,
    val decodeSupport: CodecSupportLevel,
    val encodeSupport: CodecSupportLevel,
    val primaryDecoderName: String? = null,
    val media3ExtensionNote: String? = null,
    val hardwareDecoders: List<String> = emptyList(),
    val softwareDecoders: List<String> = emptyList(),
    val maxResolutionLabel: String? = null,
    val maxFrameRate: Int? = null
)

data class DeviceDiagnosticsReport(
    val deviceModel: String,
    val manufacturer: String,
    val androidVersion: String,
    val buildNumber: String,
    val kernelVersion: String,
    val supportedAbis: String,
    val displayResolution: String,
    val displayDensityDpi: Int,
    val refreshRateHz: Int,
    val hdrCapabilities: String,
    val codecs: List<CodecCapability>,
    val media3Av1ExtensionAvailable: Boolean,
    val appVersionLabel: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
    val appBuildType: String = BuildConfig.BUILD_TYPE,
    val installedFrom: String = "Direct install",
    val packageName: String = BuildConfig.APPLICATION_ID
) {
    val videoCodecs: List<CodecCapability>
        get() = codecs.filter { !it.isAudio }

    val audioCodecs: List<CodecCapability>
        get() = codecs.filter { it.isAudio }

    val hardwareVideoDecoderCount: Int
        get() = videoCodecs.count { it.decodeSupport == CodecSupportLevel.HARDWARE }

    val av1Capability: CodecCapability?
        get() = codecs.firstOrNull { it.mimeType == "video/av01" }

    val isAv1SoftwareOnly: Boolean
        get() = av1Capability?.decodeSupport != CodecSupportLevel.HARDWARE

    fun recommendedAutoVideoCodec(): VideoCodecPreference {
        val hwMap = codecs.associate { it.mimeType to it.decodeSupport }
        return when {
            hwMap["video/av01"] == CodecSupportLevel.HARDWARE -> VideoCodecPreference.AV1
            hwMap["video/hevc"] == CodecSupportLevel.HARDWARE -> VideoCodecPreference.HEVC
            hwMap["video/x-vnd.on2.vp9"] == CodecSupportLevel.HARDWARE -> VideoCodecPreference.VP9
            else -> VideoCodecPreference.H264
        }
    }

    fun toClipboardSummary(): String = buildString {
        appendLine("Zero Play — Device & Codec Diagnostics")
        appendLine("Device: $deviceModel")
        appendLine("Android: $androidVersion")
        appendLine("Build: $buildNumber")
        appendLine("Kernel: $kernelVersion")
        appendLine("CPU ABIs: $supportedAbis")
        appendLine()
        appendLine("Display:")
        appendLine("  Resolution: $displayResolution")
        appendLine("  Density: $displayDensityDpi dpi")
        appendLine("  Refresh rate: $refreshRateHz Hz")
        appendLine("  HDR: $hdrCapabilities")
        appendLine()
        appendLine("Video decoding:")
        videoCodecs.forEach { codec ->
            appendLine("  ${codec.shortBadge}: ${codec.decodeSupport.label}")
        }
        appendLine("  Media3 dav1d extension: ${if (media3Av1ExtensionAvailable) "Active" else "Inactive"}")
        appendLine()
        appendLine("Audio decoding:")
        audioCodecs.forEach { codec ->
            val driver = codec.primaryDecoderName?.let { " ($it)" }.orEmpty()
            appendLine("  ${codec.shortBadge} [${codec.mimeType}]: ${codec.decodeSupport.label}$driver")
        }
        appendLine()
        appendLine("App:")
        appendLine("  Version: $appVersionLabel")
        appendLine("  Build type: $appBuildType")
        appendLine("  Installed from: $installedFrom")
        appendLine("  Package: $packageName")
    }.trim()
}

object DeviceCapabilityInspector {

    private data class TargetCodecSpec(
        val displayName: String,
        val shortBadge: String,
        val mimeType: String,
        val isAudio: Boolean
    )

    private val inspectedCodecs = listOf(
        TargetCodecSpec("AV1 (AOMedia)", "AV1", "video/av01", isAudio = false),
        TargetCodecSpec("VP9", "VP9", "video/x-vnd.on2.vp9", isAudio = false),
        TargetCodecSpec("HEVC / H.265", "HEVC", "video/hevc", isAudio = false),
        TargetCodecSpec("H.264 / AVC", "H.264", "video/avc", isAudio = false),
        TargetCodecSpec("AOMedia IAMF", "IAMF", "audio/iamf", isAudio = true),
        TargetCodecSpec("Opus", "OPUS", "audio/opus", isAudio = true)
    )

    fun isMedia3Av1DecoderPresent(): Boolean {
        val candidates = listOf(
            "io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory",
            "androidx.media3.decoder.av1.Libdav1dVideoRenderer",
            "androidx.media3.decoder.av1.Libgav1VideoRenderer"
        )
        return candidates.any { className ->
            runCatching {
                Class.forName(className)
                true
            }.getOrDefault(false)
        }
    }

    fun isMedia3IamfDecoderPresent(): Boolean {
        val candidates = listOf(
            "androidx.media3.decoder.iamf.IamfAudioRenderer",
            "androidx.media3.decoder.iamf.IamfLibrary"
        )
        return candidates.any { className ->
            runCatching {
                Class.forName(className)
                true
            }.getOrDefault(false)
        }
    }

    fun inspect(context: Context): DeviceDiagnosticsReport {
        val allCodecs = runCatching {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.toList()
        }.getOrDefault(emptyList())

        val media3Av1Present = isMedia3Av1DecoderPresent()
        val media3IamfPresent = isMedia3IamfDecoderPresent()

        val codecReports = inspectedCodecs.map { spec ->
            val matchingDecoders = allCodecs.filter { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(spec.mimeType, ignoreCase = true) }
            }
            val matchingEncoders = allCodecs.filter { info ->
                info.isEncoder && info.supportedTypes.any { it.equals(spec.mimeType, ignoreCase = true) }
            }

            val hwDecoders = matchingDecoders.filter { isHardwareAccelerated(it) }
            val swDecoders = matchingDecoders.filter { !isHardwareAccelerated(it) }
            val hwDecoder = hwDecoders.firstOrNull()
            val swDecoder = swDecoders.firstOrNull()

            val decodeLevel = when {
                hwDecoder != null -> CodecSupportLevel.HARDWARE
                swDecoder != null -> CodecSupportLevel.SOFTWARE_ONLY
                spec.mimeType == "video/av01" && media3Av1Present -> CodecSupportLevel.SOFTWARE_ONLY
                spec.mimeType == "audio/iamf" && media3IamfPresent -> CodecSupportLevel.SOFTWARE_ONLY
                else -> CodecSupportLevel.UNAVAILABLE
            }

            val hwEncoder = matchingEncoders.firstOrNull { isHardwareAccelerated(it) }
            val swEncoder = matchingEncoders.firstOrNull { !isHardwareAccelerated(it) }
            val encodeLevel = when {
                hwEncoder != null -> CodecSupportLevel.HARDWARE
                swEncoder != null -> CodecSupportLevel.SOFTWARE_ONLY
                else -> CodecSupportLevel.UNAVAILABLE
            }

            val primaryInfo = hwDecoder ?: swDecoder
            val (maxResLabel, maxFps) = if (!spec.isAudio && primaryInfo != null) {
                extractVideoCapabilities(primaryInfo, spec.mimeType)
            } else {
                null to null
            }

            val primaryDecoder = hwDecoder?.name
                ?: swDecoder?.name
                ?: when {
                    spec.mimeType == "video/av01" && media3Av1Present -> "NextLib / Media3 dav1d (SW)"
                    spec.mimeType == "audio/iamf" && media3IamfPresent -> "Media3 IamfAudioRenderer (SW)"
                    else -> null
                }

            val swNames = buildList {
                addAll(swDecoders.map { it.name }.distinct())
                if (spec.mimeType == "video/av01" && media3Av1Present) {
                    add("NextLib FFmpeg / dav1d (Media3)")
                }
                if (spec.mimeType == "audio/iamf" && media3IamfPresent) {
                    add("Media3 IamfAudioRenderer")
                }
            }

            val extNote = when {
                spec.mimeType == "video/av01" && decodeLevel != CodecSupportLevel.HARDWARE && media3Av1Present ->
                    "Accelerated via Media3 NextLib / dav1d software extension"
                spec.mimeType == "audio/iamf" && decodeLevel != CodecSupportLevel.HARDWARE && media3IamfPresent ->
                    "Decoded via Media3 IamfAudioRenderer software extension"
                else -> null
            }

            CodecCapability(
                displayName = spec.displayName,
                shortBadge = spec.shortBadge,
                mimeType = spec.mimeType,
                isAudio = spec.isAudio,
                decodeSupport = decodeLevel,
                encodeSupport = encodeLevel,
                primaryDecoderName = primaryDecoder,
                media3ExtensionNote = extNote,
                hardwareDecoders = hwDecoders.map { it.name }.distinct(),
                softwareDecoders = swNames,
                maxResolutionLabel = maxResLabel,
                maxFrameRate = maxFps
            )
        }

        val dm = context.resources.displayMetrics
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val defaultDisplay = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)

        val (widthPx, heightPx, refreshHz, hdrSummary) = extractDisplayDetails(defaultDisplay, dm.widthPixels, dm.heightPixels)

        val pkgInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val vName = pkgInfo?.versionName ?: BuildConfig.VERSION_NAME
        val vCode = pkgInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: BuildConfig.VERSION_CODE.toLong()
        val installer = resolveInstallerPackage(context)

        return DeviceDiagnosticsReport(
            deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim(),
            manufacturer = Build.MANUFACTURER,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            buildNumber = Build.DISPLAY.ifBlank { Build.ID },
            kernelVersion = readKernelVersion(),
            supportedAbis = Build.SUPPORTED_ABIS.joinToString(", ").ifBlank { "unknown" },
            displayResolution = "${widthPx} × ${heightPx}",
            displayDensityDpi = dm.densityDpi,
            refreshRateHz = refreshHz,
            hdrCapabilities = hdrSummary,
            codecs = codecReports,
            media3Av1ExtensionAvailable = media3Av1Present,
            appVersionLabel = "$vName ($vCode)",
            appBuildType = "github ${BuildConfig.BUILD_TYPE}",
            installedFrom = installer,
            packageName = context.packageName
        )
    }

    private fun resolveInstallerPackage(context: Context): String {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Direct APK / Sideload"
    }

    fun isAv1SoftwareOnly(context: Context): Boolean = inspect(context).isAv1SoftwareOnly

    fun recommendedAutoVideoCodec(context: Context): VideoCodecPreference =
        inspect(context).recommendedAutoVideoCodec()

    internal fun isHardwareAccelerated(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (info.isHardwareAccelerated) return true
            if (info.isSoftwareOnly) return false
        }
        val lower = info.name.lowercase()
        val isSoftwareName = lower.startsWith("omx.google.") ||
            lower.startsWith("c2.android.") ||
            lower.contains(".sw.") ||
            lower.contains("ffmpeg") ||
            lower.contains("dav1d") ||
            lower.contains("gav1")
        return !isSoftwareName
    }

    private fun extractVideoCapabilities(info: MediaCodecInfo, mimeType: String): Pair<String?, Int?> {
        return runCatching {
            val vCaps = info.getCapabilitiesForType(mimeType)?.videoCapabilities ?: return null to null
            val maxW = vCaps.supportedWidths.upper
            val maxH = vCaps.supportedHeights.upper
            val maxFps = vCaps.supportedFrameRates.upper
            val tier = when {
                maxW >= 7680 || maxH >= 4320 -> "8K"
                maxW >= 3840 || maxH >= 2160 -> "4K UHD"
                maxW >= 2560 || maxH >= 1440 -> "1440p QHD"
                maxW >= 1920 || maxH >= 1080 -> "1080p FHD"
                else -> "${maxW}×${maxH}"
            }
            val label = "$tier (${maxW}×${maxH})"
            label to maxFps.takeIf { it in 1..240 }
        }.getOrDefault(null to null)
    }

    private data class DisplayDetails(
        val width: Int,
        val height: Int,
        val refreshRateHz: Int,
        val hdrLabel: String
    )

    private fun extractDisplayDetails(
        display: Display?,
        fallbackWidth: Int,
        fallbackHeight: Int
    ): DisplayDetails {
        val mode = display?.mode
        val w = mode?.physicalWidth?.takeIf { it > 0 } ?: fallbackWidth
        val h = mode?.physicalHeight?.takeIf { it > 0 } ?: fallbackHeight
        val hz = (mode?.refreshRate ?: display?.refreshRate ?: 60f).roundToInt().coerceAtLeast(30)

        val hdrLabels = mutableListOf<String>()
        if (display != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            @Suppress("DEPRECATION")
            val supportedTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                display.mode?.supportedHdrTypes ?: display.hdrCapabilities?.supportedHdrTypes
            } else {
                display.hdrCapabilities?.supportedHdrTypes
            } ?: IntArray(0)

            supportedTypes.forEach { type ->
                when (type) {
                    Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> hdrLabels += "Dolby Vision"
                    Display.HdrCapabilities.HDR_TYPE_HDR10 -> hdrLabels += "HDR10"
                    Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> hdrLabels += "HDR10+"
                    Display.HdrCapabilities.HDR_TYPE_HLG -> hdrLabels += "HLG"
                }
            }
        }
        val hdrText = if (hdrLabels.isEmpty()) "SDR only (No HDR)" else hdrLabels.distinct().joinToString(" • ")
        return DisplayDetails(w, h, hz, hdrText)
    }

    private fun readKernelVersion(): String {
        val procVersion = runCatching {
            val f = File("/proc/version")
            if (f.exists() && f.canRead()) {
                f.readText().trim().lineSequence().firstOrNull()?.take(96)
            } else null
        }.getOrNull()
        return procVersion?.takeIf { it.isNotBlank() }
            ?: System.getProperty("os.version")?.takeIf { it.isNotBlank() }
            ?: "Linux"
    }
}
