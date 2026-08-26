# 0001: YouTube Music Streaming Support and Unified Library Architecture

PixelPlayer will support YouTube Music streaming directly using a client-side Kotlin Innertube extraction engine, routing audio streams through the embedded `CloudStreamProxy` for seamless playback, crossfades, and offline caching within a unified library model.

## Context

Users want to discover, stream, and sync YouTube Music tracks, albums, and playlists directly in PixelPlayer alongside their local audio collections without relying on third-party server proxies or switching between disjointed apps.

## Decision

1. **Extraction Engine**: Use an on-device, pure-Kotlin Innertube extractor to resolve metadata and expiring audio streams directly, eliminating dependency on external proxy infrastructure.
2. **Streaming Delivery**: Feed resolved audio streams through the local `CloudStreamProxy` to handle chunk range headers, automatic URL token renewal, and smooth integration with `DualPlayerEngine` crossfades.
3. **Unified Library with Source Switcher**: Integrate YouTube Music tracks, liked songs, and playlists directly into the primary `LibraryScreen` tabs and search results with an instant source switcher (`Unified` / `Local Only` / `YouTube Music`) and source badges (`Local`, `YouTube Music`, `Downloaded`).
4. **Data Persistence**: Add `YouTubeSongEntity`, `YouTubePlaylistEntity`, and `YouTubeDao` to `PixelPlayerDatabase` under an incremental Room schema migration (v6).
5. **Offline & Download Support**: Permit offline caching and explicit downloads for YouTube Music tracks via `CloudTrackDownloadWorker` and `OfflineTrackDao`.
6. **Authentication**: Support anonymous search and playback out-of-the-box, with an optional secure in-app WebView login to sync personal YouTube Music libraries and liked songs into encrypted storage.
7. **Lyrics**: Cascading lyrics resolution checking YouTube Music native lyrics first, falling back to `LrcLibApiService` for syllable/line synchronized timestamped lyrics.
8. **Full UI & Component Integration**: Fully integrate YouTube Music with all existing local UI components without separate player modes or UI forks—reusing the Full/Mini Player sheets, Wavy Seekbars, visualizers, Equalizer, Glance home screen widgets, dynamic Material You album-art palettes, Daily Mixes, and NLP Smart Playlist engines seamlessly across both local and YouTube audio.

## Considered Options

- **Piped / Invidious Proxy Instances**: Rejected due to reliance on external server availability, public instance rate limiting, and privacy trade-offs.
- **Siloed Dashboard Only**: Rejected in favor of a Unified Library with a fast Source Switcher to allow hybrid playlists and single-queue playback.
- **Direct ExoPlayer Stream URLs**: Rejected because expiring `googlevideo.com` tokens cause playback failure during long sessions and mid-crossfade transitions.

## Consequences

- The app remains 100% standalone and private without recurring server costs or proxy maintenance.
- Playback queue and playlists can mix local files and YouTube Music streams seamlessly.
- Audio playback benefits from PixelPlayer's existing audio processing pipeline (ReplayGain, Hi-Res cap, Equalizer, Surround downmix).
