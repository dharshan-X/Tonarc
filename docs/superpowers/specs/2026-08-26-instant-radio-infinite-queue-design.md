# Design Specification: Instant Radio & Infinite Smart Queue (Phase 2)

**Author:** Tonarc Team  
**Date:** 2026-08-26  
**Status:** Approved  
**Target:** Android App (`com.quietrays.tonarc`)  

---

## 1. Overview

Tonarc's Instant Radio builds an on-demand, endless listening experience from any seed song or artist. It blends YouTube Music's radio algorithms (`RDAMVM` streams) with the user's on-device library favorites and co-occurrence clusters to create a smart, continuous playback queue with zero startup latency and infinite automated queue extension.

---

## 2. Architecture & Data Flow

```
┌────────────────────────────────────────────────────────────┐
│                    UI Entry Points                         │
│  • SongInfoBottomSheet ("📻 Start Radio")                  │
│  • NowPlayingScreen (Radio Action)                         │
│  • ArtistDetailScreen ("Artist Radio")                     │
└─────────────────────────────┬──────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────┐
│                      PlayerViewModel                       │
│  • Starts seed song immediately with zero buffering delay  │
│  • Delegates queue assembly to SmartRadioEngine            │
└─────────────────────────────┬──────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────┐
│                     SmartRadioEngine                       │
│  • Resolves seed videoId (online/offline candidate)        │
│  • Fetches Innertube Radio (RDAMVM$videoId)                │
│  • Retrieves related local library favorites               │
│  • Interleaves 70% discovery + 30% local favorites        │
│  • Maintains continuation token for infinite playback     │
└──────────────┬──────────────────────────────┬──────────────┘
               │                              │
               ▼                              ▼
┌──────────────────────────────┐┌─────────────────────────────┐
│     InnertubeApiService      ││   SmartPlaylistGenerator    │
│  (YouTube Music Radio Next)  ││   (Local Offline Fallback)  │
└──────────────────────────────┘└─────────────────────────────┘
               │                              │
               └──────────────┬───────────────┘
                              ▼
┌────────────────────────────────────────────────────────────┐
│           DualPlayerEngine / MusicService Queue            │
│  • Plays seed immediately                                  │
│  • Appends radio tracks seamlessly                         │
│  • Pre-fetches next batch when <= 3 songs remain in queue  │
└────────────────────────────────────────────────────────────┘
```

---

## 3. Detailed Component Specifications

### 3.1 `SmartRadioEngine`
- **Location:** `app/src/main/java/com/quietrays/tonarc/data/recommendation/SmartRadioEngine.kt`
- **Injected Dependencies:**
  - `innertubeApiService: InnertubeApiService`
  - `youTubeRepository: YouTubeRepository`
  - `smartPlaylistGenerator: SmartPlaylistGenerator`
  - `candidateAggregator: CandidateAggregator`
  - `musicRepository: MusicRepository`
- **Key Methods:**
  - `suspend fun generateRadioForSong(seedSong: Song, initialLimit: Int = 25): RadioResult`
  - `suspend fun generateRadioForArtist(artistName: String, initialLimit: Int = 25): RadioResult`
  - `suspend fun fetchNextBatch(continuationToken: String, limit: Int = 15): RadioResult`
- **Data Classes:**
  ```kotlin
  data class RadioResult(
      val seed: Song,
      val tracks: List<Song>,
      val continuationToken: String?,
      val radioTitle: String
  )
  ```

### 3.2 Infinite Autoplay in `MusicService`
- In `MusicService.kt` `onPlaybackStateChanged` / `onMediaItemTransition`:
  - When remaining unplayed items in queue $\le 3$ and `isRadioModeActive` is true:
  - Dispatches coroutine to call `SmartRadioEngine.fetchNextBatch(activeContinuationToken)`.
  - Appends new items to `player` via `player.addMediaItems()`.

### 3.3 UI Integration Points
1. **`SongInfoBottomSheet.kt`**:
   - Add "Start Radio" tonal button with `Icons.Rounded.Radio` in the action list.
2. **`NowPlayingScreen.kt` / `QueueBottomSheet.kt`**:
   - Display radio indicator: `📻 [Seed Title] Radio`.
   - Add shortcut button to restart radio from the currently playing song.
3. **`ArtistDetailScreen.kt`**:
   - Add hero action "Artist Radio" next to Shuffle/Play.

---

## 4. Error Handling & Edge Cases

| Scenario | Handling Strategy |
| :--- | :--- |
| **Local song with no YouTube ID** | Queries `InnertubeApiService.search` for seed title + artist to extract `videoId`, then starts radio. |
| **Offline / Airplane Mode** | Uses `SmartPlaylistGenerator.getSmartQueueForSong` locally without making network calls. |
| **Radio endpoint returns empty** | Falls back to artist/genre top tracks from local library and search recommendations. |
| **Duplicate Prevention** | Filter out tracks whose `id`, `youtubeId`, or `contentUriString` already exist in the active playback queue. |

---

## 5. Verification & Testing

- Unit test suite: `./gradlew :app:testDebugUnitTest --no-daemon`
- Debug build verification: `./gradlew assembleDebug --no-daemon`
