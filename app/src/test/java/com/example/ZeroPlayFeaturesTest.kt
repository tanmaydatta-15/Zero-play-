package com.example

import com.example.data.local.EchoSettings
import com.example.data.repository.ArtistArtworkManager
import com.example.metadata.AudioMetadataEngine
import com.example.metadata.EditableAudioTags
import com.example.playback.AudioEffectsController
import com.example.playback.EqualizerPreset
import com.example.playback.EqualizerSpec
import com.example.updater.AppUpdateManager
import com.example.updater.VcdiffPatchApplier
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ZeroPlayFeaturesTest {

    @Test
    fun vcdiffPatchApplier_reconstructsTargetAndVerifiesSha256() {
        val tempDir = Files.createTempDirectory("vcdiff_test").toFile()
        try {
            val baseBytes = ("ZeroPlay Unified v2.1.1 APK binary payload header " + "A".repeat(4096)).toByteArray()
            val targetBytes = ("ZeroPlay Unified v2.1.1 APK binary payload header " + "A".repeat(2048) + "NEW_PATCHED_CONTENT_V2.2.0").toByteArray()

            val baseFile = File(tempDir, "base.apk").apply { writeBytes(baseBytes) }
            val expectedFile = File(tempDir, "expected.apk").apply { writeBytes(targetBytes) }
            val patchBytes = VcdiffPatchApplier.encodeSimpleTestPatch(baseBytes, targetBytes)
            val patchFile = File(tempDir, "update.patch").apply { writeBytes(patchBytes) }
            val outputFile = File(tempDir, "patched.apk")

            assertTrue("Delta patch should be much smaller than target APK", patchBytes.size < targetBytes.size)

            val result = VcdiffPatchApplier.applyPatch(baseFile, patchFile, outputFile)
            assertTrue("applyPatch should succeed: ${result.exceptionOrNull()}", result.isSuccess)
            assertArrayEquals(targetBytes, outputFile.readBytes())
            assertEquals(
                VcdiffPatchApplier.computeSha256(expectedFile),
                VcdiffPatchApplier.computeSha256(outputFile)
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun vcdiffPatchApplier_rejectsCorruptedPatch() {
        val tempDir = Files.createTempDirectory("vcdiff_corrupt").toFile()
        try {
            val baseFile = File(tempDir, "base.apk").apply { writeBytes(ByteArray(256) { 1 }) }
            val badPatch = File(tempDir, "bad.patch").apply { writeBytes("NOT_A_VCDIFF_PATCH".toByteArray()) }
            val outFile = File(tempDir, "out.apk")

            val result = VcdiffPatchApplier.applyPatch(baseFile, badPatch, outFile)
            assertTrue("Corrupted patch must fail cleanly", result.isFailure)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun appUpdateManager_deltaPatchEligibilityChecksBaseVersionAndAbi() {
        assertTrue(
            AppUpdateManager.shouldUseDeltaPatch(
                baseVersionCode = 10,
                installedVersionCode = 10,
                patchUrl = "https://github.com/tanmaydatta-15/Zero-play-/releases/download/v2.2.0/update-arm64.patch",
                supportedAbis = listOf("arm64-v8a", "armeabi-v7a")
            )
        )
        assertFalse(
            "Skipped version must fall back to full APK",
            AppUpdateManager.shouldUseDeltaPatch(
                baseVersionCode = 11,
                installedVersionCode = 10,
                patchUrl = "https://example.com/update.patch",
                supportedAbis = listOf("arm64-v8a")
            )
        )
        assertFalse(
            "Missing patch URL must fall back to full APK",
            AppUpdateManager.shouldUseDeltaPatch(
                baseVersionCode = 10,
                installedVersionCode = 10,
                patchUrl = null,
                supportedAbis = listOf("arm64-v8a")
            )
        )
        assertFalse(
            "Non-arm64 ABI must fall back to universal APK",
            AppUpdateManager.shouldUseDeltaPatch(
                baseVersionCode = 10,
                installedVersionCode = 10,
                patchUrl = "https://example.com/update.patch",
                supportedAbis = listOf("armeabi-v7a", "x86")
            )
        )
    }

    @Test
    fun equalizerPresetsAndInterpolation_coverTenBandsWithinRange() {
        assertEquals(10, EqualizerPreset.entries.size)
        assertEquals(10, EqualizerSpec.BAND_FREQS_HZ.size)
        assertEquals(10, EqualizerSpec.BAND_LABELS.size)

        for (preset in EqualizerPreset.entries) {
            assertEquals("Preset ${preset.label} must have 10 bands", 10, preset.bandLevelsDb.size)
            for (db in preset.bandLevelsDb) {
                assertTrue(db in EqualizerSpec.MIN_DB..EqualizerSpec.MAX_DB)
            }
        }

        val encoded = EqualizerSpec.encodeBandLevels(EqualizerPreset.ROCK.bandLevelsDb)
        val decoded = EqualizerSpec.decodeBandLevels(encoded)
        assertEquals(EqualizerPreset.ROCK.bandLevelsDb, decoded)

        // Log-frequency interpolation at exact 1 kHz band should match 1 kHz dB * 100 mB
        val bands = listOf(0f, 0f, 0f, 0f, 0f, 6f, 0f, 0f, 0f, 0f)
        val mb1k = AudioEffectsController.interpolateBandMillibels(
            targetFreqHz = 1000,
            uiBandsDb = bands,
            minLevelMb = -1500,
            maxLevelMb = 1500
        )
        assertEquals(600.toShort(), mb1k)
    }

    @Test
    fun audioMetadataEngine_buildsLosslessTagAndCoverArgsAndParsesLyrics() {
        val tags = EditableAudioTags(
            title = "Midnight Horizon",
            album = "Unified Sessions",
            artist = "Nova Pulse",
            albumArtist = "Nova Pulse",
            composer = "T. Datta",
            genre = "Synthwave",
            lyricist = "Nova",
            trackNumber = "3",
            discNumber = "1",
            year = "2026",
            comment = "Mastered in Zero Play",
            embeddedLyrics = "[00:05.50]First synced line\n[00:12.00]Second synced line"
        )

        val argsWithCover = AudioMetadataEngine.buildTagWriteArgs(
            inputPath = "/tmp/in.mp3",
            outputPath = "/tmp/out.mp3",
            tags = tags,
            coverImagePath = "/tmp/cover.jpg",
            removeArtwork = false,
            targetExtension = "mp3"
        )
        assertTrue(argsWithCover.contains("-c:a"))
        assertTrue(argsWithCover.contains("copy"))
        assertTrue(argsWithCover.contains("title=Midnight Horizon"))
        assertTrue(argsWithCover.contains("album_artist=Nova Pulse"))
        assertTrue(argsWithCover.contains("composer=T. Datta"))
        assertTrue(argsWithCover.contains("lyricist=Nova"))
        assertTrue(argsWithCover.contains("attached_pic"))

        val parsedLrc = AudioMetadataEngine.parseLyricsText(tags.embeddedLyrics, 180_000L)
        assertEquals(2, parsedLrc.size)
        assertEquals(5500L, parsedLrc[0].timeMs)
        assertEquals("First synced line", parsedLrc[0].text)
        assertEquals(12000L, parsedLrc[1].timeMs)

        val parsedPlain = AudioMetadataEngine.parseLyricsText("Line A\nLine B\nLine C", 30_000L)
        assertEquals(3, parsedPlain.size)
        assertEquals("Line A", parsedPlain[0].text)
    }

    @Test
    fun artistArtworkManagerAndDefaultSettings_matchRequirements() {
        assertEquals("daft_punk", ArtistArtworkManager.sanitizeArtistFileName("Daft Punk"))
        assertEquals("ac_dc", ArtistArtworkManager.sanitizeArtistFileName("AC/DC"))
        assertTrue(ArtistArtworkManager.isPlaceholderArtist("<unknown>"))
        assertTrue(ArtistArtworkManager.isPlaceholderArtist("Unknown Artist"))
        assertFalse(ArtistArtworkManager.isPlaceholderArtist("Arijit Singh"))

        val defaults = EchoSettings()
        assertEquals("tanmaydatta-15/Zero-play-", defaults.updateRepoSlug)
        assertTrue(defaults.autoDownloadArtistImages)
    }
}
