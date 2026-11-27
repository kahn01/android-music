package com.kaan.melo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaan.melo.core.links.LinkParser
import com.kaan.melo.core.links.MusicSource

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
    var selectedTab by remember { mutableIntStateOf(0) }
    var link by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf<MusicSource?>(null) }

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
                Modifier.padding(padding), link, { link = it; message = null; source = null },
                {
                    source = LinkParser.parse(link)
                    message = if (source == null) "Desteklenen bir bağlantı algılanamadı."
                    else source!!.displayName + " bağlantısı tanındı. İçerik bilgileri için desteklenen API erişimi gerekir."
                }, message, source
            )
            1 -> EmptyScreen(Modifier.padding(padding), "İndirmeler", "İndirme kuyruğun burada görünecek.", Icons.Default.Download)
            else -> EmptyScreen(Modifier.padding(padding), "Kütüphanem", "Telefondaki müziklerin burada listelenecek.", Icons.Default.LibraryMusic)
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    link: String,
    onLinkChange: (String) -> Unit,
    onAnalyze: () -> Unit,
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
                IconButton(onClick = {}) { Icon(Icons.Default.Settings, "Ayarlar", tint = Muted) }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Müziğini keşfet.", fontSize = 31.sp, fontWeight = FontWeight.Bold)
                Text("Bağlantıyı ekle, içerik bilgilerini bul ve desteklenen sesleri kütüphanene kaydet.", color = Muted, fontSize = 14.sp)
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
                    placeholder = { Text("YouTube, Spotify veya Deezer linki", color = Muted) },
                    minLines = 2,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Color(0xFF3A3548),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Accent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAnalyze() })
                )
                Button(
                    onClick = onAnalyze,
                    enabled = link.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Icon(Icons.Default.Search, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Bağlantıyı analiz et", fontWeight = FontWeight.Bold)
                }
                if (message != null) {
                    Text(message, color = if (source != null) Color(0xFFB8A5FF) else Color(0xFFFF8A8A), fontSize = 13.sp)
                }
                if (source != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF8DD9AE))
                        Spacer(Modifier.width(8.dp))
                        Text("Kaynak: " + source.displayName, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            Text("DESTEKLENEN KAYNAKLAR", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                SourceChip("YouTube", "YT")
                SourceChip("Spotify", "S")
                SourceChip("Deezer", "D")
            }
        }
        item {
            Text("Hızlı erişim", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickCard("İndirmeler", Icons.Default.Download, Modifier.weight(1f))
                QuickCard("Kütüphanem", Icons.Default.LibraryMusic, Modifier.weight(1f))
            }
        }
    }
}

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
private fun QuickCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Column(
        modifier.background(SurfaceDark, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(23.dp))
        Text(title, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyScreen(
    modifier: Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(54.dp))
        Spacer(Modifier.height(18.dp))
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = Muted)
    }
}
