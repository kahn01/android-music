package com.kaan.melo.download

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kaan.melo.data.local.DownloadedTrack
import com.kaan.melo.data.ArtworkResolver
import com.kaan.melo.data.local.MeloDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads direct audio URLs and resolves public YouTube audio streams before saving them. */
class DirectAudioDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sourceUrl = inputData.getString(KEY_URL)?.trim().orEmpty()
        var resolvedUrl = sourceUrl
        var title = inputData.getString(KEY_TITLE)?.trim().orEmpty().ifBlank { "Bilinmeyen parça" }
        var artist = inputData.getString(KEY_ARTIST)
        var extensionHint: String? = null

        setProgress(
            androidx.work.Data.Builder()
                .putString(KEY_TITLE, title)
                .putString(KEY_ARTIST, artist)
                .putInt(KEY_PROGRESS, 0)
                .build()
        )

        if (isYouTubeUrl(sourceUrl)) {
            try {
                val resolved = com.kaan.melo.download.YoutubeAudioResolver.resolve(sourceUrl)
                resolvedUrl = resolved.url
                title = resolved.title
                artist = resolved.artist
                extensionHint = resolved.extension
                setProgress(
                    androidx.work.Data.Builder()
                        .putString(KEY_TITLE, title)
                        .putString(KEY_ARTIST, artist)
                        .putInt(KEY_PROGRESS, 0)
                        .build()
                )
            } catch (_: Exception) {
                return@withContext Result.failure(
                    androidx.work.Data.Builder().putString("error", "YouTube bağlantısı çözümlenemedi. Video özel, yaş kısıtlı veya geçici olarak desteklenmiyor olabilir.").build()
                )
            }
        }

        val uri = runCatching { Uri.parse(resolvedUrl) }.getOrNull()
            ?: return@withContext Result.failure()
        if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) {
            return@withContext Result.failure()
        }

        var connection: HttpURLConnection? = null
        var tempFile: File? = null
        var finalTarget: File? = null
        try {
            connection = URL(resolvedUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Melo-Android/0.1")
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode in 500..599 || responseCode == 408 || responseCode == 429) {
                return@withContext Result.retry()
            }
            if (responseCode !in 200..299) return@withContext Result.failure()

            val mime = connection.contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            val path = connection.url.path.orEmpty().ifBlank { uri.lastPathSegment.orEmpty() }
            val extension = extensionFor(path, mime) ?: extensionHint ?: return@withContext Result.failure()
            if (mime.isNotBlank() && !mime.startsWith("audio/") &&
                mime != "application/octet-stream" && mime != "binary/octet-stream" && mime != "application/octet-stream"
            ) return@withContext Result.failure()

            val length = connection.contentLengthLong
            if (length > MAX_BYTES) return@withContext Result.failure()

            val directory = File(
                applicationContext.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC),
                "Melo"
            )
            if (!directory.exists() && !directory.mkdirs()) return@withContext Result.failure()

            val safeName = title.replace(Regex("[^A-Za-z0-9._ -]"), "_")
                .trim().take(70).ifBlank { "track" }
            val target = File(directory, "${System.currentTimeMillis()}-$safeName.$extension")
            finalTarget = target
            tempFile = File(directory, target.name + ".part")

            connection.inputStream.use { input ->
                tempFile!!.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    var lastReportedProgress = -1
                    while (true) {
                        if (isStopped) {
                            tempFile?.delete()
                            return@withContext Result.failure()
                        }
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) {
                            tempFile?.delete()
                            return@withContext Result.failure()
                        }
                        output.write(buffer, 0, read)
                        if (length > 0L) {
                            val progress = ((total * 100L) / length).toInt().coerceIn(0, 100)
                            if (progress != lastReportedProgress) {
                                setProgress(
                                    androidx.work.Data.Builder()
                                        .putString(KEY_TITLE, title)
                                        .putString(KEY_ARTIST, artist)
                                        .putInt(KEY_PROGRESS, progress)
                                        .build()
                                )
                                lastReportedProgress = progress
                            }
                        }
                    }
                    if (length > 0L && total != length) {
                        tempFile?.delete()
                        return@withContext Result.retry()
                    }
                }
            }

            val completedTemp = tempFile ?: return@withContext Result.failure()
            if (!completedTemp.renameTo(target)) {
                completedTemp.delete()
                return@withContext Result.failure()
            }

            val artworkUrl = runCatching { ArtworkResolver.findArtwork(title, artist) }.getOrNull()
            MeloDatabase.get(applicationContext).downloadedTrackDao().insert(
                DownloadedTrack(
                    title = title,
                    artist = artist,
                    sourceUrl = sourceUrl,
                    localPath = target.absolutePath,
                    mimeType = mime.ifBlank { "audio/$extension" },
                    artworkUrl = artworkUrl
                )
            )
            setProgress(
                androidx.work.Data.Builder()
                    .putString(KEY_TITLE, title)
                    .putString(KEY_ARTIST, artist)
                    .putInt(KEY_PROGRESS, 100)
                    .build()
            )
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            runCatching { tempFile?.delete() }
            runCatching { finalTarget?.delete() }
            throw cancelled
        } catch (_: Exception) {
            runCatching { tempFile?.delete() }
            runCatching { finalTarget?.delete() }
            Result.retry()
        } finally {
            connection?.disconnect()
        }
    }

    private fun isYouTubeUrl(raw: String): Boolean {
        val host = runCatching { Uri.parse(raw).host?.lowercase()?.removePrefix("www.") }.getOrNull()
        return host in setOf("youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")
    }

    private fun extensionFor(path: String, mime: String): String? {
        val fromPath = path.substringAfterLast('.', "").lowercase()
        if (fromPath in setOf("mp3", "wav", "flac", "m4a", "ogg", "aac", "webm")) return fromPath
        return when (mime) {
            "audio/mpeg" -> "mp3"
            "audio/wav", "audio/x-wav", "audio/vnd.wave" -> "wav"
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/mp4", "audio/x-m4a" -> "m4a"
            "audio/ogg", "application/ogg" -> "ogg"
            "audio/aac", "audio/aacp" -> "aac"
            "audio/webm" -> "webm"
            else -> null
        }
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_PROGRESS = "progress"
        private const val MAX_BYTES = 250L * 1024L * 1024L
    }
}
