package com.kaan.melo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedTrackDao {
    @Query("SELECT * FROM downloaded_tracks ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<DownloadedTrack>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(track: DownloadedTrack): Long

    @Query("DELETE FROM downloaded_tracks WHERE id = :trackId")
    suspend fun deleteById(trackId: Long)

    @Query("DELETE FROM downloaded_tracks WHERE id IN (:trackIds)")
    suspend fun deleteByIds(trackIds: List<Long>)

    @Query("UPDATE downloaded_tracks SET playCount = playCount + 1, lastPlayedAt = :playedAt WHERE id = :trackId")
    suspend fun recordPlay(trackId: Long, playedAt: Long = System.currentTimeMillis())

    @Query("UPDATE downloaded_tracks SET artworkUrl = :url WHERE id = :trackId")
    suspend fun updateArtwork(trackId: Long, url: String)

    @Query("SELECT * FROM downloaded_tracks WHERE id = :trackId LIMIT 1")
    suspend fun findById(trackId: Long): DownloadedTrack?
}
