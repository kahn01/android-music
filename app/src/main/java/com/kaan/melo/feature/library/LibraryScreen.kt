package com.kaan.melo.feature.library

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
fun LibraryScreen(
    modifier: Modifier,
    tracks: List<DownloadedTrack>,
    playingTrackId: Long?,
    isPlaying: Boolean,
    playlists: List<com.kaan.melo.data.local.MusicPlaylist>,
    selectedPlaylistId: Long?,
    selectedPlaylistTrackIds: Set<Long>,
    libraryQuery: String,
    onLibraryQueryChange: (String) -> Unit,
    selectedLibrarySection: String,
    onSectionChange: (String) -> Unit,
    onSelectPlaylist: (Long?) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onAddToPlaylist: (Long, Long) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onPlay: (DownloadedTrack) -> Unit,
    onDelete: (DownloadedTrack) -> Unit
) {
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Kütüphanem", fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text("${tracks.size} kayıtlı parça · çevrimdışı dinleme", color = Muted, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
            }
            FilledTonalButton(
                onClick = { onSectionChange("Çalma listeleri"); onCreatePlaylistClick() },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("Liste oluştur", fontSize = 11.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Şarkılar", "Çalma listeleri", "Sanatçılar").forEach { section ->
                FilterChip(
                    selected = selectedLibrarySection == section,
                    onClick = { onSectionChange(section); if (section != "Şarkılar") onSelectPlaylist(null) },
                    label = { Text(section, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF392B5D), selectedLabelColor = Color.White)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (selectedLibrarySection == "Şarkılar") {
            OutlinedTextField(
                value = libraryQuery,
                onValueChange = onLibraryQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Şarkı veya sanatçı ara", color = Muted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
                singleLine = true,
                shape = RoundedCornerShape(15.dp),
                colors = fieldColors()
            )
            if (selectedPlaylistId != null) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Seçili çalma listesi", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onSelectPlaylist(null) }) { Text("Tüm şarkılar") }
                }
            }
        } else if (selectedLibrarySection == "Çalma listeleri") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Listelerin", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Button(onClick = onCreatePlaylistClick, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Yeni liste")
                }
            }
        } else {
            Text("Sanatçıya göre göz at", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
        }
        when (selectedLibrarySection) {
            "Çalma listeleri" -> {
                if (playlists.isEmpty()) EmptyState(Icons.Default.QueueMusic, "Henüz çalma listen yok", "Yeni liste oluşturup şarkılarını içine ekleyebilirsin.")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        Row(Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(15.dp)).clickable { onSelectPlaylist(playlist.id); onSectionChange("Şarkılar") }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.QueueMusic, null, tint = Accent)
                            Spacer(Modifier.width(12.dp))
                            Text(playlist.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onDeletePlaylist(playlist.id) }) { Icon(Icons.Default.DeleteOutline, null, tint = Muted) }
                        }
                    }
                }
            }
            "Sanatçılar" -> {
                val artists = tracks.map { it.artist?.takeIf { value -> value.isNotBlank() } ?: "Bilinmeyen sanatçı" }.distinct().sorted()
                if (artists.isEmpty()) EmptyState(Icons.Default.Person, "Sanatçı bulunamadı", "İndirdiğin parçalar burada sanatçılarına göre listelenecek.")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(artists) { artist ->
                        val count = tracks.count { (it.artist?.takeIf { value -> value.isNotBlank() } ?: "Bilinmeyen sanatçı") == artist }
                        Row(Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(14.dp)).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, null, tint = Accent)
                            Spacer(Modifier.width(12.dp))
                            Text(artist, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("$count şarkı", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
            else -> {
                val visibleTracks = tracks.filter { track ->
                    val matches = libraryQuery.isBlank() || track.title.contains(libraryQuery, true) || (track.artist ?: "").contains(libraryQuery, true)
                    matches && (selectedPlaylistId == null || track.id in selectedPlaylistTrackIds)
                }
                if (visibleTracks.isEmpty()) EmptyState(Icons.Default.LibraryMusic, if (tracks.isEmpty()) "Kütüphanen boş" else "Eşleşen şarkı yok", "Aramana uygun kayıtlı bir parça bulunamadı.")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(visibleTracks, key = { it.id }) { track ->
                        var menuExpanded by remember(track.id) { mutableStateOf(false) }
                        Row(Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(15.dp)).padding(start = 12.dp, end = 5.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(43.dp).background(Color(0xFF30264A), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = Accent) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(track.artist ?: track.mimeType, color = Muted, fontSize = 11.sp, maxLines = 1)
                            }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.PlaylistAdd, "Listeye ekle", tint = Muted) }
                                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                    if (playlists.isEmpty()) DropdownMenuItem(text = { Text("Önce liste oluştur") }, onClick = { menuExpanded = false })
                                    playlists.forEach { playlist ->
                                        DropdownMenuItem(text = { Text(playlist.name) }, onClick = { onAddToPlaylist(playlist.id, track.id); menuExpanded = false })
                                    }
                                }
                            }
                            IconButton(onClick = { onPlay(track) }) { Icon(if (playingTrackId == track.id && isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle, "Çal", tint = Accent) }
                            IconButton(onClick = { onDelete(track) }) { Icon(Icons.Default.DeleteOutline, "Sil", tint = Muted) }
                        }
                    }
                }
            }
        }
    }
}

