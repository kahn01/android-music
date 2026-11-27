package com.kaan.melo

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

private val Background = Color(0xFF09090F)
private val SurfaceDark = Color(0xFF171722)
private val Accent = Color(0xFF9B7BFF)
private val Muted = Color(0xFF9291A3)

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
    LaunchedEffect(selectedPlaylistId) {
        val id = selectedPlaylistId
        if (id == null) selectedPlaylistTrackIds = emptySet()
        else database.playlistDao().observePlaylistTracks(id).collect { rows ->
            selectedPlaylistTrackIds = rows.map { it.trackId }.toSet()
        }
    }

    DisposableEffect(player) {
        val activePlayer = player
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) isPlaying = false
            }
        }
        activePlayer?.addListener(listener)
        onDispose { activePlayer?.removeListener(listener) }
    }

    fun playTrack(track: DownloadedTrack) {
        val activePlayer = player ?: return
        if (playingTrackId == track.id) {
            if (activePlayer.isPlaying) activePlayer.pause() else activePlayer.play()
        } else {
            playingTrackId = track.id
            val queue = tracks.map { item ->
                val metadata = MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(item.artist ?: "Melo")
                    .build()
                MediaItem.Builder()
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
            Column {
                val currentTrack = tracks.firstOrNull { it.id == playingTrackId }
                if (currentTrack != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp)
                            .background(Color(0xFF28203F), RoundedCornerShape(15.dp))
                            .clickable { selectedTab = 2 }.padding(horizontal = 13.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(38.dp).background(Color(0xFF49366F), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MusicNote, null, tint = Accent)
                        }
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
        when (selectedTab) {
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
                onSelectPlaylist = { selectedPlaylistId = it; selectedLibrarySection = "Şarkılar" },
                onCreatePlaylistClick = { showCreatePlaylist = true },
                onAddToPlaylist = { playlistId, trackId ->
                    coroutineScope.launch { database.playlistDao().addTrack(com.kaan.melo.data.local.PlaylistTrack(playlistId, trackId)) }
                },
                onDeletePlaylist = { id ->
                    coroutineScope.launch { database.playlistDao().deletePlaylist(id) }
                    if (selectedPlaylistId == id) selectedPlaylistId = null
                },
                onPlay = ::playTrack,
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

@Composable
private fun HomeScreen(
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

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = Color(0xFF3A3548),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = Accent
)

@Composable
private fun SourceChip(name: String, mark: String) {
    Row(
        Modifier.background(SurfaceDark, CircleShape).padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(mark, color = Accent, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        Spacer(Modifier.width(7.dp))
        Text(name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DownloadsScreen(
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
                            Text(job.workSpecIdShort(), fontWeight = FontWeight.SemiBold)
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

private fun WorkInfo.workSpecIdShort(): String = "Ses dosyası · ${id.toString().take(8)}"

@Composable
private fun LibraryScreen(
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
                onClick = { onSectionChange("Çalma listeleri"); onSelectPlaylist(null); onCreatePlaylistClick() },
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
                        Row(Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(15.dp)).clickable { onSelectPlaylist(playlist.id) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
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

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(SurfaceDark, RoundedCornerShape(15.dp)).padding(12.dp)) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 70.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(52.dp))
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}
