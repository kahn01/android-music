package com.kaan.melo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "music_playlists")
data class MusicPlaylist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
