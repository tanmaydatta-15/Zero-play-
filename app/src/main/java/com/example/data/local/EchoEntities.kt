package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track_records")
data class TrackRecordEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uriString: String,
    val artworkResId: Int? = null,
    val artworkUriString: String? = null,
    val mediaType: String = "AUDIO",
    val source: String = "CURATED",
    val moodTag: String = "Feel good",
    val playCount: Int = 0,
    val isLiked: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = 0L,
    val mimeType: String = "audio/wav",
    val sizeBytes: Long = 0L,
    val albumArtist: String = "",
    val composer: String = "",
    val genre: String = "",
    val lyricist: String = "",
    val trackNumber: String = "",
    val discNumber: String = "",
    val year: String = "",
    val comment: String = "",
    val embeddedLyrics: String = "",
    val dateModified: Long = 0L,
    val folderPath: String = ""
)

@Entity(tableName = "artist_artwork")
data class ArtistArtworkEntity(
    @PrimaryKey val artistKey: String,
    val artistName: String,
    val localFilePath: String? = null,
    val source: String = "DEEZER",
    val lastAttemptMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val coverResId: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "conversion_jobs")
data class ConversionJobEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: String,
    val detailLabel: String,
    val progress: Float,
    val status: String,
    val inputUriString: String? = null,
    val outputUriString: String? = null,
    val sizeBeforeBytes: Long = 0L,
    val sizeAfterBytes: Long = 0L,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
