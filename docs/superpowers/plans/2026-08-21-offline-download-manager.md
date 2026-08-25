# Offline Download Manager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide a full-fledged offline download manager with one-tap song/album/playlist downloading, background WorkManager execution, download badges, and a dedicated Downloads management screen.

**Architecture:** Extend `CloudOfflineRepository` and `CloudTrackDownloadWorker` to support YouTube Music, Navidrome, and Jellyfin downloads, add bulk enqueue APIs, integrate UI download actions with progress feedback across song lists, albums, and playlists, and build a dedicated Downloads Screen in the Library.

**Tech Stack:** AndroidX WorkManager, Room SQLite (`offline_tracks`), Jetpack Compose, Material 3 Expressive, Dagger Hilt, Flow/Coroutines.

---

## Tasks

### Task 1: Enable YouTube Music in `CloudOfflineRepository` and Add Bulk Enqueue APIs
**Files:**
- Modify: `app/src/main/java/com/lostf1sh/pixelplayeross/data/offline/CloudOfflineRepository.kt`
- Test: `app/src/test/java/com/lostf1sh/pixelplayeross/data/offline/CloudOfflineRepositoryTest.kt`

- [ ] Add `"youtube"` support in `CloudOfflineRepository.providerFor(sourceUri)` and `isCloudSong(song)`.
- [ ] Add `suspend fun enqueueAll(songs: List<Song>)` for batch enqueuing of albums and playlists.
- [ ] Add `suspend fun cancelAll()` and `suspend fun deleteAllDownloaded()`.
- [ ] Write unit tests verifying YouTube song download enqueueing and bulk operations.
- [ ] Run `./gradlew --no-daemon testDebugUnitTest` and commit.

---

### Task 2: Expose Download Actions and State in `PlayerViewModel` / `LibraryViewModel`
**Files:**
- Modify: `app/src/main/java/com/lostf1sh/pixelplayeross/presentation/viewmodel/PlayerViewModel.kt`
- Modify: `app/src/main/java/com/lostf1sh/pixelplayeross/presentation/viewmodel/LibraryViewModel.kt`
- Test: `app/src/test/java/com/lostf1sh/pixelplayeross/presentation/viewmodel/PlayerViewModelTest.kt`

- [ ] Inject `CloudOfflineRepository` into `PlayerViewModel` / `LibraryViewModel`.
- [ ] Expose `fun downloadSong(song: Song)`, `fun downloadSongs(songs: List<Song>)`, `fun deleteDownloadedSong(song: Song)`.
- [ ] Expose `val offlineDownloadsFlow: StateFlow<Map<String, OfflineDownload>>` observing real-time progress and completion.
- [ ] Write unit tests and verify.

---

### Task 3: Add Download UI Controls to Song Items, Album Detail, and Playlist Detail Screens
**Files:**
- Modify: `app/src/main/java/com/lostf1sh/pixelplayeross/presentation/components/subcomps/EnhancedSongListItem.kt`
- Modify: `app/src/main/java/com/lostf1sh/pixelplayeross/presentation/screens/AlbumDetailScreen.kt`
- Modify: `app/src/main/java/com/lostf1sh/pixelplayeross/presentation/screens/PlaylistDetailScreen.kt`

- [ ] Add Download icon/action in `EnhancedSongListItem` more-options menu and trailing status icon (Downloading circular spinner / Downloaded checkmark).
- [ ] Add "Download Album" action button in `AlbumDetailScreen` top bar / header.
- [ ] Add "Download Playlist" action button in `PlaylistDetailScreen` header.
- [ ] Test Compose rendering and verify.

---

### Task 4: Build Dedicated Downloads Management Screen in Library
**Files:**
- Create: `app/src/main/java/com/lostf1sh/pixelplayeross/presentation/screens/DownloadsScreen.kt`
- Modify: `app/src/main/java/com/lostf1sh/pixelplayeross/presentation/screens/LibraryScreen.kt`
- Modify: `app/src/main/java/com/lostf1sh/pixelplayeross/presentation/navigation/Screen.kt`

- [ ] Create `DownloadsScreen.kt` with storage footprint card (e.g. `245 MB used for 64 offline songs`), active downloads section with progress bars, and completed downloads list with swipe-to-delete.
- [ ] Add "Downloads" tab or quick chip in `LibraryScreen.kt`.
- [ ] Add navigation route `Screen.Downloads`.
- [ ] Verify UI on device.

---

### Task 5: End-to-End Build & Device Verification
- [ ] Run `./gradlew --no-daemon testDebugUnitTest` to ensure 100% test pass rate.
- [ ] Run `./gradlew --no-daemon assembleDebug` to build the APK.
- [ ] Install updated APK to connected device and verify real downloads, progress notifications, and offline playback.
