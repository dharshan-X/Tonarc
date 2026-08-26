# Fuzzy Search, Infinite Search & Infinite Autoplay Queue Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement typo-tolerant fuzzy search for local music, infinite scroll pagination for YouTube Music search, and infinite autoplay radio queue for continuous music playback.

**Architecture:** 
1. `FuzzySearchMatcher` scores queries against local metadata using Levenshtein distance and token subsequence matching.
2. `InnertubeApiService` and `InnertubeParser` support search continuation tokens with `SearchStateHolder` infinite scroll pagination.
3. YouTube Music's `/next` endpoint fetches radio playlists when near the queue end, seamlessly appending tracks in `DualPlayerEngine` / `MusicService`.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX Media3 (ExoPlayer), Room SQLite, OkHttp, Coroutines / StateFlow.

---

### Task 1: Fuzzy Search Matcher & Ranking

- [ ] Create `com/lostf1sh/pixelplayeross/utils/FuzzySearchMatcher.kt` with normalized Levenshtein distance and token ranking.
- [ ] Create unit tests in `app/src/test/java/com/lostf1sh/pixelplayeross/utils/FuzzySearchMatcherTest.kt`.
- [ ] Integrate `FuzzySearchMatcher` into `MusicRepositoryImpl.kt` for queries with length >= 3.
- [ ] Run `./gradlew --no-daemon testDebugUnitTest --tests "com.lostf1sh.pixelplayeross.utils.FuzzySearchMatcherTest"` to verify.

---

### Task 2: YouTube Music Infinite Search Pagination

- [ ] Update `InnertubeSearchResult` in `InnertubeModels.kt` to include `continuationToken: String?`.
- [ ] Update `InnertubeParser.kt` to parse search `continuationCommand.token` and handle continuation response formats.
- [ ] Add `search(query: String, continuation: String?): InnertubeSearchResult` in `InnertubeApiService.kt`.
- [ ] Expose `searchSongsPaginated` in `YouTubeRepository.kt`.
- [ ] Add `loadMoreSearchResults()` and `isLoadingMore` in `SearchStateHolder.kt`.
- [ ] Add scroll prefetch trigger and loading indicator in `SearchScreen.kt`.

---

### Task 3: Infinite Autoplay Queue (YouTube Music Radio)

- [ ] Add `getRadioTracks(videoId: String): List<InnertubeTrack>` in `InnertubeApiService.kt` using the `/next` endpoint.
- [ ] Add `parseRadioTracks` in `InnertubeParser.kt` to extract playlist panel items.
- [ ] Expose `getRadioTracks(videoId: String): Flow<List<Song>>` in `YouTubeRepository.kt`.
- [ ] Add `infiniteAutoplayEnabled` preference in `UserPreferencesRepository.kt`.
- [ ] Hook autoplay check in `DualPlayerEngine.kt` / `MusicService.kt` when `currentMediaItemIndex >= mediaItemCount - 2` to append radio tracks.

---

### Task 4: Build, Verify & Install on Connected Device

- [ ] Run all unit tests: `./gradlew --no-daemon testDebugUnitTest`.
- [ ] Build debug APK: `./gradlew --no-daemon assembleDebug`.
- [ ] Install on device: `adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`.
- [ ] Launch and verify fuzzy search, pagination, and infinite radio playback live on device.
