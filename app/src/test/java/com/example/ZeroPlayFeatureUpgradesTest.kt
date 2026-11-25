package com.example

import android.net.Uri
import com.example.data.local.EchoSettings
import com.example.data.metadata.AudioMetadataManager
import com.example.data.metadata.CoverArtEditAction
import com.example.data.metadata.TagEditRequest
import com.example.data.repository.ArtistArtworkManager
import com.example.data.repository.EchoRepository
import com.example.playback.EQUALIZER_BAND_SPECS
import com.example.playback.EqualizerPreset
import com.example.playback.EqualizerSerializer
import com.example.playback.mapTenBandsToDeviceBands
import com.example.updater.ApkAssetVariant
import com.example.updater.AppUpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ZeroPlayFeatureUpgradesTest {

    @Test
    fun `default github update repo remains tanmaydatta-15 Zero-play-`() {
        val defaults = EchoSettings()
        assertEquals("tanmaydatta-15/Zero-play-", defaults.updateRepoSlug)
        assertTrue(defaults.autoDownloadArtistImages)
    }

    @Test
    fun `ota abi selection prefers matching arm64-v8a split apk over universal`() {
        val variants = listOf(
            ApkAssetVariant(
                abi = "universal",
                fileName = "ZeroPlay-v2.2.0-universal.apk",
                downloadUrl = "https://example.com/universal.apk",
                sizeBytes = 83L * 1024L * 1024L
            ),
            ApkAssetVariant(
                abi = "arm64-v8a",
                fileName = "ZeroPlay-v2.2.0-arm64-v8a.apk",
                downloadUrl = "https://example.com/arm64-v8a.apk",
                sizeBytes = 30L * 1024L * 1024L
            ),
            ApkAssetVariant(
                abi = "armeabi-v7a",
                fileName = "ZeroPlay-v2.2.0-armeabi-v7a.apk",
                downloadUrl = "https://example.com/armeabi-v7a.apk",
                sizeBytes = 28L * 1024L * 1024L
            )
        )

        val arm64Pick = AppUpdateManager.selectBestApkForAbi(variants, arrayOf("arm64-v8a", "armeabi-v7a"))
        assertEquals("arm64-v8a", arm64Pick?.abi)
        assertEquals("ZeroPlay-v2.2.0-arm64-v8a.apk", arm64Pick?.fileName)

        val arm32Pick = AppUpdateManager.selectBestApkForAbi(variants, arrayOf("armeabi-v7a"))
        assertEquals("armeabi-v7a", arm32Pick?.abi)

        val x86Fallback = AppUpdateManager.selectBestApkForAbi(variants, arrayOf("x86_64"))
        assertEquals("universal", x86Fallback?.abi)

        assertEquals("arm64-v8a", AppUpdateManager.inferAbiFromAssetName("app-arm64-v8a-release.apk"))
        assertEquals("armeabi-v7a", AppUpdateManager.inferAbiFromAssetName("ZeroPlay-v2.2-armeabi-v7a.apk"))
        assertEquals("universal", AppUpdateManager.inferAbiFromAssetName("ZeroPlay-v2.2-universal.apk"))
    }

    @Test
    fun `equalizer has 10 bands, 10 presets, serialization roundtrip, and hardware band interpolation`() {
        assertEquals(10, EQUALIZER_BAND_SPECS.size)
        assertEquals(10, EqualizerPreset.entries.size)
        EqualizerPreset.entries.forEach { preset ->
            assertEquals("Preset ${preset.displayName} must define 10 bands", 10, preset.bandLevelsDb.size)
        }

        val sampleBands = listOf(6f, 4.5f, 2f, 0f, -1.5f, -3f, 1f, 3.5f, 5f, 12f)
        val encoded = EqualizerSerializer.encodeBands(sampleBands)
        val decoded = EqualizerSerializer.decodeBands(encoded)
        assertEquals(sampleBands, decoded)

        // Typical 5-band Android hardware EQ (60 Hz, 230 Hz, 910 Hz, 3.6 kHz, 14 kHz in mHz)
        val hwFreqsMilliHz = listOf(60_000, 230_000, 910_000, 3_600_000, 14_000_000)
        val mapped = mapTenBandsToDeviceBands(
            tenBandLevelsDb = EqualizerPreset.BASS_BOOSTER.bandLevelsDb,
            deviceCenterFreqsMilliHz = hwFreqsMilliHz,
            minLevelMb = (-1500).toShort(),
            maxLevelMb = 1500.toShort()
        )
        assertEquals(5, mapped.size)
        assertTrue("Low band should be boosted for Bass Booster", mapped[0] > 400)
        assertEquals("High band should remain flat for Bass Booster", 0.toShort(), mapped[4])
    }

    @Test
    fun `audio metadata ffmpeg args map all 11 tags, lyrics, and cover art actions accurately`() {
        val input = File("/tmp/source.mp3")
        val output = File("/tmp/out.mp3")
        val cover = File("/tmp/cover.jpg")
        val baseRequest = TagEditRequest(
            title = "Midnight Echo",
            album = "Unified Sessions",
            artist = "Nova Pulse",
            albumArtist = "Nova Pulse Collective",
            composer = "A. Composer",
            genre = "Electronic",
            lyricist = "B. Writer",
            trackNumber = "3",
            discNumber = "1",
            year = "2026",
            comment = "Mastered in lossless",
            embeddedLyrics = "[00:05.00] First line\n[00:12.50] Second line",
            coverArtAction = CoverArtEditAction.Replace(Uri.parse("content://media/picker/1"))
        )

        val replaceArgs = AudioMetadataManager.buildTagWriteArgs(input, output, baseRequest, cover, "mp3")
        assertTrue(replaceArgs.contains("-id3v2_version"))
        assertTrue(replaceArgs.contains("attached_pic"))
        assertTrue(replaceArgs.contains("title=Midnight Echo"))
        assertTrue(replaceArgs.contains("album_artist=Nova Pulse Collective"))
        assertTrue(replaceArgs.contains("lyricist=B. Writer"))
        assertTrue(replaceArgs.contains("lyrics=[00:05.00] First line\n[00:12.50] Second line"))

        val removeArgs = AudioMetadataManager.buildTagWriteArgs(
            input,
            output,
            baseRequest.copy(coverArtAction = CoverArtEditAction.Remove),
            null,
            "mp3"
        )
        assertTrue(removeArgs.contains("0:a:0"))
        assertFalse(removeArgs.contains("attached_pic"))

        val keepArgs = AudioMetadataManager.buildTagWriteArgs(
            input,
            output,
            baseRequest.copy(coverArtAction = CoverArtEditAction.Keep),
            null,
            "m4a"
        )
        assertTrue(keepArgs.contains("+faststart"))
        assertTrue(keepArgs.contains("-c"))
        assertTrue(keepArgs.contains("copy"))
    }

    @Test
    fun `artist artwork manager sanitizes filenames, filters placeholders, and parses deezer portraits`() {
        assertEquals("daft_punk.jpg", ArtistArtworkManager.sanitizeArtistFileName("Daft Punk"))
        assertEquals("ac_dc_live.jpg", ArtistArtworkManager.sanitizeArtistFileName("AC/DC (Live)!"))

        assertTrue(ArtistArtworkManager.isFetchableArtistName("Daft Punk"))
        assertFalse(ArtistArtworkManager.isFetchableArtistName("Unknown Artist"))
        assertFalse(ArtistArtworkManager.isFetchableArtistName("<unknown>"))
        assertFalse(ArtistArtworkManager.isFetchableArtistName("Local Audio"))

        val deezerJson = """
            {
              "data": [
                {
                  "id": 27,
                  "name": "Daft Punk Tribute",
                  "picture_big": "https://cdn-images.dzcdn.net/images/artist/tribute/500x500.jpg"
                },
                {
                  "id": 28,
                  "name": "Daft Punk",
                  "picture_big": "https://cdn-images.dzcdn.net/images/artist/real/500x500.jpg"
                }
              ]
            }
        """.trimIndent()

        val portrait = ArtistArtworkManager.extractBestDeezerPortraitUrl(deezerJson, "Daft Punk")
        assertNotNull(portrait)
        assertEquals("https://cdn-images.dzcdn.net/images/artist/real/500x500.jpg", portrait)
        assertNull(ArtistArtworkManager.extractBestDeezerPortraitUrl("{}", "Daft Punk"))
    }

    @Test
    fun `embedded lyrics parser handles both LRC timestamps and plain text`() {
        val lrcLines = EchoRepository.parseEmbeddedLyrics("[00:04.50] Hello world\n[01:02.25] Next verse")
        assertEquals(2, lrcLines.size)
        assertEquals(4_500L, lrcLines[0].timeMs)
        assertEquals("Hello world", lrcLines[0].text)
        assertEquals(62_250L, lrcLines[1].timeMs)
        assertEquals("Next verse", lrcLines[1].text)

        val plainLines = EchoRepository.parseEmbeddedLyrics("Line one\nLine two")
        assertEquals(2, plainLines.size)
        assertEquals(0L, plainLines[0].timeMs)
        assertEquals("Line one", plainLines[0].text)
        assertEquals(4_000L, plainLines[1].timeMs)
        assertEquals("Line two", plainLines[1].text)
    }
}
