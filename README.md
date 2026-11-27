# Melo

Melo, Android için geliştirilmiş çevrimdışı müzik kütüphanesi ve yerel ses oynatıcısıdır. Uygulama Kotlin, Jetpack Compose, Room, WorkManager ve AndroidX Media3 üzerine kuruludur.

## Mevcut özellikler

- **Yerel müzik kütüphanesi:** İndirilen ses dosyalarını Room veritabanında kayıtlı tutar; parça adı ve sanatçıya göre arama yapılabilir.
- **Çevrimdışı oynatma:** Media3 tabanlı oynatıcı ve uygulama içi mini oynatıcı.
- **YouTube bağlantı çözümleme:** Tek video ve herkese açık oynatma listesi bağlantılarından parça bilgilerini listeleme.
- **İndirme kuyruğu:** WorkManager ile arka plan indirmeleri, parça adı/sanatçı bilgisi, ilerleme yüzdesi ve devam eden işi iptal etme.
- **Toplu playlist kuyruğu:** Listelenen parçaları topluca indirme kuyruğuna ekleme.
- **Yerel çalma listeleri:** Liste oluşturma, şarkı ekleme, listeyi görüntüleme ve silme.
- **Kaynak tanıma:** YouTube, Spotify, Deezer ve doğrudan ses dosyası bağlantılarını tanıma.

## Kaynak ve kullanım sınırları

Bağlantı tanıma, her platformdan ses akışı çıkarıldığı anlamına gelmez. Spotify ve Deezer için DRM veya oturum kısıtlamaları aşılmaz. YouTube tarafındaki çözümleme/indirme davranışı platform koşullarına ve telif haklarına tabidir. Uygulamayı yalnızca indirme ve kullanma hakkına sahip olduğun içeriklerde kullan. Doğrudan MP3, WAV, FLAC, M4A, OGG ve AAC dosyaları desteklenen indirme biçimleridir.

## Teknoloji yığını

- Kotlin
- Jetpack Compose + Material 3
- Room
- WorkManager
- AndroidX Media3
- Minimum Android sürümü: API 26
- Derleme JDK'sı: 17

## Proje yapısı

```
app/src/main/java/com/kaan/melo/
├── core/                  # Bağlantı ve ortak altyapı
├── data/local/            # Room veritabanı ve DAO'lar
├── download/              # İndirme kuyruğu ve kaynak çözümleme
├── feature/
│   ├── home/              # Keşfet / bağlantı ekleme
│   ├── downloads/         # İndirme kuyruğu ve durumları
│   └── library/           # Yerel kütüphane ve playlist ekranı
├── ui/components/         # Paylaşılan Compose bileşenleri
├── MainActivity.kt        # Uygulama kabuğu, navigasyon ve durum bağlantısı
└── MeloPlaybackService.kt # Media3 oynatma servisi
```

## Derleme

Android Studio ile projeyi aç, Gradle senkronizasyonunu tamamla ve debug varyantını derle.

Komut satırından:

```bash
./gradlew :app:assembleDebug
```

APK çıktısı:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Bilinen geliştirme alanları

- Tam ekran “Şimdi çalıyor” ekranı ve gelişmiş oynatma kontrolleri
- İndirme kuyruğunda filtreleme ve daha ayrıntılı hata açıklamaları
- Ekranların ViewModel/state katmanlarına daha fazla ayrıştırılması
- Gerçek cihazlarda farklı Android sürümleri ve arka plan koşulları için test
