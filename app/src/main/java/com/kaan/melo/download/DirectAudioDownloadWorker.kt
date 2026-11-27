package com.kaan.melo.download

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kaan.melo.data.local.DownloadedTrack
import com.kaan.melo.data.local.MeloDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a directly accessible audio file supplied by the user.
 * It deliberately does not extract streams from music platforms or bypass DRM.
 */
class DirectAudioDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sourceUrl = inputData.getString(KEY_URL)?.trim().orEmpty()
        val title = inputData.getString(KEY_TITLE)?.trim().orEmpty().ifBlank { "Bilinmeyen parça" }
        val uri = runCatching { Uri.parse(sourceUrl) }.getOrNull()
            ?: return@withContext Result.failure()
        if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) {
            return@withContext Result.failure()
        }

        var connection: HttpURLConnection? = null
        try {
            connection = URL(sourceUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Melo-Android/0.1")
            connection.connect()

            if (connection.responseCode !in 200..299) return@withContext Result.retry()
            val mime = connection.contentType?.substringBefore(';')?.lowercase().orEmpty()
            val path = uri.lastPathSegment.orEmpty()
            val extension = extensionFor(path, mime)
                ?: return@withContext Result.failure()
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
            val temp = File(directory, target.name + ".part")

            connection.inputStream.use { input ->
                temp.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    var lastReportedProgress = -1
                    while (true) {
                        if (isStopped) {
                            temp.delete()
                            return@withContext Result.failure()
                        }
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) {
                            temp.delete()
                            return@withContext Result.failure()
                        }
                        output.write(buffer, 0, read)
                        if (length > 0L) {
                            val progress = ((total * 100L) / length).toInt().coerceIn(0, 100)
                            if (progress != lastReportedProgress) {
                                setProgress(androidx.work.Data.Builder().putInt(KEY_PROGRESS, progress).build())
                                lastReportedProgress = progress
                            }
                        }
                    }
                }
            }
            if (!temp.renameTo(target)) {
                temp.delete()
                return@withContext Result.failure()
            }

            MeloDatabase.get(applicationContext).downloadedTrackDao().insert(
                DownloadedTrack(
                    title = title,
                    artist = inputData.getString(KEY_ARTIST),
                    sourceUrl = sourceUrl,
                    localPath = target.absolutePath,
                    mimeType = mime.ifBlank { "audio/$extension" }
                )
            )
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            connection?.disconnect()
        }
    }

    private fun extensionFor(path: String, mime: String): String? {
        val fromPath = path.substringAfterLast('.', "").lowercase()
        if (fromPath in setOf("mp3", "wav", "flac", "m4a", "ogg", "aac")) return fromPath
        return when (mime) {
            "audio/mpeg" -> "mp3"
            "audio/wav", "audio/x-wav" -> "wav"
            "audio/flac" -> "flac"
            "audio/mp4", "audio/x-m4a" -> "m4a"
            "audio/ogg" -> "ogg"
            "audio/aac", "audio/aacp" -> "aac"
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
