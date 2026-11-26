# Tonarc Desktop (Linux)

Compose Multiplatform Desktop target for **Tonarc**, powered by `mpv` for high-resolution audio playback and packaged as a portable Linux **AppImage**.

## Features

- **Desktop-Responsive Layout**:
  - **Expanded (`>= 1040dp`)**: Collapsible left sidebar, adaptive library/discovery grids, persistent right-hand Now Playing / Queue / Synced Lyrics inspector pane.
  - **Medium (`640dp..1040dp`)**: Navigation rail + persistent bottom player bar + expandable full-screen player/lyrics/queue sheet.
  - **Compact (`< 640dp`)**: Bottom navigation bar + compact mini-player + full-screen player sheet.
- **Local Library**: Recursive folder scanning (`FLAC`, `MP3`, `M4A`, `AAC`, `OGG`, `OPUS`, `WAV`, `WebM`, `ALAC`) with embedded metadata, cover art caching, and companion `.lrc` / `.ttml` lyrics.
- **YouTube Music Streaming**: Reuses Tonarc's Innertube + NewPipeExtractor pipeline for discovery, search, album/artist/playlist browsing, infinite radio queues, and transcript lyrics.
- **Synchronized Lyrics**: Embedded tags, local `.lrc`/`.ttml`, YouTube transcripts, and LRCLIB lookup with click-to-seek.
- **10-Band Equalizer & DSP**: Real-time `mpv` `lavfi` equalizer presets, ReplayGain (`Off`, `Track`, `Album`), and playback speed control.
- **Keyboard Shortcuts**:
  - `Space`: Play / Pause
  - `Ctrl+F`: Focus Search
  - `Ctrl+O`: Add Music Folder
  - `Left` / `Right`: Seek -5s / +5s
  - `Ctrl+Left` / `Ctrl+Right`: Previous / Next track
  - `Ctrl+Up` / `Ctrl+Down`: Volume +5% / -5%
  - `M`: Mute / Unmute
  - `L` / `Q`: Toggle Lyrics / Queue inspector

## Runtime Requirement

- `mpv` installed on `PATH` (for audio output, e.g. `sudo apt install mpv`, `sudo pacman -S mpv`, or `sudo dnf install mpv`).

## Build & Package AppImage

```sh
./gradlew :desktop:test
./gradlew :desktop:createReleaseDistributable
./desktop/packaging/linux/build-appimage.sh
```

The resulting AppImage and `.sha256` checksum are written to `desktop/build/appimage/`.
