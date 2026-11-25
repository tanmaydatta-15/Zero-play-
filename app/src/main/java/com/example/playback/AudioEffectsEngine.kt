package com.example.playback

import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import kotlin.math.ln
import kotlin.math.roundToInt

data class EqualizerBandSpec(
    val frequencyHz: Int,
    val label: String
)

val EQUALIZER_BAND_SPECS: List<EqualizerBandSpec> = listOf(
    EqualizerBandSpec(32, "32 Hz"),
    EqualizerBandSpec(64, "64 Hz"),
    EqualizerBandSpec(125, "125 Hz"),
    EqualizerBandSpec(250, "250 Hz"),
    EqualizerBandSpec(500, "500 Hz"),
    EqualizerBandSpec(1_000, "1 kHz"),
    EqualizerBandSpec(2_000, "2 kHz"),
    EqualizerBandSpec(4_000, "4 kHz"),
    EqualizerBandSpec(8_000, "8 kHz"),
    EqualizerBandSpec(16_000, "16 kHz")
)

const val EQUALIZER_MIN_DB: Float = -12f
const val EQUALIZER_MAX_DB: Float = 12f

enum class EqualizerPreset(
    val displayName: String,
    val bandLevelsDb: List<Float>
) {
    FLAT("Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    ACOUSTIC("Acoustic", listOf(4f, 4f, 3f, 1f, 2f, 2f, 3f, 4f, 3f, 2f)),
    BASS_BOOSTER("Bass Booster", listOf(6f, 5.5f, 4.5f, 2.5f, 1f, 0f, 0f, 0f, 0f, 0f)),
    BASS_REDUCER("Bass Reducer", listOf(-6f, -5.5f, -4.5f, -2.5f, -1f, 0f, 0f, 0f, 0f, 0f)),
    CLASSICAL("Classical", listOf(4.5f, 3.5f, 2.5f, 2f, -1f, -1f, 0f, 2f, 3.5f, 4f)),
    DANCE("Dance", listOf(5f, 6f, 4.5f, 1f, 0f, 2f, 4f, 4.5f, 4f, 2.5f)),
    POP("Pop", listOf(-1.5f, 1.5f, 3.5f, 4.5f, 3.5f, 1f, -1f, 1.5f, 3f, 3.5f)),
    ROCK("Rock", listOf(5f, 4f, 2.5f, 1f, -1.5f, -1f, 1.5f, 3.5f, 4.5f, 5f)),
    VOCAL_BOOSTER("Vocal Booster", listOf(-2f, -1.5f, 0f, 2.5f, 4.5f, 5f, 4f, 2.5f, 0.5f, -1f)),
    CUSTOM("Custom", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f));

    companion object {
        fun fromName(raw: String?): EqualizerPreset =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: FLAT
    }
}

data class EqualizerState(
    val enabled: Boolean = false,
    val preset: EqualizerPreset = EqualizerPreset.FLAT,
    val bandLevelsDb: List<Float> = EqualizerPreset.FLAT.bandLevelsDb,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Int = 0, // 0..1000
    val surroundEnabled: Boolean = false,
    val surroundStrength: Int = 0 // 0..1000
) {
    fun normalizedBands(): List<Float> {
        return List(EQUALIZER_BAND_SPECS.size) { index ->
            bandLevelsDb.getOrElse(index) { 0f }.coerceIn(EQUALIZER_MIN_DB, EQUALIZER_MAX_DB)
        }
    }
}

object EqualizerSerializer {
    fun encodeBands(levelsDb: List<Float>): String {
        val normalized = List(EQUALIZER_BAND_SPECS.size) { idx ->
            levelsDb.getOrElse(idx) { 0f }.coerceIn(EQUALIZER_MIN_DB, EQUALIZER_MAX_DB)
        }
        return normalized.joinToString(",") { value ->
            val rounded = (value * 10f).roundToInt() / 10f
            rounded.toString()
        }
    }

    fun decodeBands(raw: String?): List<Float> {
        if (raw.isNullOrBlank()) return EqualizerPreset.FLAT.bandLevelsDb
        val parsed = raw.split(",").mapNotNull { it.trim().toFloatOrNull() }
        return List(EQUALIZER_BAND_SPECS.size) { idx ->
            parsed.getOrElse(idx) { 0f }.coerceIn(EQUALIZER_MIN_DB, EQUALIZER_MAX_DB)
        }
    }
}

/**
 * Maps 10 virtual UI bands (32 Hz .. 16 kHz, in dB) onto the device hardware
 * Equalizer band center frequencies (in milliHertz) using log-frequency interpolation.
 */
fun mapTenBandsToDeviceBands(
    tenBandLevelsDb: List<Float>,
    deviceCenterFreqsMilliHz: List<Int>,
    minLevelMb: Short,
    maxLevelMb: Short
): List<Short> {
    if (deviceCenterFreqsMilliHz.isEmpty()) return emptyList()
    val safeLevels = List(EQUALIZER_BAND_SPECS.size) { idx ->
        tenBandLevelsDb.getOrElse(idx) { 0f }.coerceIn(EQUALIZER_MIN_DB, EQUALIZER_MAX_DB)
    }
    val logSpecFreqs = EQUALIZER_BAND_SPECS.map { ln(it.frequencyHz.toDouble()) }
    val minMb = minLevelMb.toInt()
    val maxMb = maxLevelMb.toInt()

    return deviceCenterFreqsMilliHz.map { centerMilliHz ->
        val freqHz = (centerMilliHz / 1000.0).coerceAtLeast(1.0)
        val logFreq = ln(freqHz)

        val interpolatedDb: Double = when {
            logFreq <= logSpecFreqs.first() -> safeLevels.first().toDouble()
            logFreq >= logSpecFreqs.last() -> safeLevels.last().toDouble()
            else -> {
                var upperIdx = 1
                while (upperIdx < logSpecFreqs.size - 1 && logSpecFreqs[upperIdx] < logFreq) {
                    upperIdx++
                }
                val lowerIdx = upperIdx - 1
                val lowF = logSpecFreqs[lowerIdx]
                val highF = logSpecFreqs[upperIdx]
                val fraction = if (highF > lowF) ((logFreq - lowF) / (highF - lowF)).coerceIn(0.0, 1.0) else 0.0
                safeLevels[lowerIdx] * (1.0 - fraction) + safeLevels[upperIdx] * fraction
            }
        }

        // Convert dB (-12..+12) to millibels (-1200..+1200) and clamp to device range
        val targetMb = (interpolatedDb * 100.0).roundToInt().coerceIn(minMb, maxMb)
        targetMb.toShort()
    }
}

/**
 * Manages Android's native Equalizer, BassBoost, and Virtualizer AudioEffects attached
 * to the active Media3 ExoPlayer audio session ID.
 */
class AudioEffectsController {
    private var attachedSessionId: Int = AudioManager.AUDIO_SESSION_ID_GENERATE
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var currentState: EqualizerState = EqualizerState()

    @Synchronized
    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == AudioManager.AUDIO_SESSION_ID_GENERATE) {
            return
        }
        if (attachedSessionId == audioSessionId && equalizer != null) {
            applyStateLocked()
            return
        }
        releaseEffectsOnly()
        attachedSessionId = audioSessionId

        equalizer = runCatching { Equalizer(0, audioSessionId) }
            .onFailure { Log.w(TAG, "Unable to attach Equalizer to session $audioSessionId", it) }
            .getOrNull()

        bassBoost = runCatching { BassBoost(0, audioSessionId) }
            .onFailure { Log.w(TAG, "Unable to attach BassBoost to session $audioSessionId", it) }
            .getOrNull()

        virtualizer = runCatching { Virtualizer(0, audioSessionId) }
            .onFailure { Log.w(TAG, "Unable to attach Virtualizer to session $audioSessionId", it) }
            .getOrNull()

        applyStateLocked()
    }

    @Synchronized
    fun applyState(state: EqualizerState) {
        currentState = state
        applyStateLocked()
    }

    @Synchronized
    fun release() {
        releaseEffectsOnly()
        attachedSessionId = AudioManager.AUDIO_SESSION_ID_GENERATE
    }

    private fun releaseEffectsOnly() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
    }

    private fun applyStateLocked() {
        val state = currentState
        val masterEnabled = state.enabled

        equalizer?.let { eq ->
            runCatching {
                eq.enabled = masterEnabled
                val bandCount = eq.numberOfBands.toInt().coerceAtLeast(0)
                if (bandCount > 0) {
                    val range = eq.bandLevelRange
                    val minMb = range.getOrNull(0) ?: (-1200).toShort()
                    val maxMb = range.getOrNull(1) ?: 1200.toShort()
                    val centerFreqs = List(bandCount) { band -> eq.getCenterFreq(band.toShort()) }
                    val mappedLevels = mapTenBandsToDeviceBands(
                        tenBandLevelsDb = state.normalizedBands(),
                        deviceCenterFreqsMilliHz = centerFreqs,
                        minLevelMb = minMb,
                        maxLevelMb = maxMb
                    )
                    mappedLevels.forEachIndexed { idx, levelMb ->
                        eq.setBandLevel(idx.toShort(), levelMb)
                    }
                }
            }.onFailure {
                Log.w(TAG, "Failed applying Equalizer bands", it)
            }
        }

        bassBoost?.let { bb ->
            runCatching {
                val active = masterEnabled && state.bassBoostEnabled
                bb.enabled = active
                if (bb.strengthSupported) {
                    val strength = if (active) state.bassBoostStrength.coerceIn(0, 1000).toShort() else 0.toShort()
                    bb.setStrength(strength)
                }
            }.onFailure {
                Log.w(TAG, "Failed applying BassBoost", it)
            }
        }

        virtualizer?.let { virt ->
            runCatching {
                val active = masterEnabled && state.surroundEnabled
                virt.enabled = active
                if (virt.strengthSupported) {
                    val strength = if (active) state.surroundStrength.coerceIn(0, 1000).toShort() else 0.toShort()
                    virt.setStrength(strength)
                }
            }.onFailure {
                Log.w(TAG, "Failed applying Virtualizer", it)
            }
        }
    }

    companion object {
        private const val TAG = "AudioEffectsController"
    }
}
