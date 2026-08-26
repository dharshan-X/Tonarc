# Design Specification: YouTube Library & Liked Music Sync (Phase 1)

**Author:** Tonarc Team  
**Date:** 2026-08-25  
**Status:** Approved  
**Target:** Android App (`com.quietrays.tonarc`)  

---

## 1. Overview

Tonarc allows users to connect their YouTube Music accounts via session cookies and tokens. This specification defines the architecture, data layer, UI components, and offline-first caching for syncing:
1. **User Liked Songs** (`FEmusic_liked_videos` / playlist `LM`).
2. **Personal & Saved YouTube Music Playlists** (`FEmusic_library_playlists`).
3. **Two-Way ❤️ Like/Unlike Synchronization** between Tonarc and remote YouTube Music accounts.

---

## 2. Architecture & Data Flow

```
┌─────────────────────────┐
│     Innertube API       │ (music.youtube.com/youtubei/v1)
│  • FEmusic_liked_videos │
│  • FEmusic_library_...  │
│  • like/like, removelike│
└────────────▲────────────┘
             │ (Authenticated SAPISID + Cookies)
┌────────────┴────────────┐
│   InnertubeApiService   │
└────────────▲────────────┘
             │
┌────────────┴──────────────────────┐
│     YouTubeLibrarySyncEngine      │
│  • Deduplication & Pagination     │
│  • Sync StateFlow (Idle/Syncing)  │
└────────────▲──────────────────────┘
             │
┌────────────▼──────────────────────┐
│      Room SQLite Database         │
│  • PlaylistEntity (source=YOUTUBE)│
│  • SongEntity (is_cloud=true)     │
│  • MusicDao                       │
└────────────▲──────────────────────┘
             │
┌────────────┴────────────┐
│    LibraryViewModel     │ ──► LibraryScreen (Jetpack Compose UI)
│    PlayerViewModel      │ ──► NowPlayingScreen (❤️ Like Sync)
└─────────────────────────┘
```

---

## 3. Detailed Component Specifications

### 3.1 `InnertubeApiService` Extensions
- **`getLikedSongs(continuation: String? = null): Pair<List<InnertubeTrack>, String?>`**:
  - Request endpoint: `browse` with `browseId = "FEmusic_liked_videos"`.
  - Parses response into `List<InnertubeTrack>` and extracts pagination continuation token.
- **`getUserPlaylists(continuation: String? = null): Pair<List<InnertubePlaylist>, String?>`**:
  - Request endpoint: `browse` with `browseId = "FEmusic_library_playlists"`.
  - Parses user-created and saved playlists into `List<InnertubePlaylist>`.
- **`setLikeStatus(videoId: String, isLiked: Boolean): Boolean`**:
  - Request endpoint: `like/like` (if `isLiked == true`, `target = { videoId: videoId }`) or `like/removelike` (`target = { videoId: videoId }`).
  - Headers: `Authorization: SAPISIDHASH ...`, `Cookie: ...`.
  - Returns `true` on HTTP 200 success.

### 3.2 `InnertubeParser` Extensions
- **`parseLikedSongs(responseJson: String): Pair<List<InnertubeTrack>, String?>`**:
  - Traverses `contents.singleColumnBrowseResultsRenderer.tabs[0].tabRenderer.content.sectionListRenderer.contents`.
  - Extracts track metadata (`videoId`, `title`, `artists`, `duration`, `thumbnailUrl`).
- **`parseLibraryPlaylists(responseJson: String): Pair<List<InnertubePlaylist>, String?>`**:
  - Traverses grid/musicShelf items for playlist title, browseId/playlistId, track count string, and thumbnail URLs.

### 3.3 `YouTubeLibrarySyncEngine`
- **Location:** `app/src/main/java/com/quietrays/tonarc/data/network/youtube/YouTubeLibrarySyncEngine.kt`
- **Responsibilities:**
  1. Coordinates fetching Liked Music and personal playlists from `InnertubeApiService`.
  2. Maps `InnertubeTrack` to `SongEntity` with `source = "YOUTUBE"` and `is_cloud = true`.
  3. Maps `InnertubePlaylist` to `PlaylistEntity` with `source = "YOUTUBE"`.
  4. Manages the special pinned "Liked Music" playlist with ID `ytm_liked_music`.
  5. Exposes `val syncState: StateFlow<SyncState>` (`Idle`, `Syncing(progress)`, `Success`, `Error(message)`).

### 3.4 Presentation & UI
- **Library Screen (`LibraryScreen.kt`)**:
  - Shows pinned "❤️ Liked Music" card at top of playlists when YouTube is connected.
  - Displays cloud playlists unified with local playlists, decorated with a subtle YouTube badge.
  - Pull-to-refresh initiates `YouTubeLibrarySyncEngine.sync()`.
- **Player & Track Menus**:
  - Tapping ❤️ dispatches optimistic UI update, records local favorite in Room, and launches background coroutine to call `setLikeStatus(videoId, true/false)`.
  - Automatically rolls back and shows snackbar if remote sync fails after retries.

---

## 4. Error Handling & Edge Cases

| Scenario | Handling Strategy |
| :--- | :--- |
| **No Cookies / Anonymous** | Library shows local playlists; displays non-intrusive card offering YouTube connection. |
| **Offline / Airplane Mode** | Displays all cached YouTube playlists and tracks from SQLite. No blocking network calls. |
| **Session Expired (401/403)** | Preserves existing cached data; emits `SyncState.Error("Session expired")` with re-auth prompt. |
| **Paging Continuation Failure** | Gracefully completes sync with all tracks fetched up to the failure point. |
| **Duplicate Tracks / Conflict** | SQLite `OnConflictStrategy.REPLACE` on `(id, source)` or upsert pattern. |

---

## 5. Verification & Testing

1. **Unit Tests**:
   - `InnertubeApiServiceTest`: Mock Liked Songs and Library Playlists responses.
   - `InnertubeParserTest`: Test parser against valid and malformed JSON payloads.
   - `YouTubeLibrarySyncEngineTest`: Test upsert, deduplication, and state flow transitions.
2. **Build & Test Commands**:
   - `./gradlew :app:testDebugUnitTest --no-daemon`
   - `./gradlew assembleDebug --no-daemon`
