package com.example.data.repository

import android.content.Context
import com.example.data.local.TrackRecordEntity
import com.example.data.model.ArtistChartItem
import com.example.data.model.LyricLine
import java.io.File

/**
 * Zero Play ships without bundled demo/placeholder songs or fake charts.
 * Cleans up any legacy synthesized seed WAV files left over from earlier versions.
 */
object MediaCatalogSeed {

    val trendingArtists: List<ArtistChartItem> = emptyList()

    fun getArtworkResForTrack(@Suppress("UNUSED_PARAMETER") trackId: String): Int? = null

    fun getLyricsForTrack(@Suppress("UNUSED_PARAMETER") trackId: String): List<LyricLine> = emptyList()

    fun ensureSeedTracksAndAudio(context: Context): List<TrackRecordEntity> {
        cleanupLegacySeedFiles(context)
        return emptyList()
    }

    fun cleanupLegacySeedFiles(context: Context) {
        runCatching {
            val synthDir = File(context.filesDir, "echo_synth_tracks")
            if (synthDir.exists()) {
                synthDir.deleteRecursively()
            }
        }
    }
}
