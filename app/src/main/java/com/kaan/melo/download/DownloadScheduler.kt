package com.kaan.melo.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

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
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }
}
