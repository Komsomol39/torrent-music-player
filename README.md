# Torrent Music Player

Android music player with torrent search and offline playback.

## Features

- **Beautiful player UI** — full-screen player, mini player, album art
- **Library management** — browse, search, favorites, play counts
- **Torrent search** — find music on torrent sites
- **Torrent download** — download via magnet links
- **Offline playback** — listen without internet after download
- **Media3/ExoPlayer** — background playback, notification controls, headphone support

## Architecture

```
app/
  data/
    db/          — Room database (tracks, playlists)
  domain/
    model/       — Track, Playlist, TorrentResult, PlayerState
  player/
    MusicService       — Media3 MediaSessionService
    PlayerController   — ExoPlayer wrapper + state flow
  ui/
    screens/
      library/   — Track list
      player/    — Full-screen player
      search/    — Local search
      torrent/   — Torrent search + download
    components/  — MiniPlayer, AlbumArtwork
    theme/       — Dark purple Material3 theme
  di/
    AppModule    — Hilt DI
```

## Tech stack

| | |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Player | Media3 / ExoPlayer |
| DI | Hilt |
| DB | Room |
| Images | Coil |
| Network | Retrofit + OkHttp |
| Architecture | MVVM + StateFlow |

## Status

- [x] Player UI (full screen + mini player)
- [x] Library screen
- [x] Local search
- [x] Dark theme
- [x] Media3 background service
- [ ] Torrent search implementation (stub ready)
- [ ] Torrent download (magnet → files)
- [ ] Equalizer
- [ ] Sleep timer
- [ ] Lyrics

## Build

```bash
git clone https://github.com/Komsomol39/torrent-music-player
# Open in Android Studio
# Run on device/emulator with API 26+
```
