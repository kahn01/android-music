package com.kaan.melo.download

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response as ExtractorResponse
import java.util.concurrent.TimeUnit

data class ResolvedYoutubeAudio(
    val url: String,
    val title: String,
    val artist: String,
    val extension: String
)

data class YoutubePlaylistItem(
    val title: String,
    val url: String,
    val artist: String
)

data class ResolvedYoutubePlaylist(
    val title: String,
    val items: List<YoutubePlaylistItem>
)

object YoutubeAudioResolver {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    @Volatile private var initialized = false

    @Synchronized
    private fun initialize() {
        if (!initialized) {
            NewPipe.init(object : Downloader() {
                override fun execute(request: Request): ExtractorResponse {
                    val builder = okhttp3.Request.Builder()
                        .url(request.url())
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125.0 Mobile Safari/537.36")

                    request.headers().forEach { (name, values) ->
                        values.forEach { builder.addHeader(name, it) }
                    }

                    when (request.httpMethod().uppercase()) {
                        "GET" -> builder.get()
                        "HEAD" -> builder.head()
                        "POST" -> builder.post(
                            (request.dataToSend() ?: ByteArray(0))
                                .toRequestBody("application/json".toMediaType())
                        )
                        else -> throw IllegalArgumentException("Unsupported HTTP method: ${request.httpMethod()}")
                    }

                    client.newCall(builder.build()).execute().use { response ->
                        return ExtractorResponse(
                            response.code,
                            response.message,
                            response.headers.toMultimap(),
                            response.body?.string().orEmpty(),
                            response.request.url.toString()
                        )
                    }
                }
            })
            initialized = true
        }
    }

    fun isPlaylistUrl(raw: String): Boolean {
        val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase()?.removePrefix("www.")
        return host in setOf("youtube.com", "m.youtube.com", "music.youtube.com") &&
            !uri.getQueryParameter("list").isNullOrBlank()
    }

    fun resolvePlaylist(playlistUrl: String): ResolvedYoutubePlaylist {
        initialize()
        val extractor = ServiceList.YouTube.getPlaylistExtractor(playlistUrl)
        extractor.fetchPage()

        val items = mutableListOf<YoutubePlaylistItem>()
        var page = extractor.initialPage
        var pagesRead = 0
        while (true) {
            page.items.forEach { item ->
                if (items.size < 300 && item.url.isNotBlank()) {
                    items += YoutubePlaylistItem(
                        title = item.name.ifBlank { "Bilinmeyen parça" },
                        url = item.url,
                        artist = item.uploaderName.ifBlank { "YouTube" }
                    )
                }
            }
            pagesRead++
            val next = page.nextPage
            if (next == null || items.size >= 300 || pagesRead >= 20) break
            page = extractor.getPage(next)
        }

        return ResolvedYoutubePlaylist(
            title = extractor.name.ifBlank { "YouTube oynatma listesi" },
            items = items.distinctBy { it.url }
        )
    }

    fun resolve(videoUrl: String): ResolvedYoutubeAudio {
        initialize()
        val extractor = ServiceList.YouTube.getStreamExtractor(videoUrl)
        extractor.fetchPage()
        val audio = extractor.audioStreams.maxByOrNull { it.averageBitrate }
            ?: throw IllegalStateException("No audio stream available for this video")
        return ResolvedYoutubeAudio(
            url = audio.content,
            title = extractor.name.ifBlank { "Bilinmeyen parça" },
            artist = extractor.uploaderName.ifBlank { "YouTube" },
            extension = if (audio.codec?.contains("opus", ignoreCase = true) == true) "webm" else "m4a"
        )
    }
}
