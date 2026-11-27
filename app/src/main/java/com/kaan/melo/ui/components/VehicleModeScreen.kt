package com.kaan.melo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.kaan.melo.data.local.DownloadedTrack

@Composable
fun VehicleModeScreen(
    player: Player?,
    currentTrack: DownloadedTrack?,
    nextTrack: DownloadedTrack?,
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit
) {
    var volume by remember(player) { mutableFloatStateOf(player?.volume ?: 1f) }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF080B10)).padding(horizontal = 22.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MELO • ARAÇ MODU", color = Color(0xFFB9A5FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Sürüş sırasında kolay kontrol", color = Color(0xFF9AA4B2), fontSize = 12.sp)
            }
            FilledTonalIconButton(onClick = onExit, modifier = Modifier.size(58.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Araç modundan çık", modifier = Modifier.size(28.dp))
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TrackArtwork(currentTrack?.artworkUrl, Modifier.fillMaxWidth(0.76f).aspectRatio(1f), iconSize = 72.dp)
            Text(
                currentTrack?.title ?: "Henüz parça çalmıyor",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Text(
                currentTrack?.artist ?: "Kütüphanenden bir şarkı başlat",
                color = Color(0xFFADB4C0),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            if (nextTrack != null) {
                Text("Sıradaki: ${nextTrack.title}", color = Color(0xFF778392), fontSize = 13.sp, maxLines = 1)
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(onClick = onPrevious, modifier = Modifier.size(76.dp)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Önceki parça", modifier = Modifier.size(38.dp))
            }
            Button(
                onClick = onPlayPause,
                modifier = Modifier.size(104.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Duraklat" else "Oynat",
                    modifier = Modifier.size(54.dp)
                )
            }
            FilledTonalIconButton(onClick = onNext, modifier = Modifier.size(76.dp)) {
                Icon(Icons.Default.SkipNext, contentDescription = "Sonraki parça", modifier = Modifier.size(38.dp))
            }
        }

        Row(
            Modifier.fillMaxWidth().background(Color(0xFF151B24), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.VolumeDown, contentDescription = "Sesi azalt", tint = Color(0xFFB9A5FF))
            Slider(
                value = volume.coerceIn(0f, 1f),
                onValueChange = {
                    volume = it
                    player?.volume = it
                },
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
            )
            Icon(Icons.Default.VolumeUp, contentDescription = "Sesi artır", tint = Color(0xFFB9A5FF))
        }
    }
}
