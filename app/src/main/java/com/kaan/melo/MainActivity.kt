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
    ) { padding ->
        when (selectedTab) {
            0 -> HomeScreen(
                modifier = Modifier.padding(padding),
                link = link,
                onLinkChange = { link = it; message = null; source = null },
                title = title,
                onTitleChange = { title = it },
                onAnalyze = {
                    source = LinkParser.parse(link)
                    message = when (source) {
                        null -> "Bu bağlantı tanınmadı. Doğrudan MP3, WAV, FLAC, M4A, OGG veya AAC dosyası deneyebilirsin."
                        MusicSource.DIRECT -> "Doğrudan ses dosyası algılandı. İndirmeyi başlatabilirsin."
                        MusicSource.YOUTUBE -> "YouTube videosu algılandı. Ses akışı çözümlenip indirilecek."
                        else -> "${source!!.displayName} bağlantısı tanındı. Bu servis için doğrudan ses dosyası indirme desteği bulunmuyor."
                    }
                },
                onDownload = {
                    DownloadScheduler.enqueue(
                        context = context,
                        url = link.trim(),
                        title = title.ifBlank { link.substringAfterLast('/').substringBefore('?').ifBlank { "Melo parçası" } }
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
            1 -> DownloadsScreen(Modifier.padding(padding), jobs)
            else -> LibraryScreen(
                modifier = Modifier.padding(padding),
                tracks = tracks,
                playingTrackId = playingTrackId,
                isPlaying = isPlaying,
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
    source: MusicSource?
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
                if (message != null) {
                    Text(message, color = if (source == MusicSource.DIRECT) Color(0xFFB8A5FF) else Color(0xFFFFB4A9), fontSize = 13.sp)
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
                if (source == MusicSource.DIRECT || source == MusicSource.YOUTUBE) {
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
private fun DownloadsScreen(modifier: Modifier, jobs: List<WorkInfo>) {
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
                        if (job.state == WorkInfo.State.ENQUEUED || job.state == WorkInfo.State.BLOCKED) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
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
    onPlay: (DownloadedTrack) -> Unit,
    onDelete: (DownloadedTrack) -> Unit
) {
    Column(modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 26.dp)) {
        Text("Kütüphanem", fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text("${tracks.size} kayıtlı parça · çevrimdışı dinleme", color = Muted, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
        if (tracks.isEmpty()) {
            EmptyState(Icons.Default.LibraryMusic, "Kütüphanen boş", "İndirdiğin izinli ses dosyaları burada görünecek.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(tracks, key = { it.id }) { track ->
                    Row(
                        Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(16.dp)).padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(44.dp).background(Color(0xFF30264A), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.MusicNote, null, tint = Accent) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(track.artist ?: track.mimeType, color = Muted, fontSize = 12.sp, maxLines = 1)
                        }
                        IconButton(onClick = { onPlay(track) }) {
                            Icon(
                                if (playingTrackId == track.id && isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                contentDescription = "Çal",
                                tint = Accent
                            )
                        }
                        IconButton(onClick = { onDelete(track) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Sil", tint = Muted)
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
