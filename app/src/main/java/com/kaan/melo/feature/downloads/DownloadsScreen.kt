package com.kaan.melo.feature.downloads

import android.os.Bundle
import android.content.Intent
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.kaan.melo.core.links.LinkParser
import com.kaan.melo.core.links.MusicSource
import com.kaan.melo.data.local.DownloadedTrack
import com.kaan.melo.data.local.MeloDatabase
import com.kaan.melo.download.DownloadScheduler
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.kaan.melo.*
import com.kaan.melo.ui.components.*

@Composable
fun DownloadsScreen(
    modifier: Modifier,
    jobs: List<WorkInfo>,
    onCancel: (WorkInfo) -> Unit
) {
    val active = jobs.filter { !it.state.isFinished }
    val completed = jobs.count { it.state == WorkInfo.State.SUCCEEDED }
    val failed = jobs.count { it.state == WorkInfo.State.FAILED || it.state == WorkInfo.State.CANCELLED }
    Column(modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("İndirmeler", fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text("Arka plandaki indirme işlemlerini buradan takip et.", color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Devam eden", active.size.toString(), Modifier.weight(1f))
            StatCard("Tamamlanan", completed.toString(), Modifier.weight(1f))
            StatCard("Hatalı", failed.toString(), Modifier.weight(1f))
        }
        if (jobs.isEmpty()) {
            EmptyState(Icons.Default.Download, "Henüz indirme yok", "Keşfet sekmesinden doğrudan bir ses dosyası bağlantısı ekle.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(jobs, key = { it.id.toString() }) { job ->
                    val label = when (job.state) {
                        WorkInfo.State.ENQUEUED -> "Sırada"
                        WorkInfo.State.RUNNING -> "İndiriliyor"
                        WorkInfo.State.SUCCEEDED -> "Tamamlandı"
                        WorkInfo.State.FAILED -> "Başarısız"
                        WorkInfo.State.BLOCKED -> "Bekliyor"
                        WorkInfo.State.CANCELLED -> "İptal edildi"
                    }
                    Row(
                        Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(16.dp)).padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(if (job.state == WorkInfo.State.SUCCEEDED) Icons.Default.CheckCircle else Icons.Default.Download, null, tint = Accent)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            val downloadTitle = job.progress.getString(com.kaan.melo.download.DirectAudioDownloadWorker.KEY_TITLE)
                                ?: job.inputData.getString(com.kaan.melo.download.DirectAudioDownloadWorker.KEY_TITLE)
                                ?: "Bilinmeyen parça"
                            val downloadArtist = job.progress.getString(com.kaan.melo.download.DirectAudioDownloadWorker.KEY_ARTIST)
                                ?: job.inputData.getString(com.kaan.melo.download.DirectAudioDownloadWorker.KEY_ARTIST)
                            Text(downloadTitle, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            if (!downloadArtist.isNullOrBlank()) {
                                Text(downloadArtist, color = Muted, fontSize = 11.sp, maxLines = 1)
                            }
                            Text(label, color = Muted, fontSize = 12.sp)
                            if (job.state == WorkInfo.State.RUNNING) {
                                val progress = job.progress.getInt("progress", 0)
                                Spacer(Modifier.height(7.dp))
                                LinearProgressIndicator(
                                    progress = progress / 100f,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Accent,
                                    trackColor = Color(0xFF393344)
                                )
                                Text("$progress%", color = Muted, fontSize = 10.sp)
                            }
                        }
                        if (!job.state.isFinished) {
                            TextButton(
                                onClick = { onCancel(job) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "İndirmeyi iptal et", tint = Color(0xFFFF8A80), modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("İptal", color = Color(0xFFFF8A80), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

