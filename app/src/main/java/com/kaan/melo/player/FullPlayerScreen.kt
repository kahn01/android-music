package com.kaan.melo.player

import androidx.compose.foundation.background
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
import androidx.media3.common.Player
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
    onBack: () -> Unit
) {
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var shuffle by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }

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
            IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, "Diğer", tint = Color.White) }
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
        Text("MELO • ÇEVRİMDIŞI MÜZİK", color = Color(0xFF777184), fontSize = 10.sp, letterSpacing = 1.5.sp)
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
