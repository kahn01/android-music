package com.kaan.melo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DownloadedTrack::class, MusicPlaylist::class, PlaylistTrack::class],
    version = 2,
    exportSchema = false
)
abstract class MeloDatabase : RoomDatabase() {
    abstract fun downloadedTrackDao(): DownloadedTrackDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS music_playlists (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS playlist_tracks (playlistId INTEGER NOT NULL, trackId INTEGER NOT NULL, addedAt INTEGER NOT NULL, PRIMARY KEY(playlistId, trackId))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_tracks_trackId ON playlist_tracks(trackId)")
            }
        }

        @Volatile private var instance: MeloDatabase? = null

        fun get(context: Context): MeloDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MeloDatabase::class.java,
                "melo-library.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
