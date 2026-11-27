package com.kaan.melo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM music_playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<MusicPlaylist>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY addedAt")
    fun observePlaylistTracks(playlistId: Long): Flow<List<PlaylistTrack>>

    @Query("SELECT * FROM music_playlists ORDER BY createdAt")
    suspend fun getAllPlaylists(): List<MusicPlaylist>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY addedAt")
    suspend fun getPlaylistTracks(playlistId: Long): List<PlaylistTrack>

    @Query("DELETE FROM playlist_tracks")
    suspend fun clearPlaylistTracks()

    @Query("DELETE FROM music_playlists")
    suspend fun clearPlaylists()

    @Insert
    suspend fun create(playlist: MusicPlaylist): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTrack(item: PlaylistTrack)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(playlistId: Long, trackId: Long)

    @Query("DELETE FROM music_playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)
}
