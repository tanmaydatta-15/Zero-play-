package com.example.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Bundle
import android.util.Log
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * 10-band graphic equalizer frequencies (32 Hz through 16 kHz) with ±12 dB gain range.
 */
object EqualizerSpec {
    const val MIN_DB = -12f
    const val MAX_DB = 12f
    const val BAND_COUNT = 10

    val BAND_FREQS_HZ = listOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    val BAND_LABELS = listOf("32", "64", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

    fun clampDb(db: Float): Float = db.coerceIn(MIN_DB, MAX_DB)

    fun normalizeBands(raw: List<Float>): List<Float> =
        List(BAND_COUNT) { idx -> clampDb(raw.getOrElse(idx) { 0f }) }

    fun encodeBandLevels(levels: List<Float>): String =
        normalizeBands(levels).joinToString(",") { "%.1f".format(java.util.Locale.US, it) }

    fun decodeBandLevels(raw: String?): List<Float> {
        if (raw.isNullOrBlank()) return List(BAND_COUNT) { 0f }
        val parsed = raw.split(",").mapNotNull { it.trim().toFloatOrNull() }
        return normalizeBands(parsed)
    }
}

enum class EqualizerPreset(
    val label: String,
    val bandLevelsDb: List<Float>
) {
    FLAT("Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    ACOUSTIC("Acoustic", listOf(4f, 4f, 3f, 1f, 2f, 2f, 3f, 4f, 3f, 2f)),
    BASS_BOOSTER("Bass Booster", listOf(7f, 6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f)),
    BASS_REDUCER("Bass Reducer", listOf(-6f, -5f, -4f, -2f, -1f, 0f, 0f, 1f, 1f, 1f)),
    CLASSICAL("Classical", listOf(4f, 3f, 2f, 1f, -1f, -1f, 0f, 2f, 3f, 4f)),
    DANCE("Dance", listOf(5f, 6f, 4f, 1f, 0f, -1f, 2f, 4f, 5f, 4f)),
    POP("Pop", listOf(-1f, 1f, 3f, 4f, 3f, 1f, -1f, 1f, 2f, 3f)),
    ROCK("Rock", listOf(5f, 4f, 3f, 1f, -1f, 0f, 2f, 4f, 5f, 5f)),
    VOCAL_BOOSTER("Vocal Booster", listOf(-2f, -2f, -1f, 1f, 4f, 5f, 4f, 2f, 0f, -1f)),
    CUSTOM("Custom", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
}

data class EqualizerState(
    val enabled: Boolean = false,
    val preset: EqualizerPreset = EqualizerPreset.FLAT,
    val bandLevelsDb: List<Float> = EqualizerPreset.FLAT.bandLevelsDb,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Int = 0, // 0..1000
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 0 // 0..1000
) {
    fun toBundle(): Bundle = Bundle().apply {
        putBoolean(KEY_ENABLED, enabled)
        putString(KEY_PRESET, preset.name)
        putFloatArray(KEY_BANDS, EqualizerSpec.normalizeBands(bandLevelsDb).toFloatArray())
        putBoolean(KEY_BASS_ENABLED, bassBoostEnabled)
        putInt(KEY_BASS_STRENGTH, bassBoostStrength.coerceIn(0, 1000))
        putBoolean(KEY_VIRT_ENABLED, virtualizerEnabled)
        putInt(KEY_VIRT_STRENGTH, virtualizerStrength.coerceIn(0, 1000))
    }

    companion object {
        private const val KEY_ENABLED = "eq_enabled"
        private const val KEY_PRESET = "eq_preset"
        private const val KEY_BANDS = "eq_bands"
        private const val KEY_BASS_ENABLED = "eq_bass_enabled"
        private const val KEY_BASS_STRENGTH = "eq_bass_strength"
        private const val KEY_VIRT_ENABLED = "eq_virt_enabled"
        private const val KEY_VIRT_STRENGTH = "eq_virt_strength"

        fun fromBundle(bundle: Bundle): EqualizerState {
            val presetName = bundle.getString(KEY_PRESET, EqualizerPreset.FLAT.name)
            val preset = runCatching { EqualizerPreset.valueOf(presetName) }.getOrDefault(EqualizerPreset.FLAT)
            val bands = bundle.getFloatArray(KEY_BANDS)?.toList() ?: preset.bandLevelsDb
            return EqualizerState(
                enabled = bundle.getBoolean(KEY_ENABLED, false),
                preset = preset,
                bandLevelsDb = EqualizerSpec.normalizeBands(bands),
                bassBoostEnabled = bundle.getBoolean(KEY_BASS_ENABLED, false),
                bassBoostStrength = bundle.getInt(KEY_BASS_STRENGTH, 0).coerceIn(0, 1000),
                virtualizerEnabled = bundle.getBoolean(KEY_VIRT_ENABLED, false),
                virtualizerStrength = bundle.getInt(KEY_VIRT_STRENGTH, 0).coerceIn(0, 1000)
            )
        }
    }
}

/**
 * Manages Android DSP AudioEffects (Equalizer, BassBoost, Virtualizer) attached directly to
 * Media3 ExoPlayer's active audio session ID, interpolating the 10-band UI curve cleanly onto
 * whatever number of hardware EQ bands the device exposes.
 */
class AudioEffectsController {
    companion object {
        private const val TAG = "AudioEffectsController"

        /**
         * Interpolates a target frequency (in Hz) onto the 10-band UI curve in log-frequency space
         * and converts dB to millibels (1 dB = 100 mB), clamped to the hardware range.
         */
        internal fun interpolateBandMillibels(
            targetFreqHz: Int,
            uiBandsDb: List<Float>,
            minLevelMb: Short,
            maxLevelMb: Short
        ): Short {
            val normalized = EqualizerSpec.normalizeBands(uiBandsDb)
            val freqs = EqualizerSpec.BAND_FREQS_HZ
            val safeFreq = targetFreqHz.coerceAtLeast(1)

            val db = when {
                safeFreq <= freqs.first() -> normalized.first()
                safeFreq >= freqs.last() -> normalized.last()
                else -> {
                    var upperIdx = freqs.indexOfFirst { it >= safeFreq }.coerceAtLeast(1)
                    if (upperIdx < 1) upperIdx = 1
                    val lowerIdx = upperIdx - 1
                    val fLow = freqs[lowerIdx].toDouble()
                    val fHigh = freqs[upperIdx].toDouble()
                    val t = if (abs(fHigh - fLow) < 1e-6) {
                        0.0
                    } else {
                        ((ln(safeFreq.toDouble()) - ln(fLow)) / (ln(fHigh) - ln(fLow))).coerceIn(0.0, 1.0)
                    }
                    (normalized[lowerIdx] + (normalized[upperIdx] - normalized[lowerIdx]) * t.toFloat())
                }
            }

            val mb = (db * 100f).roundToInt()
            return mb.coerceIn(minLevelMb.toInt(), maxLevelMb.toInt()).toShort()
        }
    }

    private var attachedSessionId: Int = 0
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var currentState: EqualizerState = EqualizerState()

    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == attachedSessionId) {
            if (audioSessionId > 0) applyState(currentState)
            return
        }
        release()
        attachedSessionId = audioSessionId

        equalizer = runCatching { Equalizer(0, audioSessionId) }
            .onFailure { Log.w(TAG, "Hardware Equalizer unavailable on session $audioSessionId", it) }
            .getOrNull()

        bassBoost = runCatching { BassBoost(0, audioSessionId) }
            .onFailure { Log.w(TAG, "Hardware BassBoost unavailable on session $audioSessionId", it) }
            .getOrNull()

        @Suppress("DEPRECATION")
        virtualizer = runCatching { Virtualizer(0, audioSessionId) }
            .onFailure { Log.w(TAG, "Hardware Virtualizer unavailable on session $audioSessionId", it) }
            .getOrNull()

        applyState(currentState)
    }

    fun applyState(state: EqualizerState) {
        currentState = state
        val masterOn = state.enabled

        equalizer?.let { eq ->
            runCatching {
                eq.enabled = masterOn
                val bandCount = eq.numberOfBands.toInt()
                val range = eq.bandLevelRange
                val minMb = range.getOrElse(0) { (-1200).toShort() }
                val maxMb = range.getOrElse(1) { 1200.toShort() }
                for (band in 0 until bandCount) {
                    val centerHz = (eq.getCenterFreq(band.toShort()) / 1000).coerceAtLeast(20)
                    val levelMb = interpolateBandMillibels(
                        targetFreqHz = centerHz,
                        uiBandsDb = state.bandLevelsDb,
                        minLevelMb = minMb,
                        maxLevelMb = maxMb
                    )
                    eq.setBandLevel(band.toShort(), levelMb)
                }
            }.onFailure { Log.w(TAG, "Failed updating Equalizer bands", it) }
        }

        bassBoost?.let { bb ->
            runCatching {
                val active = masterOn && state.bassBoostEnabled
                bb.enabled = active
                if (bb.strengthSupported) {
                    val strength = if (active) state.bassBoostStrength.coerceIn(0, 1000).toShort() else 0.toShort()
                    bb.setStrength(strength)
                }
            }.onFailure { Log.w(TAG, "Failed updating BassBoost", it) }
        }

        @Suppress("DEPRECATION")
        virtualizer?.let { virt ->
            runCatching {
                val active = masterOn && state.virtualizerEnabled
                virt.enabled = active
                if (virt.strengthSupported) {
                    val strength = if (active) state.virtualizerStrength.coerceIn(0, 1000).toShort() else 0.toShort()
                    virt.setStrength(strength)
                }
            }.onFailure { Log.w(TAG, "Failed updating Virtualizer", it) }
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        @Suppress("DEPRECATION")
        runCatching { virtualizer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        attachedSessionId = 0
    }
}
