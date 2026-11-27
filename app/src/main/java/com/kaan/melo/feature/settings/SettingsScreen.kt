package com.kaan.melo.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaan.melo.Accent
import com.kaan.melo.Muted
import com.kaan.melo.SurfaceDark

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    statusMessage: String?,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit
) {
    Column(
        modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Ayarlar", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Durka ayarlarını ve çalma listelerini yönet.", color = Muted, fontSize = 13.sp)
        Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Yedekleme", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Çalma listelerin ve uygulama ayarların JSON dosyasına kaydedilir. Müzik dosyalarının kendisi yedeğe dahil değildir.",
                    color = Muted, fontSize = 12.sp
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = onExportBackup, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Text("  Yedek oluştur")
                }
                Button(onClick = onImportBackup, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Text("  Yedekten geri yükle")
                }
            }
        }
        if (!statusMessage.isNullOrBlank()) {
            Text(statusMessage, color = Accent, fontSize = 13.sp)
        }
        Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Durka", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Çevrimdışı müzik oynatıcı", color = Muted, fontSize = 12.sp)
                Text("Sürüm 1.0", color = Muted, fontSize = 11.sp)
            }
        }
    }
}
