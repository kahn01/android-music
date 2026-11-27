package com.kaan.melo.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaan.melo.*
import com.kaan.melo.data.local.DownloadedTrack
import com.kaan.melo.data.local.MusicPlaylist
import com.kaan.melo.ui.components.EmptyState
import com.kaan.melo.ui.components.TrackArtwork
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(
    modifier: Modifier,
    tracks: List<DownloadedTrack>,
    playingTrackId: Long?,
    isPlaying: Boolean,
    playlists: List<MusicPlaylist>,
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
    onDelete: (DownloadedTrack) -> Unit,
    onBulkDelete: (List<Long>) -> Unit,
    onRemoveFromPlaylist: (Long, Long) -> Unit
) {
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var selectionMode by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf("En yeni") }
    var sortMenu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }

    var storageBytes by remember { mutableLongStateOf(0L) }
    LaunchedEffect(tracks) {
        storageBytes = withContext(Dispatchers.IO) {
            tracks.sumOf { track ->
                runCatching { File(track.localPath).takeIf { it.isFile }?.length() ?: 0L }.getOrDefault(0L)
            }
        }
    }
    val storageText = when {
        storageBytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(storageBytes / (1024.0 * 1024.0 * 1024.0))
        storageBytes >= 1024L * 1024L -> "%.0f MB".format(storageBytes / (1024.0 * 1024.0))
        else -> "%.0f KB".format(storageBytes / 1024.0)
    }

    val matchingTracks = tracks.filter { track ->
        libraryQuery.isBlank() || track.title.contains(libraryQuery, true) ||
            (track.artist ?: "").contains(libraryQuery, true) ||
            track.mimeType.contains(libraryQuery, true)
    }
    val visibleTracks = when (selectedLibrarySection) {
        "Son çalınanlar" -> matchingTracks.filter { it.lastPlayedAt != null }.sortedByDescending { it.lastPlayedAt }
        "En çok dinlenenler" -> matchingTracks.filter { it.playCount > 0 }.sortedWith(compareByDescending<DownloadedTrack> { it.playCount }.thenByDescending { it.lastPlayedAt ?: 0L })
        else -> when (sortMode) {
            "A-Z" -> matchingTracks.sortedBy { it.title.lowercase() }
            "Sanatçı" -> matchingTracks.sortedBy { (it.artist ?: "").lowercase() }
            "En çok dinlenen" -> matchingTracks.sortedByDescending { it.playCount }
            "En eski" -> matchingTracks.sortedBy { it.addedAt }
            else -> matchingTracks.sortedByDescending { it.addedAt }
        }
    }.let { list -> if (selectedPlaylistId == null) list else list.filter { it.id in selectedPlaylistTrackIds } }

    Column(modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Kütüphanem", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("${tracks.size} kayıtlı parça · ${storageText}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            IconButton(onClick = { selectionMode = !selectionMode; selectedIds = emptySet() }) {
                Icon(if (selectionMode) Icons.Default.Close else Icons.Default.Checklist, if (selectionMode) "Seçimi kapat" else "Çoklu seç", tint = Accent)
            }
            FilledTonalButton(
                onClick = { onSectionChange("Çalma listeleri"); onCreatePlaylistClick() },
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(3.dp))
                Text("Liste", fontSize = 11.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 13.dp)) {
            listOf("Şarkılar", "Çalma listeleri", "Sanatçılar").forEach { section ->
                FilterChip(
                    selected = selectedLibrarySection == section,
                    onClick = { onSectionChange(section); selectedIds = emptySet(); if (section != "Şarkılar") onSelectPlaylist(null) },
                    label = { Text(section, fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF392B5D), selectedLabelColor = Color.White)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Son çalınanlar", "En çok dinlenenler").forEach { section ->
                FilterChip(
                    selected = selectedLibrarySection == section,
                    onClick = { onSectionChange(section); onSelectPlaylist(null); selectedIds = emptySet() },
                    label = { Text(section, fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF392B5D), selectedLabelColor = Color.White)
                )
            }
        }

        if (selectedLibrarySection == "Şarkılar" || selectedLibrarySection == "Son çalınanlar" || selectedLibrarySection == "En çok dinlenenler") {
            OutlinedTextField(
                value = libraryQuery,
                onValueChange = onLibraryQueryChange,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                placeholder = { Text("Şarkı, sanatçı veya format ara", color = Muted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Color(0xFF393344),
                    cursorColor = Accent
                )
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (selectedPlaylistId != null) "Çalma listesi içeriği" else "${visibleTracks.size} şarkı",
                    color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f)
                )
                if (selectedLibrarySection == "Şarkılar") {
                    Box {
                        TextButton(onClick = { sortMenu = true }) {
                            Icon(Icons.Default.Sort, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(sortMode, fontSize = 11.sp)
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf("En yeni", "En eski", "A-Z", "Sanatçı", "En çok dinlenen").forEach { option ->
                                DropdownMenuItem(text = { Text(option) }, onClick = { sortMode = option; sortMenu = false })
                            }
                        }
                    }
                }
                if (selectedPlaylistId != null) {
                    TextButton(onClick = { onSelectPlaylist(null) }) { Text("Tüm şarkılar", fontSize = 11.sp) }
                }
            }
            if (selectionMode && selectedIds.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFF29213D), RoundedCornerShape(13.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${selectedIds.size} seçildi", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Box {
                        TextButton(onClick = { addMenu = true }) { Text("Listeye ekle", fontSize = 11.sp) }
                        DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                            playlists.forEach { playlist ->
                                DropdownMenuItem(text = { Text(playlist.name) }, onClick = {
                                    selectedIds.forEach { onAddToPlaylist(playlist.id, it) }
                                    selectedIds = emptySet()
                                    selectionMode = false
                                    addMenu = false
                                })
                            }
                        }
                    }
                    TextButton(onClick = { onBulkDelete(selectedIds.toList()); selectedIds = emptySet(); selectionMode = false }) {
                        Text("Sil", color = Color(0xFFFF8A80), fontSize = 11.sp)
                    }
                }
            }
        }

        when (selectedLibrarySection) {
            "Çalma listeleri" -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Çalma listelerin", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Button(onClick = onCreatePlaylistClick, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Yeni liste")
                    }
                }
                if (playlists.isEmpty()) EmptyState(Icons.Default.QueueMusic, "Henüz çalma listen yok", "Yeni liste oluşturup şarkılarını içine ekleyebilirsin.")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        Row(Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(15.dp)).clickable {
                            onSelectPlaylist(playlist.id); onSectionChange("Şarkılar")
                        }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.QueueMusic, null, tint = Accent)
                            Spacer(Modifier.width(12.dp))
                            Text(playlist.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onDeletePlaylist(playlist.id) }) { Icon(Icons.Default.DeleteOutline, null, tint = Muted) }
                        }
                    }
                }
            }
            "Sanatçılar" -> {
                val artists = tracks.map { it.artist?.takeIf(String::isNotBlank) ?: "Bilinmeyen sanatçı" }.distinct().sorted()
                if (artists.isEmpty()) EmptyState(Icons.Default.Person, "Sanatçı bulunamadı", "İndirdiğin parçalar burada sanatçılarına göre listelenecek.")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(artists) { artist ->
                        val count = tracks.count { (it.artist?.takeIf(String::isNotBlank) ?: "Bilinmeyen sanatçı") == artist }
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
                if (visibleTracks.isEmpty()) {
                    EmptyState(Icons.Default.LibraryMusic, when (selectedLibrarySection) {
                        "Son çalınanlar" -> "Henüz dinleme geçmişi yok"
                        "En çok dinlenenler" -> "Henüz dinleme istatistiği yok"
                        else -> if (tracks.isEmpty()) "Kütüphanen boş" else "Eşleşen şarkı yok"
                    }, "İndirdiğin ve dinlediğin parçalar burada görünecek.")
                } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visibleTracks, key = { it.id }) { track ->
                        val selected = track.id in selectedIds
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (selected) Color(0xFF30264A) else SurfaceDark, RoundedCornerShape(14.dp))
                                .clickable {
                                    if (selectionMode) selectedIds = if (selected) selectedIds - track.id else selectedIds + track.id
                                    else onPlay(track)
                                }
                                .padding(horizontal = 9.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectionMode) {
                                Checkbox(checked = selected, onCheckedChange = { checked ->
                                    selectedIds = if (checked) selectedIds + track.id else selectedIds - track.id
                                })
                            } else {
                                TrackArtwork(track.artworkUrl, Modifier.size(48.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(
                                    "${track.artist ?: track.mimeType} · ${track.playCount} dinleme",
                                    color = Muted, fontSize = 10.sp, maxLines = 1
                                )
                            }
                            if (selectedPlaylistId != null) {
                                IconButton(onClick = { onRemoveFromPlaylist(selectedPlaylistId, track.id) }) {
                                    Icon(Icons.Default.RemoveCircleOutline, "Listeden çıkar", tint = Muted)
                                }
                            }
                            IconButton(onClick = { onPlay(track) }) {
                                Icon(if (playingTrackId == track.id && isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle, "Çal", tint = Accent)
                            }
                            if (!selectionMode) {
                                IconButton(onClick = { onDelete(track) }) { Icon(Icons.Default.DeleteOutline, "Sil", tint = Muted) }
                            }
                        }
                    }
                }
            }
        }
    }
}
