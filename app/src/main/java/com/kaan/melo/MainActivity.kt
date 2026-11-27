package com.kaan.melo

import com.kaan.melo.feature.home.HomeScreen
import com.kaan.melo.feature.downloads.DownloadsScreen
import com.kaan.melo.feature.library.LibraryScreen
import com.kaan.melo.ui.components.*
import com.kaan.melo.player.FullPlayerScreen

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.core.app.ActivityCompat
import com.kaan.melo.data.ArtworkResolver
import com.kaan.melo.ui.components.TrackArtwork
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

internal val Background = Color(0xFF09090F)
internal val SurfaceDark = Color(0xFF171722)
internal val Accent = Color(0xFF9B7BFF)
internal val Muted = Color(0xFF9291A3)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MeloTheme { MeloApp() } }
    }
}

@Composable
private fun MeloTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Background,
            surface = SurfaceDark,
            primary = Accent,
            secondary = Color(0xFFC4B4FF)
        ),
        content = content
    )
}

@Composable
private fun MeloApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { MeloDatabase.get(context) }
    var player by remember { mutableStateOf<MediaController?>(null) }
    val controllerFuture = remember(context) {
        MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, MeloPlaybackService::class.java))
        ).buildAsync()
    }
    DisposableEffect(controllerFuture) {
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }.onSuccess { player = it }
            },
            ContextCompat.getMainExecutor(context)
        )
        onDispose { MediaController.releaseFuture(controllerFuture) }
    }
    val tracks by database.downloadedTrackDao().observeAll().collectAsState(initial = emptyList())
    val jobs by WorkManager.getInstance(context)
        .getWorkInfosByTagLiveData(DownloadScheduler.TAG_DOWNLOAD)
        .observeAsState(initial = emptyList())
    var selectedTab by remember { mutableIntStateOf(0) }
    var showFullPlayer by remember { mutableStateOf(false) }\n    var vehicleMode by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf<MusicSource?>(null) }
    var playingTrackId by remember { mutableStateOf<Long?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playlistTitle by remember { mutableStateOf<String?>(null) }
    var playlistItems by remember { mutableStateOf<List<com.kaan.melo.download.YoutubePlaylistItem>>(emptyList()) }
    var resolvingPlaylist by remember { mutableStateOf(false) }
    val playlists by database.playlistDao().observePlaylists().collectAsState(initial = emptyList())
    var selectedLibrarySection by remember { mutableStateOf("Şarkılar") }
    var libraryQuery by remember { mutableStateOf("") }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
    var selectedPlaylistTrackIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val artworkLookupAttempts = remember { mutableSetOf<Long>() }
    LaunchedEffect(selectedPlaylistId) {
        val id = selectedPlaylistId
        if (id == null) selectedPlaylistTrackIds = emptySet()
        else database.playlistDao().observePlaylistTracks(id).collect { rows ->
            selectedPlaylistTrackIds = rows.map { it.trackId }.toSet()
        }
    }

    LaunchedEffect(tracks) {
        tracks.filter { it.artworkUrl.isNullOrBlank() && artworkLookupAttempts.add(it.id) }
            .forEach { track ->
                val artwork = withContext(Dispatchers.IO) {
                    runCatching { ArtworkResolver.findArtwork(track.title, track.artist) }.getOrNull()
                }
                if (!artwork.isNullOrBlank()) database.downloadedTrackDao().updateArtwork(track.id, artwork)
            }
    }

    DisposableEffect(player) {
        val activePlayer = player
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId?.toLongOrNull() ?: return
                playingTrackId = id
                coroutineScope.launch { database.downloadedTrackDao().recordPlay(id, System.currentTimeMillis()) }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) isPlaying = false
            }
        }
        activePlayer?.addListener(listener)
        onDispose { activePlayer?.removeListener(listener) }
    }

    fun playTrack(track: DownloadedTrack) {
        val activePlayer = player ?: return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && context is android.app.Activity) {
            ActivityCompat.requestPermissions(context, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002)
        }
        if (playingTrackId == track.id) {
            if (activePlayer.isPlaying) activePlayer.pause() else activePlayer.play()
        } else {
            playingTrackId = track.id
            val queue = tracks.map { item ->
                val metadata = MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(item.artist ?: "Melo")
                    .apply { item.artworkUrl?.let { setArtworkUri(android.net.Uri.parse(it)) } }
                    .build()
                MediaItem.Builder()
                    .setMediaId(item.id.toString())
                    .setUri(android.net.Uri.fromFile(File(item.localPath)))
                    .setMediaMetadata(metadata)
                    .build()
            }
            val startIndex = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            activePlayer.setMediaItems(queue, startIndex, 0L)
            activePlayer.prepare()
            activePlayer.play()
        }
    }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            if (!showFullPlayer && !vehicleMode) Column {
                val currentTrack = tracks.firstOrNull { it.id == playingTrackId }
                if (currentTrack != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp)
                            .background(Color(0xFF28203F), RoundedCornerShape(15.dp))
                            .clickable { showFullPlayer = true }.padding(horizontal = 13.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrackArtwork(currentTrack.artworkUrl, Modifier.size(42.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(currentTrack.title, fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 13.sp)
                            Text(currentTrack.artist ?: "Bilinmeyen sanatçı", color = Muted, fontSize = 11.sp, maxLines = 1)
                        }
                        IconButton(onClick = { player?.let { if (it.isPlaying) it.pause() else it.play() } }) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Oynat/Duraklat", tint = Accent)
                        }
                        IconButton(onClick = { player?.seekToNextMediaItem() }) {
                            Icon(Icons.Default.SkipNext, "Sonraki", tint = Color.White)
                        }
                    }
                }
                NavigationBar(containerColor = SurfaceDark) {
                val tabs = listOf(
                    "Keşfet" to Icons.Default.Explore,
                    "İndirmeler" to Icons.Default.Download,
                    "Kütüphane" to Icons.Default.LibraryMusic
                )
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(item.second, contentDescription = item.first) },
                        label = { Text(item.first) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Accent,
                            selectedTextColor = Accent,
                            indicatorColor = Accent.copy(alpha = 0.16f),
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
                }
            }
        }
    ) { padding ->
        if (showFullPlayer) {
            val currentTrack = tracks.firstOrNull { it.id == playingTrackId }
            FullPlayerScreen(
                player = player,
                title = player?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() } ?: currentTrack?.title ?: "Şimdi çalıyor",
                artist = player?.mediaMetadata?.artist?.toString()?.takeIf { it.isNotBlank() } ?: currentTrack?.artist ?: "Bilinmeyen sanatçı",
                isPlaying = isPlaying,
                artworkUrl = currentTrack?.artworkUrl ?: player?.mediaMetadata?.artworkUri?.toString(),
                onBack = { showFullPlayer = false }
            )
        } else when (selectedTab) {
            0 -> HomeScreen(
                modifier = Modifier.padding(padding),
                link = link,
                onLinkChange = { link = it; message = null; source = null; playlistTitle = null; playlistItems = emptyList() },
                title = title,
                onTitleChange = { title = it },
                onAnalyze = {
                    source = LinkParser.parse(link)
                    playlistTitle = null
                    playlistItems = emptyList()
                    if (source == MusicSource.YOUTUBE && com.kaan.melo.download.YoutubeAudioResolver.isPlaylistUrl(link)) {
                        resolvingPlaylist = true
                        message = "Oynatma listesi çözümleniyor..."
                        coroutineScope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    com.kaan.melo.download.YoutubeAudioResolver.resolvePlaylist(link.trim())
                                }
                                playlistTitle = result.title
                                playlistItems = result.items
                                message = if (result.items.isEmpty()) "Bu oynatma listesinde görüntülenebilir parça bulunamadı."
                                    else "${result.items.size} parça listelendi. İndirme hakkına sahip olduğun parçaları tek tek seçebilirsin."
                            } catch (error: Exception) {
                                message = "Playlist çözümlenemedi: ${error.message ?: "YouTube geçici olarak yanıt vermedi."}"
                            } finally {
                                resolvingPlaylist = false
                            }
                        }
                    } else {
                        message = when (source) {
                            null -> "Bu bağlantı tanınmadı. Doğrudan MP3, WAV, FLAC, M4A, OGG veya AAC dosyası deneyebilirsin."
                            MusicSource.DIRECT -> "Doğrudan ses dosyası algılandı. İndirmeyi başlatabilirsin."
                            MusicSource.YOUTUBE -> "YouTube videosu algılandı. Bağlantıyı çözümleyip işleme alabilirsin."
                            else -> "${source!!.displayName} bağlantısı tanındı. Bu servis için doğrudan ses dosyası indirme desteği bulunmuyor."
                        }
                    }
                },
                playlistTitle = playlistTitle,
                playlistItems = playlistItems,
                resolvingPlaylist = resolvingPlaylist,
                onDownloadAllPlaylist = {
                    playlistItems.forEach { track ->
                        DownloadScheduler.enqueue(context, track.url, track.title, track.artist)
                    }
                    message = "${playlistItems.size} parça indirme kuyruğuna eklendi."
                    selectedTab = 1
                },
                onDownloadPlaylistTrack = { track ->
                    DownloadScheduler.enqueue(
                        context = context,
                        url = track.url,
                        title = track.title,
                        artist = track.artist
                    )
                    message = "${track.title} indirme kuyruğuna eklendi."
                    selectedTab = 1
                },
                onDownload = {
                    DownloadScheduler.enqueue(
                        context = context,
                        url = link.trim(),
                        title = if (source == MusicSource.YOUTUBE) "Bilinmeyen parça" else title.ifBlank { link.substringAfterLast('/').substringBefore('?').ifBlank { "Melo parçası" } }
                    )
                    message = "İndirme kuyruğa eklendi. Durumunu İndirmeler sekmesinden takip edebilirsin."
                    selectedTab = 1
                },
                onOpenSource = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link.trim())))
                    }.onFailure {
                        message = "Bağlantı açılamadı. İlgili uygulamanın yüklü olduğundan emin ol."
                    }
                },
                message = message,
                source = source
            )
            1 -> DownloadsScreen(
                Modifier.padding(padding),
                jobs,
                onCancel = { job -> WorkManager.getInstance(context).cancelWorkById(job.id) }
            )
            else -> LibraryScreen(
                modifier = Modifier.padding(padding),
                tracks = tracks,
                playingTrackId = playingTrackId,
                isPlaying = isPlaying,
                playlists = playlists,
                selectedPlaylistId = selectedPlaylistId,
                selectedPlaylistTrackIds = selectedPlaylistTrackIds,
                libraryQuery = libraryQuery,
                onLibraryQueryChange = { libraryQuery = it },
                selectedLibrarySection = selectedLibrarySection,
                onSectionChange = { selectedLibrarySection = it },
                onSelectPlaylist = { selectedPlaylistId = it },
                onCreatePlaylistClick = { showCreatePlaylist = true },
                onAddToPlaylist = { playlistId, trackId ->
                    coroutineScope.launch { database.playlistDao().addTrack(com.kaan.melo.data.local.PlaylistTrack(playlistId, trackId)) }
                },
                onDeletePlaylist = { id ->
                    coroutineScope.launch { database.playlistDao().deletePlaylist(id) }
                    if (selectedPlaylistId == id) selectedPlaylistId = null
                },
                onPlay = ::playTrack,
                onBulkDelete = { ids ->
                    val selected = tracks.filter { it.id in ids }
                    selected.forEach { track ->
                        if (playingTrackId == track.id) { player?.stop(); playingTrackId = null; isPlaying = false }
                        File(track.localPath).delete()
                    }
                    coroutineScope.launch { database.downloadedTrackDao().deleteByIds(ids) }
                },
                onRemoveFromPlaylist = { playlistId, trackId ->
                    coroutineScope.launch { database.playlistDao().removeTrack(playlistId, trackId) }
                },
                onDelete = { track ->
                    if (playingTrackId == track.id) {
                        player?.stop()
                        playingTrackId = null
                        isPlaying = false
                    }
                    coroutineScope.launch {
                        database.downloadedTrackDao().deleteById(track.id)
                        File(track.localPath).delete()
                    }
                }
            )
        }
    }
    if (showCreatePlaylist) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false; newPlaylistName = "" },
            title = { Text("Yeni çalma listesi") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    singleLine = true,
                    label = { Text("Liste adı") },
                    colors = fieldColors()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newPlaylistName.trim()
                    if (name.isNotEmpty()) coroutineScope.launch {
                        database.playlistDao().create(com.kaan.melo.data.local.MusicPlaylist(name = name))
                    }
                    showCreatePlaylist = false
                    newPlaylistName = ""
                }) { Text("Oluştur") }
            },
            dismissButton = { TextButton(onClick = { showCreatePlaylist = false; newPlaylistName = "" }) { Text("İptal") } }
        )
    }

}

