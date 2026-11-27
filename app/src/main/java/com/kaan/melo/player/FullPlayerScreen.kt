package com.kaan.melo.player

import android.media.audiofx.Equalizer
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import kotlinx.coroutines.delay
import com.kaan.melo.ui.components.TrackArtwork

private val PlayerAccent = Color(0xFFB49AFF)

@Composable
fun FullPlayerScreen(
    player: MediaController?,
    title: String,
    artist: String,
    isPlaying: Boolean,
    artworkUrl: String?,
    audioSessionId: Int,
    onBack: () -> Unit
) {
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var shuffle by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var showQueueDialog by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var queueItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var equalizerRefresh by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val equalizer = remember(audioSessionId) {
        if (audioSessionId > 0) runCatching {
            Equalizer(0, audioSessionId).apply {
                enabled = true
                val prefs = context.getSharedPreferences("durka_settings", android.content.Context.MODE_PRIVATE)
                for (band in 0 until numberOfBands.toInt()) {
                    val saved = prefs.getInt("eq_band_" + band, 0)
                    setBandLevel(band.toShort(), saved.coerceIn(bandLevelRange[0].toInt(), bandLevelRange[1].toInt()).toShort())
                }
            }
        }.getOrNull() else null
    }
    DisposableEffect(equalizer) {
        onDispose { runCatching { equalizer?.release() } }
    }
    LaunchedEffect(player) {
        while (true) {
            queueItems = player?.let { active ->
                (0 until active.mediaItemCount).mapNotNull { index -> runCatching { active.getMediaItemAt(index) }.getOrNull() }
            }.orEmpty()
            delay(600)
        }
    }

    LaunchedEffect(sleepTimerMinutes, player) {
        if (sleepTimerMinutes > 0) {
            kotlinx.coroutines.delay(sleepTimerMinutes * 60_000L)
            player?.pause()
            sleepTimerMinutes = 0
        }
    }

    LaunchedEffect(player) {
        while (true) {
            player?.let {
                duration = it.duration.coerceAtLeast(0L)
                if (!dragging) {
                    position = it.currentPosition.coerceAtLeast(0L)
                    sliderPosition = if (duration > 0) position.toFloat() / duration else 0f
                }
                shuffle = it.shuffleModeEnabled
                repeatMode = it.repeatMode
            }
            delay(400)
        }
    }

    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF30234D), Color(0xFF15131F), Color(0xFF09090F)))
        ).padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, "Kapat", tint = Color.White) }
            Spacer(Modifier.weight(1f))
            Text("ŞİMDİ ÇALIYOR", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { showMoreMenu = true }) {
                    Icon(Icons.Default.MoreVert, "Diğer", tint = Color.White)
                }
                DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Çalma sırası") },
                        onClick = { showMoreMenu = false; showQueueDialog = true }
                    )
                    DropdownMenuItem(
                        text = { Text("Ekolayzır") },
                        onClick = { showMoreMenu = false; showEqualizerDialog = true }
                    )
                    DropdownMenuItem(
                        text = { Text(if (sleepTimerMinutes > 0) "Uyku zamanlayıcısı: ${sleepTimerMinutes} dk" else "Uyku zamanlayıcısı") },
                        onClick = { showMoreMenu = false; showSleepDialog = true }
                    )
                    if (sleepTimerMinutes > 0) {
                        DropdownMenuItem(
                            text = { Text("Zamanlayıcıyı iptal et") },
                            onClick = { sleepTimerMinutes = 0; showMoreMenu = false }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(0.7f))
        TrackArtwork(
            artworkUrl = artworkUrl,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
        )
        Spacer(Modifier.height(32.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(artist, color = Color(0xFFB7B2C6), fontSize = 15.sp, maxLines = 1)
        }
        Spacer(Modifier.height(24.dp))
        Slider(
            value = sliderPosition.coerceIn(0f, 1f),
            onValueChange = {
                dragging = true
                sliderPosition = it
            },
            onValueChangeFinished = {
                val target = (sliderPosition * duration).toLong().coerceIn(0L, duration.coerceAtLeast(0L))
                player?.seekTo(target)
                position = target
                dragging = false
            },
            colors = SliderDefaults.colors(thumbColor = PlayerAccent, activeTrackColor = PlayerAccent, inactiveTrackColor = Color(0xFF504A5D))
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), color = Color.LightGray, fontSize = 11.sp)
            Text(formatTime(duration), color = Color.LightGray, fontSize = 11.sp)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { player?.shuffleModeEnabled = !shuffle }) {
                Icon(Icons.Default.Shuffle, "Karıştır", tint = if (shuffle) PlayerAccent else Color.LightGray)
            }
            IconButton(onClick = { player?.seekToPreviousMediaItem() }, modifier = Modifier.size(54.dp)) {
                Icon(Icons.Default.SkipPrevious, "Önceki", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            FilledIconButton(
                onClick = { player?.let { if (it.isPlaying) it.pause() else it.play() } },
                modifier = Modifier.size(70.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = PlayerAccent, contentColor = Color(0xFF171321))
            ) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Oynat/Duraklat", modifier = Modifier.size(38.dp))
            }
            IconButton(onClick = { player?.seekToNextMediaItem() }, modifier = Modifier.size(54.dp)) {
                Icon(Icons.Default.SkipNext, "Sonraki", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = {
                val next = when (repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                player?.repeatMode = next
            }) {
                Icon(
                    if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    "Tekrar",
                    tint = if (repeatMode == Player.REPEAT_MODE_OFF) Color.LightGray else PlayerAccent
                )
            }
        }
        Spacer(Modifier.weight(0.9f))
        Text(
            if (sleepTimerMinutes > 0) "DURKA • $sleepTimerMinutes DK SONRA DURACAK" else "DURKA • ÇEVRİMDIŞI MÜZİK",
            color = Color(0xFF777184), fontSize = 10.sp, letterSpacing = 1.5.sp
        )
        if (showQueueDialog) {
            AlertDialog(
                onDismissRequest = { showQueueDialog = false },
                title = { Text("Çalma sırası (${queueItems.size})") },
                text = {
                    if (queueItems.isEmpty()) Text("Sırada şarkı yok.")
                    else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(queueItems.size) { index ->
                            val item = queueItems[index]
                            val isCurrent = player?.currentMediaItemIndex == index
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.mediaMetadata.title?.toString() ?: "Bilinmeyen parça", color = if (isCurrent) PlayerAccent else Color.White, maxLines = 1)
                                    Text(item.mediaMetadata.artist?.toString() ?: "", color = Color.LightGray, fontSize = 11.sp, maxLines = 1)
                                }
                                IconButton(onClick = { if (index > 0) player?.moveMediaItem(index, index - 1) }, enabled = index > 0) {
                                    Icon(Icons.Default.KeyboardArrowUp, "Yukarı taşı")
                                }
                                IconButton(onClick = { if (index < queueItems.lastIndex) player?.moveMediaItem(index, index + 1) }, enabled = index < queueItems.lastIndex) {
                                    Icon(Icons.Default.KeyboardArrowDown, "Aşağı taşı")
                                }
                                IconButton(onClick = { player?.removeMediaItem(index) }) {
                                    Icon(Icons.Default.Close, "Sıradan çıkar", tint = Color.LightGray)
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showQueueDialog = false }) { Text("Tamam") } }
            )
        }
        if (showEqualizerDialog) {
            AlertDialog(
                onDismissRequest = { showEqualizerDialog = false },
                title = { Text("Ekolayzır") },
                text = {
                    if (equalizer == null) {
                        Text("Bu cihazda ses efektine erişilemiyor. Müziği başlatıp tekrar dene.")
                    } else {
                        val bandRange = equalizer.bandLevelRange
                        val bandCount = equalizer.numberOfBands.toInt()
                        Column {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = {
                                    for (band in 0 until bandCount) {
                                        equalizer.setBandLevel(band.toShort(), 0)
                                        context.getSharedPreferences("durka_settings", android.content.Context.MODE_PRIVATE).edit().putInt("eq_band_" + band, 0).apply()
                                    }
                                    equalizerRefresh++
                                }) { Text("Düz") }
                                TextButton(onClick = {
                                    for (band in 0 until bandCount) {
                                        val boost = if (band < bandCount / 2) 500 else -100
                                        equalizer.setBandLevel(band.toShort(), boost.toShort())
                                        context.getSharedPreferences("durka_settings", android.content.Context.MODE_PRIVATE).edit().putInt("eq_band_" + band, boost).apply()
                                    }
                                    equalizerRefresh++
                                }) { Text("Bas") }
                                TextButton(onClick = {
                                    for (band in 0 until bandCount) {
                                        equalizer.setBandLevel(band.toShort(), 250)
                                        context.getSharedPreferences("durka_settings", android.content.Context.MODE_PRIVATE).edit().putInt("eq_band_" + band, 250).apply()
                                    }
                                    equalizerRefresh++
                                }) { Text("Canlı") }
                            }
                            key(equalizerRefresh) {
                                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                                    items(bandCount) { band ->
                                        val centerHz = equalizer.getCenterFreq(band.toShort()) / 1000
                                        val current = equalizer.getBandLevel(band.toShort()).toFloat() / 1000f
                                        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                            Text("${centerHz} Hz", color = Color.LightGray, fontSize = 11.sp)
                                            Slider(
                                                value = current.coerceIn(bandRange[0] / 1000f, bandRange[1] / 1000f),
                                                onValueChange = { value ->
                                                    equalizer.setBandLevel(band.toShort(), (value * 1000).toInt().toShort())
                                                    context.getSharedPreferences("durka_settings", android.content.Context.MODE_PRIVATE).edit().putInt("eq_band_" + band, (value * 1000).toInt()).apply()
                                                    equalizerRefresh++
                                                },
                                                valueRange = bandRange[0] / 1000f..bandRange[1] / 1000f
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showEqualizerDialog = false }) { Text("Kapat") } }
            )
        }
        if (showSleepDialog) {
            AlertDialog(
                onDismissRequest = { showSleepDialog = false },
                title = { Text("Uyku zamanlayıcısı") },
                text = { Text("Müzik ne kadar süre sonra dursun?") },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(15, 30, 45, 60).forEach { minutes ->
                            TextButton(onClick = {
                                sleepTimerMinutes = minutes
                                showSleepDialog = false
                            }) { Text("$minutes") }
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSleepDialog = false }) { Text("Vazgeç") }
                }
            )
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
