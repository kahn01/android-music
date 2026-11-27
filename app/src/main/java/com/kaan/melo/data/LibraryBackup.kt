package com.kaan.melo.data

import android.content.Context
import android.net.Uri
import com.kaan.melo.data.local.MeloDatabase
import com.kaan.melo.data.local.MusicPlaylist
import com.kaan.melo.data.local.PlaylistTrack
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object LibraryBackup {
    suspend fun export(context: Context, database: MeloDatabase, uri: Uri) {
        val root = JSONObject()
            .put("formatVersion", 1)
            .put("app", "Durka")
            .put("createdAt", System.currentTimeMillis())
        val playlistArray = JSONArray()
        val dao = database.playlistDao()
        val tracksDao = database.downloadedTrackDao()
        dao.getAllPlaylists().forEach { playlist ->
            val item = JSONObject().put("name", playlist.name)
            val members = JSONArray()
            dao.getPlaylistTracks(playlist.id).forEach { relation ->
                val track = tracksDao.findById(relation.trackId)
                if (track != null) members.put(track.sourceUrl)
            }
            item.put("sourceUrls", members)
            playlistArray.put(item)
        }
        root.put("playlists", playlistArray)
        val preferences = context.getSharedPreferences("durka_settings", Context.MODE_PRIVATE)
        val settings = JSONObject()
        preferences.all.forEach { (key, value) ->
            when (value) {
                is String, is Boolean, is Int, is Long, is Float -> settings.put(key, value)
            }
        }
        root.put("settings", settings)
        context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(root.toString(2))
        } ?: error("Yedek dosyası oluşturulamadı")
    }

    suspend fun restore(context: Context, database: MeloDatabase, uri: Uri) {
        val json = context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
        } ?: error("Yedek dosyası okunamadı")
        val root = JSONObject(json)
        require(root.optInt("formatVersion") == 1) { "Bu yedek sürümü desteklenmiyor." }
        val dao = database.playlistDao()
        val tracksDao = database.downloadedTrackDao()
        dao.clearPlaylistTracks()
        dao.clearPlaylists()
        val playlists = root.optJSONArray("playlists") ?: JSONArray()
        for (index in 0 until playlists.length()) {
            val item = playlists.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            if (name.isEmpty()) continue
            val playlistId = dao.create(MusicPlaylist(name = name))
            val urls = item.optJSONArray("sourceUrls") ?: JSONArray()
            for (trackIndex in 0 until urls.length()) {
                val sourceUrl = urls.optString(trackIndex)
                val trackId = tracksDao.findIdBySourceUrl(sourceUrl)
                if (trackId != null) dao.addTrack(PlaylistTrack(playlistId, trackId))
            }
        }
        val settings = root.optJSONObject("settings") ?: JSONObject()
        val editor = context.getSharedPreferences("durka_settings", Context.MODE_PRIVATE).edit().clear()
        settings.keys().forEach { key ->
            when (val value = settings.opt(key)) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
            }
        }
        editor.apply()
    }
}
