# Design Spec: Fuzzy Search, Infinite Search Pagination & Infinite Autoplay Queue

**Date**: 2026-08-20  
**Status**: Approved  

---

## 1. Overview & Objectives

This specification defines the implementation for three interconnected music discovery and playback features in PixelPlayerOSS:
1. **Fuzzy Search**: Typo-tolerant approximate matching and ranking across local audio files, artists, and albums.
2. **Infinite Search Pagination**: Dynamic, continuous loading of YouTube Music search results via InnerTube continuation tokens as the user scrolls.
3. **Infinite Autoplay Queue (Radio)**: Algorithmic radio track generation via YouTube Music's `/next` endpoint, automatically appending similar songs when approaching the end of the queue so playback never stops.

---

## 2. Architecture & Detailed Design

### 2.1 Fuzzy Search (Local Library)

#### Components:
* **`FuzzySearchMatcher`**: Utility class in `com.lostf1sh.pixelplayeross.utils` providing:
  * Normalized Levenshtein / Damerau-Levenshtein distance calculation.
  * Tokenized substring scoring with prefix match bonuses.
  * Character-level fuzzy matching for short strings (threshold <= 2 edit distance for queries >= 3 characters).
* **`MusicRepositoryImpl.kt` & `MusicDao.kt`**:
  * For search queries with length >= 3, if exact FTS matching returns few results, evaluate local library songs, albums, and artists through `FuzzySearchMatcher`.
  * Rank results by computed score:
    * Exact token match: 1.0
    * Word prefix match: 0.85
    * Fuzzy edit match: 0.60 - 0.80
    * Fallback substring: 0.50
* **`SearchStateHolder.kt`**:
  * Merges ranked local fuzzy results with YouTube Music online search results.

---

### 2.2 Infinite Search Pagination

#### Components:
* **`InnertubeParser.kt`**:
  * Extract `continuationCommand.token` or `nextContinuationData.continuation` from the YouTube Music search response JSON.
  * Support `parseSearchContinuation(jsonString: String)` to parse additional tracks and the subsequent continuation token.
* **`InnertubeApiService.kt`**:
  * Add `search(query: String, continuation: String? = null): InnertubeSearchResult`.
  * When `continuation` is provided, send `{"continuation": continuation, "context": createBaseContext()}` to `https://music.youtube.com/youtubei/v1/search`.
* **`YouTubeRepository.kt`**:
  * Expose `searchSongsPaginated(query: String, continuation: String?)`.
* **`SearchStateHolder.kt`**:
  * Maintain `currentContinuationToken: String?` and `isLoadingMore: StateFlow<Boolean>`.
  * Expose `loadMoreSearchResults()`.
* **`SearchScreen.kt`**:
  * Attach scroll listener / `LazyListState` item prefetch threshold (k <= 4 items from end) to invoke `loadMoreSearchResults()`.
  * Display a discreet bottom loading indicator while fetching subsequent pages.

---

### 2.3 Infinite Autoplay Queue (Radio)

#### Components:
* **`InnertubeApiService.kt`**:
  * Add `getRadioTracks(videoId: String): List<InnertubeTrack>`.
  * Calls `https://music.youtube.com/youtubei/v1/next` with:
    ```json
    {
      "context": createBaseContext(),
      "videoId": videoId,
      "isAudioOnly": true,
      "enablePersistentPlaylistPanel": true
    }
    ```
  * Parses the returned playlist / radio panel (`playlistPanelRenderer` or `playlistPanelVideoRenderer`) into a list of 25–50 related tracks.
* **`YouTubeRepository.kt`**:
  * Expose `getRadioTracks(videoId: String): Flow<List<Song>>`.
  * For local songs without `youtubeId`, perform a fast single-track search query (`"$title $artist"`) to find the seed `videoId` before calling `getRadioTracks`.
* **`UserPreferencesRepository.kt`**:
  * Add `infiniteAutoplayEnabled: Flow<Boolean>` (default: `true`) and `setInfiniteAutoplayEnabled(enabled: Boolean)`.
* **`DualPlayerEngine.kt` / `MusicService.kt`**:
  * Monitor queue progress via `Player.Listener.onMediaItemTransition`.
  * When `currentMediaItemIndex >= mediaItemCount - 2` and autoplay is enabled, trigger background radio expansion.
  * Convert new tracks to `MediaItem`s and call `addMediaItems` without interrupting active playback.

---

## 3. Data Models & Interface Updates

### `InnertubeSearchResult`
```kotlin
data class InnertubeSearchResult(
    val query: String,
    val songs: List<InnertubeTrack> = emptyList(),
    val albums: List<InnertubeAlbum> = emptyList(),
    val artists: List<InnertubeArtist> = emptyList(),
    val playlists: List<InnertubePlaylist> = emptyList(),
    val continuationToken: String? = null
)
```

---

## 4. Verification Plan

1. **Unit Tests**:
   * `FuzzySearchMatcherTest`: Verify typo handling, score thresholds, prefix ranking, and special characters.
   * `InnertubeSearchContinuationTest`: Verify continuation token extraction and subsequent page parsing.
   * `InnertubeRadioTest`: Verify `/next` endpoint radio queue extraction.
2. **End-to-End Device Verification**:
   * Test fuzzy search with intentional typos on connected device (`CPH2667`).
   * Scroll down search results to verify smooth infinite pagination.
   * Play the last track in the queue to verify automatic radio extension and uninterrupted playback.
