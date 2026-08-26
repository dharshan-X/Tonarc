# Design Specification: Auto-Lyrics & Multi-Source Karaoke Aggregation (Phase 3)

**Author:** Tonarc Team  
**Date:** 2026-08-26  
**Status:** Approved  
**Target:** Android App (`com.quietrays/tonarc`)  

---

## 1. Overview

Phase 3 introduces automated, multi-source synchronized lyrics resolution across LRCLIB and YouTube Music Timed-Text transcripts. It features instant background preloading when a song begins playback, persistent SQLite caching for offline karaoke access, and multi-candidate manual search.

---

## 2. Multi-Source Pipeline

```
                               ┌───────────────────────────┐
                               │     Playback Trigger      │
                               │   (Song Starts Playing)   │
                               └─────────────┬─────────────┘
                                             │
                                             ▼
                               ┌───────────────────────────┐
                               │     LyricsStateHolder     │
                               │  (Background Pre-fetch)   │
                               └─────────────┬─────────────┘
                                             │
                                             ▼
                               ┌───────────────────────────┐
                               │   LyricsRepositoryImpl    │
                               └─────────────┬─────────────┘
                                             │
                     ┌───────────────────────┴───────────────────────┐
                     ▼                                               ▼
         [1] Stored / Embedded                           [2] Remote Search Pipeline
      • TagLib ID3 / TTML                                 1. Query LRCLIB (Cleaned)
      • Room SQLite (LyricsDao)                           2. If null, Resolve videoId
      • JSON Disk Cache                                   3. Fetch YouTube Timed Text
                     │                                               │
                     └───────────────────────┬───────────────────────┘
                                             │
                                             ▼
                               ┌───────────────────────────┐
                               │      Save to Room DB      │
                               │    (Offline Persistence)  │
                               └─────────────┬─────────────┘
                                             │
                                             ▼
                               ┌───────────────────────────┐
                               │     LyricsSheet UI        │
                               │  (Word-by-Word Karaoke)   │
                               └───────────────────────────┘
```

---

## 3. Detailed Component Specifications

### 3.1 `LyricsRepositoryImpl` Multi-Source Fallback
- **Location:** `app/src/main/java/com/quietrays/tonarc/data/repository/LyricsRepositoryImpl.kt`
- **Fallback Chain:**
  1. Checks memory cache and `lyricsDao.getLyricsForSong(songId)`.
  2. Queries LRCLIB using heuristics (`cleanTitle`, `cleanArtist`, `primaryArtist`, `normalizeText`).
  3. If LRCLIB returns null or unsynced, resolves `videoId`:
     - From `song.youtubeId` or `song.contentUriString`.
     - Or searches Innertube for `"${song.title} ${song.artist}"`.
  4. Calls `innertubeApiService.getTranscriptLyrics(videoId)`.
  5. Parses timed-text entries into synchronized `[mm:ss.xx]` LRC format.
  6. Stores result in Room database (`LyricsEntity`) with `isSynced = true`.

### 3.2 Automated Preloading in `LyricsStateHolder`
- **Location:** `app/src/main/java/com/quietrays/tonarc/presentation/viewmodel/LyricsStateHolder.kt`
- On `playbackStateHolder.stablePlayerState.currentSong` change:
  - Immediately queries `lyricsRepository.getStoredLyrics(song)` or triggers `lyricsRepository.fetchFromRemote(song)` in background.
  - Updates `_lyricsState` so opening the sheet requires zero wait time.

### 3.3 Enhanced Manual Search
- `searchRemoteByQuery(title, artist)` queries both LRCLIB and YouTube Music transcripts, tagging each candidate with its origin source badge.

---

## 4. Verification & Testing

- Unit test suite: `./gradlew :app:testDebugUnitTest --no-daemon`
- Debug APK build: `./gradlew assembleDebug --no-daemon`
