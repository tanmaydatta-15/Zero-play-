package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT * FROM playlist_tracks ORDER BY addedAt ASC")
    fun observePlaylistTrackRefs(): Flow<List<PlaylistTrackCrossRef>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrackToPlaylist(ref: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String)

    @Query("SELECT * FROM conversion_jobs ORDER BY createdAt DESC")
    fun observeConversionJobs(): Flow<List<ConversionJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversionJob(job: ConversionJobEntity)

    @Query("DELETE FROM conversion_jobs")
    suspend fun clearConversionJobs()

    @Query("DELETE FROM track_records WHERE source = 'CURATED' OR trackId LIKE 'seed_%'")
    suspend fun deleteSeededTracks()

    @Query("DELETE FROM playlist_tracks WHERE trackId LIKE 'seed_%'")
    suspend fun deleteSeededPlaylistRefs()

    @Query("DELETE FROM playlists WHERE name IN ('Midnight Drive', 'Velvet & Chill', 'Late Night Mix')")
    suspend fun deleteSeededPlaylists()
}
