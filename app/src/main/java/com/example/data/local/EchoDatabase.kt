package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackRecordEntity::class,
        ArtistArtworkEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        ConversionJobEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class EchoDatabase : RoomDatabase() {
    abstract fun echoDao(): EchoDao

    companion object {
        @Volatile
        private var INSTANCE: EchoDatabase? = null

        fun getInstance(context: Context): EchoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    EchoDatabase::class.java,
                    "echo_music.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
