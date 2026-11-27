package com.kaan.melo.core.links

import android.net.Uri

/**
 * Identifies known URL hosts only. This does not extract protected streams
 * or bypass DRM, paywalls, authentication or platform download restrictions.
 */
object LinkParser {
    fun parse(raw: String): MusicSource? {
        val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null

        return when {
            host == "youtube.com" || host == "m.youtube.com" ||
                host == "youtu.be" || host == "music.youtube.com" -> MusicSource.YOUTUBE
            host == "spotify.com" || host == "open.spotify.com" -> MusicSource.SPOTIFY
            host == "deezer.com" -> MusicSource.DEEZER
            else -> {
                val path = uri.path.orEmpty().lowercase()
                if (listOf(".mp3", ".wav", ".flac", ".m4a", ".ogg", ".aac").any(path::endsWith)) {
                    MusicSource.DIRECT
                } else null
            }
        }
    }
}
