package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface EchoDao {

    @Query("SELECT * FROM track_records ORDER BY dateAdded DESC")
    fun observeAllTracks(): Flow<List<TrackRecordEntity>>

    @Query("SELECT * FROM track_records WHERE trackId = :trackId LIMIT 1")
    suspend fun getTrackById(trackId: String): TrackRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrack(track: TrackRecordEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTracksIfAbsent(tracks: List<TrackRecordEntity>)

    @Query("UPDATE track_records SET isLiked = :liked WHERE trackId = :trackId")
    suspend fun setLiked(trackId: String, liked: Boolean)

    @Query("UPDATE track_records SET playCount = playCount + 1, lastPlayedAt = :timestamp WHERE trackId = :trackId")
    suspend fun incrementPlayCount(trackId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE track_records SET source = :source, uriString = :uriString WHERE trackId = :trackId")
    suspend fun updateTrackSource(trackId: String, source: String, uriString: String)

    @Query(
        """
        UPDATE track_records SET
            title = :title,
            album = :album,
            artist = :artist,
            albumArtist = :albumArtist,
            composer = :composer,
            genre = :genre,
            lyricist = :lyricist,
            trackNumber = :trackNumber,
            discNumber = :discNumber,
            year = :year,
            comment = :comment,
            embeddedLyrics = :embeddedLyrics,
            artworkUriString = :artworkUriString,
            dateModified = :dateModified
        WHERE trackId = :trackId
        """
    )
    suspend fun updateTrackMetadata(
        trackId: String,
        title: String,
        album: String,
        artist: String,
        albumArtist: String,
        composer: String,
        genre: String,
        lyricist: String,
        trackNumber: String,
        discNumber: String,
        year: String,
        comment: String,
        embeddedLyrics: String,
        artworkUriString: String?,
        dateModified: Long
    )

    @Query("SELECT * FROM artist_artwork")
    fun observeArtistArtworks(): Flow<List<ArtistArtworkEntity>>

    @Query("SELECT * FROM artist_artwork WHERE artistKey = :artistKey LIMIT 1")
    suspend fun getArtistArtwork(artistKey: String): ArtistArtworkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtistArtwork(entity: ArtistArtworkEntity)

    @Query("DELETE FROM artist_artwork WHERE artistKey = :artistKey")
    suspend fun deleteArtistArtwork(artistKey: String)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, newName: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT * FROM playlist_tracks ORDER BY addedAt ASC")
    fun observePlaylistTrackRefs(): Flow<List<PlaylistTrackCrossRef>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrackToPlaylist(ref: PlaylistTrackCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrackRefs(refs: List<PlaylistTrackCrossRef>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deletePlaylistTrackRefs(playlistId: Long)

    @Transaction
    suspend fun replacePlaylistTracks(playlistId: Long, refs: List<PlaylistTrackCrossRef>) {
        deletePlaylistTrackRefs(playlistId)
        if (refs.isNotEmpty()) {
            insertPlaylistTrackRefs(refs)
        }
    }

    @Query("SELECT * FROM conversion_jobs ORDER BY createdAt DESC")
    fun observeConversionJobs(): Flow<List<ConversionJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversionJob(job: ConversionJobEntity)

    @Query("DELETE FROM conversion_jobs WHERE id = :jobId")
    suspend fun deleteConversionJob(jobId: String)

    @Query("DELETE FROM conversion_jobs WHERE status IN ('COMPLETED', 'FAILED')")
    suspend fun deleteFinishedConversionJobs()

    @Query("DELETE FROM conversion_jobs")
    suspend fun clearConversionJobs()

    @Query("DELETE FROM track_records WHERE source = 'CURATED' OR trackId LIKE 'seed_%'")
    suspend fun deleteSeededTracks()

    @Query("DELETE FROM playlist_tracks WHERE trackId LIKE 'seed_%'")
    suspend fun deleteSeededPlaylistRefs()

    @Query("DELETE FROM playlists WHERE name IN ('Midnight Drive', 'Velvet & Chill', 'Late Night Mix')")
    suspend fun deleteSeededPlaylists()
}
