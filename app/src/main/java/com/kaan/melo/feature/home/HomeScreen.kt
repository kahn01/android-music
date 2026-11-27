package com.kaan.melo.feature.home

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
fun HomeScreen(
    modifier: Modifier,
    link: String,
    onLinkChange: (String) -> Unit,
    title: String,
    onTitleChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onDownload: () -> Unit,
    onOpenSource: () -> Unit,
    message: String?,
    source: MusicSource?,
    playlistTitle: String?,
    playlistItems: List<com.kaan.melo.download.YoutubePlaylistItem>,
    resolvingPlaylist: Boolean,
    onDownloadAllPlaylist: () -> Unit,
    onDownloadPlaylistTrack: (com.kaan.melo.download.YoutubePlaylistItem) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).background(
                        Brush.linearGradient(listOf(Accent, Color(0xFF6545C5))),
                        RoundedCornerShape(15.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.GraphicEq, null, tint = Color.White) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("melo", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                    Text("MÜZİĞİN, SENİN KONTROLÜNDE", fontSize = 9.sp, color = Muted, letterSpacing = 1.sp)
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.Headphones, null, tint = Muted)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Müziğini keşfet.", fontSize = 31.sp, fontWeight = FontWeight.Bold)
                Text("Ses bağlantısını ekle, indir ve çevrimdışı kütüphanende dinle.", color = Muted, fontSize = 14.sp)
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().background(
                    Brush.linearGradient(listOf(Color(0xFF28203F), Color(0xFF171722))),
                    RoundedCornerShape(24.dp)
                ).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Link, null, tint = Accent)
                    Spacer(Modifier.width(8.dp))
                    Text("MÜZİK LİNKİ", fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                }
                OutlinedTextField(
                    value = link,
                    onValueChange = onLinkChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Doğrudan ses dosyası bağlantısı", color = Muted) },
                    minLines = 2,
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Şarkı adı (isteğe bağlı)", color = Muted) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors()
                )
                Button(
                    onClick = onAnalyze,
                    enabled = link.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Icon(Icons.Default.Search, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Bağlantıyı analiz et", fontWeight = FontWeight.Bold)
                }
                if (resolvingPlaylist) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Accent)
                        Text("Playlist parçaları alınıyor...", color = Muted, fontSize = 12.sp)
                    }
                }
                if (message != null) {
                    Text(message, color = if (source == MusicSource.DIRECT || source == MusicSource.YOUTUBE) Color(0xFFB8A5FF) else Color(0xFFFFB4A9), fontSize = 13.sp)
                }
                if (playlistTitle != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(playlistTitle, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${playlistItems.size} parça", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Button(
                                onClick = onDownloadAllPlaylist,
                                enabled = playlistItems.isNotEmpty(),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Tümünü indir")
                            }
                        }
                        playlistItems.forEachIndexed { index, track ->
                            Row(
                                Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(14.dp)).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${index + 1}", color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                    Text(track.artist, color = Muted, fontSize = 11.sp, maxLines = 1)
                                }
                                IconButton(onClick = { onDownloadPlaylistTrack(track) }) {
                                    Icon(Icons.Default.Download, contentDescription = "İndir", tint = Accent)
                                }
                            }
                        }
                        Text("Yalnızca indirme hakkına sahip olduğun veya indirilmesine izin verilen içerikleri indir.", color = Muted, fontSize = 10.sp)
                    }
                }
                if (source == MusicSource.SPOTIFY || source == MusicSource.DEEZER) {
                    OutlinedButton(
                        onClick = onOpenSource,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Bağlantıyı platformda aç", fontWeight = FontWeight.Bold)
                    }
                    Text("Spotify ve Deezer'ın korumalı müzik dosyaları dışarı aktarılamaz.", color = Muted, fontSize = 11.sp)
                }
                if (source == MusicSource.DIRECT || (source == MusicSource.YOUTUBE && playlistTitle == null && !resolvingPlaylist)) {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7254D4))
                    ) {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (source == MusicSource.YOUTUBE) "YouTube sesini indir" else "İndirmeyi başlat", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Text("BAĞLANTI TANIMA", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SourceChip("YouTube", "YT")
                SourceChip("Spotify", "S")
                SourceChip("Deezer", "D")
            }
            Text("YouTube ses indirme ve doğrudan ses dosyası indirme desteklenir.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

