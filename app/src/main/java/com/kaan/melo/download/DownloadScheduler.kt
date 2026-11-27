package com.kaan.melo.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object DownloadScheduler {
    const val TAG_DOWNLOAD = "melo-download"

    fun enqueue(
        context: Context,
        url: String,
        title: String,
        artist: String? = null
    ) {
        val data = Data.Builder()
            .putString(DirectAudioDownloadWorker.KEY_URL, url)
            .putString(DirectAudioDownloadWorker.KEY_TITLE, title)
            .putString(DirectAudioDownloadWorker.KEY_ARTIST, artist)
            .build()

        val request = OneTimeWorkRequestBuilder<DirectAudioDownloadWorker>()
            .setInputData(data)
            .addTag(TAG_DOWNLOAD)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(url.trim().toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

        WorkManager.getInstance(context).enqueueUniqueWork(
            "durka-download-$digest",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
