package com.kaan.melo.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object ArtworkResolver {
    fun findArtwork(title: String, artist: String?): String? {
        val term = listOfNotNull(artist?.takeIf { it.isNotBlank() }, title.takeIf { it.isNotBlank() })
            .joinToString(" ")
        if (term.isBlank()) return null
        val encoded = URLEncoder.encode(term, "UTF-8")
        val connection = (URL("https://itunes.apple.com/search?term=$encoded&entity=song&limit=1")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            setRequestProperty("User-Agent", "Melo-Android/0.2")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val results = JSONObject(json).optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            results.optJSONObject(0)?.optString("artworkUrl100")
                ?.takeIf { it.startsWith("https://") }
                ?.replace("100x100bb", "600x600bb")
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
