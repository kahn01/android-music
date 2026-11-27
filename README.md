# Melo

Melo is an Android-first, on-device music library and permitted-audio downloader.

## Current status

Initial Kotlin + Jetpack Compose scaffold:
- Dark Compose UI and bottom navigation
- URL host recognition for YouTube, Spotify, Deezer and direct audio-file URLs
- Dependencies prepared for Media3, WorkManager, Room, DataStore and Retrofit
- Minimum Android version: API 26

**Important:** URL recognition is not stream extraction. Melo does not bypass DRM, paywalls, authentication or platform download restrictions. Metadata integrations must use supported APIs and user-authorized access. Downloads and MP3 conversion are intended for direct files and sources the user is authorized to download or convert.

## Build

Open this repository in Android Studio with JDK 17 and Android SDK 35, sync Gradle, then run the app.

Build command (with Gradle installed):

    gradle :app:assembleDebug

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Architecture

- `core/links`: URL parsing and source identification
- `core/network`: future API clients
- `core/download`: future WorkManager download pipeline
- `core/database`: future Room persistence
- `core/player`: future Media3 playback
- `feature`: Compose screens

## Roadmap

1. URL parsing and validation
2. Supported metadata providers and OAuth where required
3. Permission-aware direct audio download queue
4. Local metadata database and media library
5. Media3 playback and media-session controls
6. Maintained Android-compatible FFmpeg integration for authorized local conversions
