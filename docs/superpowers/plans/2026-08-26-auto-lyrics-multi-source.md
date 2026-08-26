# Auto-Lyrics & Multi-Source Karaoke Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement automated, multi-source synced lyrics fetching (LRCLIB + YouTube Timed Text), persistent SQLite caching in Room, automatic background preloading on song transitions, and source-attributed manual search.

**Architecture:** Extend `LyricsRepositoryImpl` with a fallback chain (Embedded -> LRCLIB -> YouTube Timed Text -> SQLite Cache); update `LyricsStateHolder` to proactively pre-fetch lyrics when songs transition; enhance `FetchLyricsDialog` with multi-source candidate indicators.

**Tech Stack:** Kotlin 2.4, Retrofit/OkHttp, Room SQLite, Jetpack Compose Material 3 Expressive, JUnit 5.

## Global Constraints
- Append `--no-daemon` to all Gradle invocations.
- Always use `file://` scheme for markdown links.

---

### Task 1: Multi-Source Remote Pipeline & YouTube Timed Text Fallback

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/data/network/youtube/InnertubeApiService.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/data/repository/LyricsRepositoryImpl.kt`
- Test: `app/src/test/java/com/quietrays/tonarc/data/repository/LyricsRepositoryTest.kt`

**Interfaces:**
- Consumes: `InnertubeApiService.getTranscriptLyrics(videoId)`, `LrcLibApiService`
- Produces: `LyricsRepository.fetchFromRemote(song)` (with LRCLIB -> YouTube Timed Text fallback)

- [ ] **Step 1: Write failing tests in `LyricsRepositoryTest.kt` for YouTube transcript fallback**
- [ ] **Step 2: Fix `InnertubeApiService.getTranscriptLyrics` payload formatting**
- [ ] **Step 3: Implement YouTube transcript fallback and videoId auto-resolution in `LyricsRepositoryImpl.kt`**
- [ ] **Step 4: Run unit tests to verify passing**
- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/data/network/youtube/InnertubeApiService.kt \
        app/src/main/java/com/quietrays/tonarc/data/repository/LyricsRepositoryImpl.kt \
        app/src/test/java/com/quietrays/tonarc/data/repository/LyricsRepositoryTest.kt
git commit -m "feat(lyrics): implement multi-source LRCLIB and YouTube Timed Text fallback pipeline"
```

---

### Task 2: Automatic Background Preloading in `LyricsStateHolder`

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/viewmodel/LyricsStateHolder.kt`
- Test: `app/src/test/java/com/quietrays/tonarc/presentation/viewmodel/LyricsStateHolderTest.kt`

**Interfaces:**
- Consumes: `PlaybackStateHolder.stablePlayerState`, `LyricsRepository.getLyrics`
- Produces: Instant lyrics state on song start

- [ ] **Step 1: Write failing test in `LyricsStateHolderTest.kt` verifying auto-preload on song change**
- [ ] **Step 2: Implement reactive song change collector with debounce in `LyricsStateHolder.kt`**
- [ ] **Step 3: Run unit tests to verify passing**
- [ ] **Step 4: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/viewmodel/LyricsStateHolder.kt \
        app/src/test/java/com/quietrays/tonarc/presentation/viewmodel/LyricsStateHolderTest.kt
git commit -m "feat(lyrics): add reactive background preloading on song playback start"
```

---

### Task 3: Multi-Source Manual Search Dialog & UI Badges

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/data/repository/LyricsRepositoryImpl.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/components/subcomps/FetchLyricsDialog.kt`

**Interfaces:**
- Consumes: `LyricsRepository.searchRemoteByQuery`
- Produces: Multi-source candidates tagged with `LRCLIB` / `YouTube Transcript`

- [ ] **Step 1: Enhance `searchRemoteByQuery` to aggregate LRCLIB and YouTube transcript results**
- [ ] **Step 2: Render source pill badges in `FetchLyricsDialog.kt`**
- [ ] **Step 3: Build APK to verify Compose layout compilation**

Run: `./gradlew assembleDebug --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/data/repository/LyricsRepositoryImpl.kt \
        app/src/main/java/com/quietrays/tonarc/presentation/components/subcomps/FetchLyricsDialog.kt
git commit -m "feat(ui): display source badges in manual lyrics search dialog"
```

---

### Task 4: End-to-End Verification & Release

- [ ] **Step 1: Run full unit test suite**
Run: `./gradlew :app:testDebugUnitTest --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Assemble Debug APK**
Run: `./gradlew assembleDebug --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Merge and Push to GitHub**
```bash
git push origin main
```
