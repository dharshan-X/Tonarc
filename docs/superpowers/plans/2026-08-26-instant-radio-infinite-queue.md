# Instant Radio & Infinite Smart Queue Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a zero-latency Instant Radio and Infinite Smart Queue engine that blends YouTube Music recommendations with local library favorites, featuring continuous auto-replenishing playback and universal UI entry points.

**Architecture:** Create `SmartRadioEngine` to resolve seed tracks, retrieve Innertube radio streams, and interleave local co-occurrence matches; hook into `PlayerViewModel` for zero-latency start and `MusicService` for infinite queue appending; expose "Start Radio" across all song context menus, player sheets, and artist screens.

**Tech Stack:** Kotlin 2.4, AndroidX Media3 / ExoPlayer, Coroutines & Flow, Jetpack Compose Material 3 Expressive, JUnit 5.

## Global Constraints
- Append `--no-daemon` to all Gradle invocations.
- Always use `file://` scheme for markdown links.
- Maintain zero playback startup delay (start playing seed song immediately before awaiting network).

---

### Task 1: `SmartRadioEngine` & Hybrid Synthesis Layer

**Files:**
- Create: `app/src/main/java/com/quietrays/tonarc/data/recommendation/SmartRadioEngine.kt`
- Test: `app/src/test/java/com/quietrays/tonarc/data/recommendation/SmartRadioEngineTest.kt`

**Interfaces:**
- Consumes: `InnertubeApiService`, `YouTubeRepository`, `SmartPlaylistGenerator`, `CandidateAggregator`
- Produces: 
  - `SmartRadioEngine.generateRadioForSong(seedSong: Song, initialLimit: Int): RadioResult`
  - `SmartRadioEngine.generateRadioForArtist(artistName: String, initialLimit: Int): RadioResult`
  - `SmartRadioEngine.fetchNextBatch(continuationToken: String, limit: Int): RadioResult`

- [ ] **Step 1: Write failing tests in `SmartRadioEngineTest.kt`**
- [ ] **Step 2: Run test to verify it fails**
- [ ] **Step 3: Implement `SmartRadioEngine.kt`**
- [ ] **Step 4: Run unit tests to verify passing**
- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/data/recommendation/SmartRadioEngine.kt \
        app/src/test/java/com/quietrays/tonarc/data/recommendation/SmartRadioEngineTest.kt
git commit -m "feat(radio): implement SmartRadioEngine with hybrid cloud and local interleaving"
```

---

### Task 2: PlayerViewModel & MusicService Infinite Radio Queue

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/viewmodel/PlayerViewModel.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/data/service/MusicService.kt`
- Test: `app/src/test/java/com/quietrays/tonarc/presentation/viewmodel/PlayerViewModelRadioTest.kt`

**Interfaces:**
- Consumes: `SmartRadioEngine`
- Produces: `PlayerViewModel.playInstantRadio(seedSong: Song)`, `PlayerViewModel.playArtistRadio(artistName: String)`

- [ ] **Step 1: Write failing tests in `PlayerViewModelRadioTest.kt`**
- [ ] **Step 2: Implement zero-latency radio start and queue hydration in `PlayerViewModel.kt`**
- [ ] **Step 3: Update `MusicService.kt` infinite autoplay trigger when in radio mode**
- [ ] **Step 4: Run unit tests to verify passing**
- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/viewmodel/PlayerViewModel.kt \
        app/src/main/java/com/quietrays/tonarc/data/service/MusicService.kt \
        app/src/test/java/com/quietrays/tonarc/presentation/viewmodel/PlayerViewModelRadioTest.kt
git commit -m "feat(player): wire zero-latency instant radio and infinite queue replenishment"
```

---

### Task 3: Universal UI Entry Points ("Start Radio" Actions)

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/components/SongInfoBottomSheet.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/screens/ArtistDetailScreen.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/components/QueueBottomSheet.kt`

**Interfaces:**
- Consumes: `PlayerViewModel.playInstantRadio`, `PlayerViewModel.playArtistRadio`

- [ ] **Step 1: Add "Start Radio" tonal button in `SongInfoBottomSheet.kt`**
- [ ] **Step 2: Add "Artist Radio" button in `ArtistDetailScreen.kt` header**
- [ ] **Step 3: Add Radio queue indicator and restart action in `QueueBottomSheet.kt`**
- [ ] **Step 4: Build APK to verify Compose layout and compilation**

Run: `./gradlew assembleDebug --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit UI changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/components/SongInfoBottomSheet.kt \
        app/src/main/java/com/quietrays/tonarc/presentation/screens/ArtistDetailScreen.kt \
        app/src/main/java/com/quietrays/tonarc/presentation/components/QueueBottomSheet.kt
git commit -m "feat(ui): add universal Start Radio buttons across song menus, artist screens, and queue"
```

---

### Task 4: End-to-End Verification & Release

- [ ] **Step 1: Run full unit test suite**
Run: `./gradlew :app:testDebugUnitTest --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Assemble Universal Debug APK**
Run: `./gradlew assembleDebug -Ptonarc.enableAbiSplits=false --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Merge and Push to GitHub**
```bash
git push origin main
```
