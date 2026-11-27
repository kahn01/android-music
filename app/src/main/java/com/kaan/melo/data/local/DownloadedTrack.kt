package com.kaan.melo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_tracks")
data class DownloadedTrack(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String? = null,
    val sourceUrl: String,
    val localPath: String,
    val mimeType: String,
    val addedAt: Long = System.currentTimeMillis(),
    val artworkUrl: String? = null,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null
)
