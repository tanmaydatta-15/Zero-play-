package com.example.downloader

import com.example.converter.FfmpegKitEngine
import com.example.data.local.SealAudioFormatPref
import com.example.data.local.SealPreferredVideoFormatPref
import com.example.data.local.VideoCodecPreference
import com.example.data.model.DownloadFormatPreset
import com.example.data.model.MediaTrack
import com.example.data.model.MediaType
import com.example.data.model.VideoContainerOption
import com.example.diagnostics.CodecCapability
import com.example.diagnostics.CodecSupportLevel
import com.example.diagnostics.DeviceDiagnosticsReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridMediaEngineTest {

    @Test
    fun `direct stream detector recognizes container and audio extensions`() {
        assertTrue(HybridExtractionRouter.isDirectStreamUrl("https://cdn.example.com/movie.mkv?token=abc"))
        assertTrue(HybridExtractionRouter.isDirectStreamUrl("https://cdn.example.com/track.opus"))
        assertTrue(HybridExtractionRouter.isDirectStreamUrl("https://cdn.example.com/song.mp3#t=10"))
        assertTrue(HybridExtractionRouter.isDirectStreamUrl("https://cdn.example.com/lossless.flac"))
        assertTrue(HybridExtractionRouter.isDirectStreamUrl("https://cdn.example.com/live/master.m3u8"))
        assertTrue(HybridExtractionRouter.isDirectStreamUrl("https://cdn.example.com/video.webm"))
        assertTrue(HybridExtractionRouter.isDirectStreamUrl("https://cdn.example.com/clip.mp4"))

        assertFalse(HybridExtractionRouter.isDirectStreamUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(HybridExtractionRouter.isDirectStreamUrl("https://www.instagram.com/reel/C123456789/"))
        assertFalse(HybridExtractionRouter.isDirectStreamUrl("https://soundcloud.com/artist/track"))
    }

    @Test
    fun `newpipe fast path matches supported platforms and skips instagram or tiktok`() {
        val engine = NewPipeEngine()
        assertTrue(engine.isFastPathUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(engine.isFastPathUrl("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(engine.isFastPathUrl("https://music.youtube.com/watch?v=abc123"))
        assertTrue(engine.isFastPathUrl("https://soundcloud.com/artist/song"))
        assertTrue(engine.isFastPathUrl("https://artist.bandcamp.com/track/demo"))

        assertFalse(engine.isFastPathUrl("https://www.instagram.com/reel/abc123/"))
        assertFalse(engine.isFastPathUrl("https://www.tiktok.com/@user/video/12345"))
        assertFalse(engine.isFastPathUrl("https://x.com/user/status/12345"))
    }

    @Test
    fun `ytdlp format selector encodes AV1 VP9 HEVC H264 and Opus selectors accurately`() {
        val av1Mkv = YtDlpEngine.buildFormatSelector(
            preset = DownloadFormatPreset.VIDEO_MKV_AV1,
            maxVideoHeightPx = 2160,
            preferredVideoFormat = SealPreferredVideoFormatPref.QUALITY,
            preferredCodec = VideoCodecPreference.AV1,
            container = VideoContainerOption.MKV,
            audioFormat = SealAudioFormatPref.OPUS
        )
        assertTrue(av1Mkv.contains("vcodec^=av01"))
        assertTrue(av1Mkv.contains("height<=2160"))
        assertTrue(av1Mkv.contains("acodec^=opus"))

        val hevcMp4 = YtDlpEngine.buildFormatSelector(
            preset = DownloadFormatPreset.VIDEO_MP4_HIGH,
            maxVideoHeightPx = 1080,
            preferredVideoFormat = SealPreferredVideoFormatPref.QUALITY,
            preferredCodec = VideoCodecPreference.HEVC,
            container = VideoContainerOption.MP4,
            audioFormat = SealAudioFormatPref.M4A
        )
        assertTrue(hevcMp4.contains("vcodec^=hev1"))
        assertTrue(hevcMp4.contains("acodec^=mp4a"))

        val vp9 = YtDlpEngine.buildFormatSelector(
            preset = DownloadFormatPreset.VIDEO_MKV_HIGH,
            maxVideoHeightPx = 1440,
            preferredVideoFormat = SealPreferredVideoFormatPref.QUALITY,
            preferredCodec = VideoCodecPreference.VP9,
            container = VideoContainerOption.MKV
        )
        assertTrue(vp9.contains("vcodec^=vp9") || vp9.contains("vcodec^=vp09"))

        val opusOnly = YtDlpEngine.buildFormatSelector(
            preset = DownloadFormatPreset.AUDIO_OPUS_HIGH,
            maxVideoHeightPx = 1080,
            preferredVideoFormat = SealPreferredVideoFormatPref.QUALITY,
            audioFormat = SealAudioFormatPref.OPUS
        )
        assertTrue(opusOnly.startsWith("bestaudio[acodec^=opus]"))
    }

    @Test
    fun `ffmpeg codec badge normalizer maps native codec strings to clean pills`() {
        assertEquals("AV1", FfmpegKitEngine.normalizeCodecBadge("av01.0.08M.08"))
        assertEquals("AV1", FfmpegKitEngine.normalizeCodecBadge("libsvtav1"))
        assertEquals("HEVC", FfmpegKitEngine.normalizeCodecBadge("hevc"))
        assertEquals("HEVC", FfmpegKitEngine.normalizeCodecBadge("hvc1"))
        assertEquals("VP9", FfmpegKitEngine.normalizeCodecBadge("vp09.00.41.08"))
        assertEquals("H.264", FfmpegKitEngine.normalizeCodecBadge("avc1.640028"))
        assertEquals("OPUS", FfmpegKitEngine.normalizeCodecBadge("opus"))
        assertEquals("MP3", FfmpegKitEngine.normalizeCodecBadge("libmp3lame"))
        assertEquals("FLAC", FfmpegKitEngine.normalizeCodecBadge("flac"))
    }

    @Test
    fun `media track format badges detect AV1 MKV Opus MP3 and FLAC`() {
        val av1Mkv = MediaTrack(
            id = "t1",
            title = "Demo Clip",
            artist = "Downloaded • AV1 • MKV",
            album = "Zero Play",
            durationMs = 60_000L,
            uriString = "file:///storage/emulated/0/Movies/ZeroPlay/demo.mkv",
            mediaType = MediaType.VIDEO,
            mimeType = "video/x-matroska"
        )
        assertEquals(listOf("AV1", "MKV"), av1Mkv.formatBadges)

        val opusTrack = MediaTrack(
            id = "t2",
            title = "Studio Audio",
            artist = "Downloaded • OPUS",
            album = "Zero Play",
            durationMs = 180_000L,
            uriString = "file:///storage/emulated/0/Music/ZeroPlay/song.opus",
            mediaType = MediaType.AUDIO,
            mimeType = "audio/opus"
        )
        assertEquals(listOf("OPUS"), opusTrack.formatBadges)

        val mp3Track = MediaTrack(
            id = "t3",
            title = "Portable Track",
            artist = "Exported Audio",
            album = "Zero Play",
            durationMs = 180_000L,
            uriString = "file:///storage/emulated/0/Music/ZeroPlay/song.mp3",
            mediaType = MediaType.AUDIO,
            mimeType = "audio/mpeg"
        )
        assertEquals(listOf("MP3"), mp3Track.formatBadges)
    }

    @Test
    fun `device diagnostics auto codec picks best hardware accelerated video codec`() {
        fun reportWith(
            av1: CodecSupportLevel,
            hevc: CodecSupportLevel,
            vp9: CodecSupportLevel,
            h264: CodecSupportLevel
        ) = DeviceDiagnosticsReport(
            deviceModel = "Test Device",
            manufacturer = "Test",
            androidVersion = "Android 15 (API 35)",
            buildNumber = "TEST.1",
            kernelVersion = "6.1",
            supportedAbis = "arm64-v8a",
            displayResolution = "1080 × 2400 px",
            displayDensityDpi = 420,
            refreshRateHz = 120,
            hdrCapabilities = "HDR10",
            codecs = listOf(
                CodecCapability("AV1", "AV1", "video/av01", false, av1, CodecSupportLevel.UNAVAILABLE),
                CodecCapability("HEVC", "HEVC", "video/hevc", false, hevc, CodecSupportLevel.HARDWARE),
                CodecCapability("VP9", "VP9", "video/x-vnd.on2.vp9", false, vp9, CodecSupportLevel.UNAVAILABLE),
                CodecCapability("H.264", "H.264", "video/avc", false, h264, CodecSupportLevel.HARDWARE)
            ),
            media3Av1ExtensionAvailable = true
        )

        val hwAv1 = reportWith(
            av1 = CodecSupportLevel.HARDWARE,
            hevc = CodecSupportLevel.HARDWARE,
            vp9 = CodecSupportLevel.HARDWARE,
            h264 = CodecSupportLevel.HARDWARE
        )
        assertFalse(hwAv1.isAv1SoftwareOnly)
        assertEquals(4, hwAv1.hardwareVideoDecoderCount)
        assertEquals(VideoCodecPreference.AV1, hwAv1.recommendedAutoVideoCodec())

        val swAv1HwHevc = reportWith(
            av1 = CodecSupportLevel.SOFTWARE_ONLY,
            hevc = CodecSupportLevel.HARDWARE,
            vp9 = CodecSupportLevel.HARDWARE,
            h264 = CodecSupportLevel.HARDWARE
        )
        assertTrue(swAv1HwHevc.isAv1SoftwareOnly)
        assertEquals(3, swAv1HwHevc.hardwareVideoDecoderCount)
        assertEquals(VideoCodecPreference.HEVC, swAv1HwHevc.recommendedAutoVideoCodec())

        val swOnlyFallback = reportWith(
            av1 = CodecSupportLevel.SOFTWARE_ONLY,
            hevc = CodecSupportLevel.SOFTWARE_ONLY,
            vp9 = CodecSupportLevel.SOFTWARE_ONLY,
            h264 = CodecSupportLevel.HARDWARE
        )
        assertEquals(1, swOnlyFallback.hardwareVideoDecoderCount)
        assertEquals(VideoCodecPreference.H264, swOnlyFallback.recommendedAutoVideoCodec())
    }

    @Test
    fun `parallel range chunk planner partitions file seamlessly with zero gaps`() {
        val totalSize = 50L * 1024L * 1024L // 50 MB
        val chunks = HybridExtractionRouter.planParallelChunks(totalSize)
        assertEquals(8, HybridExtractionRouter.PARALLEL_CONNECTIONS)
        assertTrue(chunks.size >= 13) // 50 MB / 4 MB slices = 13 chunks
        assertEquals(0L, chunks.first().first)
        assertEquals(totalSize - 1L, chunks.last().last)
        for (i in 1 until chunks.size) {
            assertEquals(chunks[i - 1].last + 1L, chunks[i].first)
        }
        val summed = chunks.sumOf { (it.last - it.first) + 1L }
        assertEquals(totalSize, summed)
    }

    @Test
    fun `ota updater detects newer version from release title even when tag is custom like Zero2`() {
        val detected = com.example.updater.AppUpdateManager.isReleaseNewer(
            tagName = "Zero2",
            title = "Zero play V.2.0.10",
            apkFileName = "app-debug.3.apk",
            manifestVersionName = null,
            manifestVersionCode = 0L,
            publishedAtMs = 1_700_000_000_000L,
            assetId = 999L,
            installedName = "2.0",
            installedCode = 2L,
            installedTimestampMs = 1_700_000_000_000L,
            lastInstalledAssetId = -1L
        )
        assertTrue(detected)

        // Monotonic GitHub Actions CI release with version.json
        val ciDetected = com.example.updater.AppUpdateManager.isReleaseNewer(
            tagName = "v2.1.0.5",
            title = "Zero Play v2.1.0.5",
            apkFileName = "ZeroPlay-v2.1.0.5.apk",
            manifestVersionName = "2.1.0.5",
            manifestVersionCode = 8L,
            publishedAtMs = 1_700_001_000_000L,
            assetId = 1001L,
            installedName = "2.1.0",
            installedCode = 3L,
            installedTimestampMs = 1_700_000_000_000L,
            lastInstalledAssetId = -1L
        )
        assertTrue(ciDetected)

        // Same version & already installed asset must not re-prompt
        val sameBuild = com.example.updater.AppUpdateManager.isReleaseNewer(
            tagName = "v2.1.0",
            title = "Zero Play v2.1.0",
            apkFileName = "ZeroPlay-v2.1.0.apk",
            manifestVersionName = "2.1.0",
            manifestVersionCode = 3L,
            publishedAtMs = 1_700_000_000_000L,
            assetId = 1001L,
            installedName = "2.1.0",
            installedCode = 3L,
            installedTimestampMs = 1_700_000_000_000L,
            lastInstalledAssetId = 1001L
        )
        assertFalse(sameBuild)

        // Newly uploaded APK with same custom tag detected via publish timestamp + new asset ID
        val newerTimestamp = com.example.updater.AppUpdateManager.isReleaseNewer(
            tagName = "Zero2",
            title = "Zero2",
            apkFileName = "app-debug.apk",
            manifestVersionName = null,
            manifestVersionCode = 0L,
            publishedAtMs = 1_700_001_000_000L,
            assetId = 2002L,
            installedName = "2.0",
            installedCode = 2L,
            installedTimestampMs = 1_700_000_000_000L,
            lastInstalledAssetId = 1001L
        )
        assertTrue(newerTimestamp)
    }

    @Test
    fun `bundled demo content is removed and nextlib av1 decoder is present`() {
        assertTrue(com.example.data.repository.MediaCatalogSeed.trendingArtists.isEmpty())
        val fallback = com.example.R.drawable.img_cover_midnight_1794995218900
        assertEquals(fallback, com.example.data.repository.EchoRepository.sanitizeDrawableId(0x7f05005d, fallback))
        assertEquals(
            com.example.R.drawable.img_cover_aurora_1794995218098,
            com.example.data.repository.EchoRepository.sanitizeDrawableId(
                com.example.R.drawable.img_cover_aurora_1794995218098,
                fallback
            )
        )
        assertTrue(com.example.diagnostics.DeviceCapabilityInspector.isMedia3Av1DecoderPresent())

        val summary = DeviceDiagnosticsReport(
            deviceModel = "OPPO CPH2733",
            manufacturer = "OPPO",
            androidVersion = "Android 15 (API 35)",
            buildNumber = "CPH2733_17.0.0.100",
            kernelVersion = "6.18.21",
            supportedAbis = "arm64-v8a",
            displayResolution = "720 × 1604",
            displayDensityDpi = 272,
            refreshRateHz = 60,
            hdrCapabilities = "SDR only (No HDR)",
            codecs = listOf(
                CodecCapability("AV1", "AV1", "video/av01", false, CodecSupportLevel.SOFTWARE_ONLY, CodecSupportLevel.UNAVAILABLE),
                CodecCapability("VP9", "VP9", "video/x-vnd.on2.vp9", false, CodecSupportLevel.HARDWARE, CodecSupportLevel.UNAVAILABLE),
                CodecCapability("HEVC", "HEVC", "video/hevc", false, CodecSupportLevel.HARDWARE, CodecSupportLevel.HARDWARE),
                CodecCapability("H.264", "H.264", "video/avc", false, CodecSupportLevel.HARDWARE, CodecSupportLevel.HARDWARE)
            ),
            media3Av1ExtensionAvailable = true,
            appVersionLabel = "2.1.0 (3)",
            appBuildType = "github debug",
            installedFrom = "com.google.android.apps.nbu.files",
            packageName = "com.aistudio.echomusic.kxmvqr"
        ).toClipboardSummary()
        assertTrue(summary.contains("AV1: Software only"))
        assertTrue(summary.contains("VP9: Hardware"))
        assertTrue(summary.contains("Version: 2.1.0 (3)"))
        assertTrue(summary.contains("Package: com.aistudio.echomusic.kxmvqr"))
    }

    @Test
    fun `ffmpeg mux args map both video and audio and encode AAC 192k for MP4`() {
        val video = java.io.File("/tmp/test_video.mp4")
        val audio = java.io.File("/tmp/test_audio.webm")
        val outputMp4 = java.io.File("/tmp/test_output.mp4")
        val outputMkv = java.io.File("/tmp/test_output.mkv")

        val mp4Args = FfmpegKitEngine.buildMuxArgs(
            videoFile = video,
            audioFile = audio,
            outputFile = outputMp4,
            container = VideoContainerOption.MP4
        )
        val mp4Cmd = mp4Args.joinToString(" ")
        assertTrue(mp4Cmd.contains("-map 0:v:0 -map 1:a:0"))
        assertTrue(mp4Cmd.contains("-c:v copy"))
        assertTrue(mp4Cmd.contains("-c:a aac -b:a 192k"))
        assertTrue(mp4Cmd.contains("-movflags +faststart"))

        val mkvArgs = FfmpegKitEngine.buildMuxArgs(
            videoFile = video,
            audioFile = audio,
            outputFile = outputMkv,
            container = VideoContainerOption.MKV
        )
        val mkvCmd = mkvArgs.joinToString(" ")
        assertTrue(mkvCmd.contains("-map 0:v:0 -map 1:a:0"))
        assertTrue(mkvCmd.contains("-c:v copy -c:a copy"))
    }

    @Test
    fun `ytdlp separate stream selector and default auto update repo are configured properly`() {
        val sepMp4 = YtDlpEngine.buildSeparateVideoAudioSelector(
            maxVideoHeightPx = 1080,
            preferredVideoFormat = SealPreferredVideoFormatPref.QUALITY,
            preferredCodec = VideoCodecPreference.H264,
            container = VideoContainerOption.MP4
        )
        val parts = sepMp4.split(",")
        assertEquals(2, parts.size)
        assertTrue(parts[0].contains("bv[height<=1080]"))
        assertTrue(parts[1].contains("ba[acodec^=mp4a]"))

        val mp4Selector = YtDlpEngine.buildFormatSelector(
            preset = DownloadFormatPreset.VIDEO_MP4_HIGH,
            maxVideoHeightPx = 1080,
            preferredVideoFormat = SealPreferredVideoFormatPref.QUALITY,
            preferredCodec = VideoCodecPreference.AUTO,
            container = VideoContainerOption.MP4
        )
        assertTrue(mp4Selector.contains("+(ba[acodec^=mp4a]/ba[ext=m4a]/ba)"))

        assertEquals("tanmaydatta-15/Zero-play-", com.example.data.local.EchoSettings().updateRepoSlug)
        assertEquals("tanmaydatta-15/Zero-play-", com.example.data.local.PreferencesManager.DEFAULT_UPDATE_REPO_SLUG)
    }
}
