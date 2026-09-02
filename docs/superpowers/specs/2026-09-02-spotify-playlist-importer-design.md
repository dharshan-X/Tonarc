# Spotify Playlist Importer Design Specification

- **Feature Name**: Spotify Playlist Importer & Universal Track Matching
- **Author**: Antigravity Agent
- **Date**: 2026-09-02
- **Status**: Approved

---

## 1. Overview & Objective

Provide a zero-login, privacy-friendly playlist import mechanism allowing users to paste any public Spotify playlist URL or URI (or share via Android Share Sheet) into Tonarc. Tonarc extracts the complete metadata (Title, Description, Author, Cover Art, Track List) and matches each track against local files and YouTube Music cloud streams, persisting the result as an instantly streamable and downloadable playlist.

---

## 2. Architectural Design

```
[ User Input / Android ACTION_SEND ]
              │
              ▼
  ┌───────────────────────────────┐
  │   SpotifyPlaylistFetcher      │ ◄── Fetches playlist name, cover, & tracks
  │  (Zero-login Anonymous API    │     via Spotify public client API
  │   + embed scraper fallback)   │
  └──────────────┬────────────────┘
                 │ List of Spotify Tracks (Title, Artist, Album, Duration)
                 ▼
  ┌───────────────────────────────┐
  │     SpotifyMatchingEngine     │
  │                               │
  │  ├── 1. Local Library Match   │ ◄── Matches existing local MP3/FLAC files
  │  └── 2. Cloud Stream Match    │ ◄── Resolves YouTube Music audio streams
  └──────────────┬────────────────┘
                 │
                 ▼
  ┌───────────────────────────────┐
  │       Playlist Creation       │ ◄── Saves as Cloud Playlist (Instant Streamable)
  │    (Room SQLite + Prefs)      │     and/or Local Playlist in Tonarc Library
  └───────────────────────────────┘
```

---

## 3. Detailed Component Specifications

### 3.1 `SpotifyPlaylistFetcher.kt` (`data/network/spotify/`)
* **URL Extraction**:
  * Matches `https://open.spotify.com/playlist/{id}` (handling query params like `?si=...`).
  * Matches `spotify:playlist:{id}`.
* **Token Resolution**:
  * Queries `https://open.spotify.com/get_access_token?reason=transport&productType=web_player`.
  * Caches the anonymous `accessToken` in memory with expiration tracking (`accessTokenExpirationTimestampMs`).
* **Metadata & Tracks Endpoint**:
  * Calls `https://api.spotify.com/v1/playlists/{playlistId}` with Bearer token.
  * Paginates through track items if `tracks.next` exists (`limit = 100`).
  * Extracts:
    * `SpotifyPlaylist(id, title, description, author, coverUri, trackCount, tracks)`
    * `SpotifyTrack(id, title, artist, artists, album, durationMs, coverUri)`
* **Fallback Embed Scraper**:
  * If API is unreachable/rate-limited, fetches `https://open.spotify.com/embed/playlist/{playlistId}` and parses `<script id="__NEXT_DATA__">` or oEmbed JSON.

### 3.2 `SpotifyMatchingEngine.kt` (`data/spotify/`)
* Injected with `@Singleton`:
  * `innertubeApiService: InnertubeApiService`
  * `musicRepository: MusicRepository`
  * `youTubeDao: YouTubeDao`
* **Matching Logic**:
  * **Phase 1 (Local Match)**: Searches local library for exact/fuzzy title and artist match within $\pm 7$s duration tolerance.
  * **Phase 2 (Cloud Match)**: If no local match or when saving as cloud playlist, queries `innertubeApiService.search("${track.title} ${track.artist}")` and picks the best matching audio track.
  * Provides progress updates via a callback or flow for responsive UI progress indication.

### 3.3 ViewModel & State (`PlaylistViewModel.kt`)
* Sealed interface `SpotifyImportState`:
  * `data object Idle : SpotifyImportState`
  * `data class Loading(val message: String) : SpotifyImportState`
  * `data class Preview(val playlist: SpotifyPlaylist, val songs: List<SpotifyTrack>) : SpotifyImportState`
  * `data class Matching(val current: Int, val total: Int, val currentSong: String) : SpotifyImportState`
  * `data class Success(val title: String, val matchedCount: Int, val totalCount: Int) : SpotifyImportState`
  * `data class Error(val message: String) : SpotifyImportState`
* StateFlow: `val spotifyImportState: StateFlow<SpotifyImportState>`
* Actions:
  * `previewSpotifyPlaylist(urlOrId: String)`
  * `saveSpotifyPlaylist(playlist: SpotifyPlaylist, customTitle: String?, saveAsCloud: Boolean, saveAsLocal: Boolean)`
  * `resetSpotifyImportState()`

### 3.4 UI Components (`ImportSpotifyPlaylistDialog.kt`)
* Material 3 Expressive dialog:
  * Green accent Spotify branding badge.
  * URL text field with clipboard paste icon.
  * Preview card with high-resolution cover art, title, creator name, track count.
  * Editable text field for custom library playlist name.
  * Checkboxes: "Save as Cloud Streamable Playlist" (default checked) and "Match with Local Library".
  * Real-time progress bar during track matching.

### 3.5 Integration Points
* **Library Screen**: "Import from Spotify" option in the playlist overflow menu and FAB options.
* **Create Playlist Dialog**: Option to import from Spotify.
* **Android Share Sheet (`MainActivity.kt`)**: Catch `Intent.ACTION_SEND` containing `open.spotify.com/playlist/...` and auto-open `ImportSpotifyPlaylistDialog`.

---

## 4. Verification & Testing Plan

1. **Unit Tests**:
   * `SpotifyPlaylistFetcherTest.kt`: Test URL ID extraction, anonymous token parsing, playlist response parsing, and embed scraper fallback.
   * `SpotifyMatchingEngineTest.kt`: Test local song matching tolerance, cloud candidate resolution, and missing track handling.
2. **End-to-End Tests**:
   * Test sharing Spotify link from clipboard and share intent.
   * Test importing a 50+ song playlist and saving as streamable cloud playlist.
3. **Build Verification**:
   * `./gradlew :app:testDebugUnitTest --no-daemon`
   * `./gradlew assembleDebug --no-daemon`
