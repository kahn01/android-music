package com.kaan.melo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DownloadedTrack::class], version = 1, exportSchema = false)
abstract class MeloDatabase : RoomDatabase() {
    abstract fun downloadedTrackDao(): DownloadedTrackDao

    companion object {
        @Volatile private var instance: MeloDatabase? = null

        fun get(context: Context): MeloDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MeloDatabase::class.java,
                "melo-library.db"
            ).build().also { instance = it }
        }
    }
}
