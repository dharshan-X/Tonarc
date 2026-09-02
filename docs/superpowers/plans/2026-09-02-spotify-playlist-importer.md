# Spotify Playlist Importer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a zero-login Spotify playlist importer in Tonarc that extracts metadata/tracks from Spotify playlist URLs or Android share intents, matches songs against local files and YouTube Music streams, and persists them as streamable playlists.

**Architecture:** A standalone `SpotifyPlaylistFetcher` network client uses Spotify's public anonymous token API and embed fallback to fetch playlist tracks. `SpotifyMatchingEngine` resolves each Spotify track to local audio files and YouTube Music audio streams with live progress updates. `ImportSpotifyPlaylistDialog` provides a Material 3 Expressive UI triggered from the Library screen, playlist creation dialogs, and Android Share Sheet.

**Tech Stack:** Kotlin, OkHttp, JSONObject, Jetpack Compose, Material 3 Expressive, Room SQLite, Dagger Hilt, Coroutines & StateFlow.

## Global Constraints

- Always append `--no-daemon` to all Gradle invocations.
- Follow Material 3 Expressive styling consistent with Tonarc's design tokens.
- Maintain error safety with `runCatching` so network or malformed JSON payloads never crash the app.
- Never hardcode API keys or require user logins for Spotify playlist importing.

---

### Task 1: Spotify Models & Network Fetcher (`SpotifyPlaylistFetcher.kt`)

**Files:**
- Create: `app/src/main/java/com/quietrays/tonarc/data/network/spotify/SpotifyModels.kt`
- Create: `app/src/main/java/com/quietrays/tonarc/data/network/spotify/SpotifyPlaylistFetcher.kt`
- Create: `app/src/test/java/com/quietrays/tonarc/data/network/spotify/SpotifyPlaylistFetcherTest.kt`

**Interfaces:**
- Produces:
  - `data class SpotifyTrack(val id: String, val title: String, val artist: String, val artists: List<String>, val album: String?, val durationMs: Long, val coverUri: String?)`
  - `data class SpotifyPlaylist(val id: String, val title: String, val description: String?, val author: String?, val coverUri: String?, val trackCount: Int, val tracks: List<SpotifyTrack>)`
  - `class SpotifyPlaylistFetcher`: `fun extractPlaylistId(urlOrId: String): String?`, `suspend fun fetchPlaylist(playlistId: String): Result<SpotifyPlaylist>`

- [ ] **Step 1: Write failing unit test in `SpotifyPlaylistFetcherTest.kt`**

```kotlin
package com.quietrays.tonarc.data.network.spotify

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpotifyPlaylistFetcherTest {
    @Test
    fun extractPlaylistId_extractsFromVariousUrlFormats() {
        val fetcher = SpotifyPlaylistFetcher(okhttp3.OkHttpClient())
        assertThat(fetcher.extractPlaylistId("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=123")).isEqualTo("37i9dQZF1DXcBWIGoYBM5M")
        assertThat(fetcher.extractPlaylistId("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M")).isEqualTo("37i9dQZF1DXcBWIGoYBM5M")
        assertThat(fetcher.extractPlaylistId("37i9dQZF1DXcBWIGoYBM5M")).isEqualTo("37i9dQZF1DXcBWIGoYBM5M")
        assertThat(fetcher.extractPlaylistId("https://invalid.com")).isNull()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*.SpotifyPlaylistFetcherTest" --no-daemon`
Expected: FAIL (unresolved reference)

- [ ] **Step 3: Implement `SpotifyModels.kt` and `SpotifyPlaylistFetcher.kt`**

Implement URL parsing, anonymous token retrieval (`https://open.spotify.com/get_access_token`), playlist API calls (`https://api.spotify.com/v1/playlists/{id}` with pagination), and embed HTML fallback parsing (`https://open.spotify.com/embed/playlist/{id}`).

- [ ] **Step 4: Run unit tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*.SpotifyPlaylistFetcherTest" --no-daemon`
Expected: PASS

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/data/network/spotify/ app/src/test/java/com/quietrays/tonarc/data/network/spotify/
git commit -m "feat(spotify): implement SpotifyPlaylistFetcher with anonymous token resolution and embed fallback"
```

---

### Task 2: Spotify Track Matching Engine (`SpotifyMatchingEngine.kt`)

**Files:**
- Create: `app/src/main/java/com/quietrays/tonarc/data/spotify/SpotifyMatchingEngine.kt`
- Create: `app/src/test/java/com/quietrays/tonarc/data/spotify/SpotifyMatchingEngineTest.kt`

**Interfaces:**
- Consumes: `SpotifyPlaylist`, `SpotifyTrack`, `InnertubeApiService`, `MusicRepository`, `YouTubeDao`
- Produces:
  - `data class MatchProgress(val current: Int, val total: Int, val currentTrackTitle: String)`
  - `data class MatchResult(val song: Song, val isLocalMatch: Boolean)`
  - `class SpotifyMatchingEngine`: `suspend fun matchPlaylistTracks(tracks: List<SpotifyTrack>, matchLocal: Boolean, matchCloud: Boolean, onProgress: (MatchProgress) -> Unit): List<MatchResult>`

- [ ] **Step 1: Write failing unit test in `SpotifyMatchingEngineTest.kt`**

Test matching Spotify tracks against local repository songs and YouTube Music search candidates with fuzzy matching tolerance.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*.SpotifyMatchingEngineTest" --no-daemon`
Expected: FAIL

- [ ] **Step 3: Implement `SpotifyMatchingEngine.kt`**

Implement local library index matching with string normalization and cloud search resolution with concurrency rate limiting (e.g. 3-4 concurrent requests) and progress emissions.

- [ ] **Step 4: Run unit tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*.SpotifyMatchingEngineTest" --no-daemon`
Expected: PASS

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/data/spotify/ app/src/test/java/com/quietrays/tonarc/data/spotify/
git commit -m "feat(spotify): implement SpotifyMatchingEngine for local and cloud stream track resolution"
```

---

### Task 3: Playlist ViewModel State & Actions (`PlaylistViewModel.kt`)

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/viewmodel/PlaylistViewModel.kt`
- Modify: `app/src/test/java/com/quietrays/tonarc/presentation/viewmodel/PlaylistViewModelTest.kt` (or related test)

**Interfaces:**
- Consumes: `SpotifyPlaylistFetcher`, `SpotifyMatchingEngine`
- Produces:
  - `val spotifyImportState: StateFlow<SpotifyImportState>`
  - `fun previewSpotifyPlaylist(urlOrId: String)`
  - `fun saveSpotifyPlaylist(playlist: SpotifyPlaylist, customTitle: String?, saveAsCloud: Boolean, saveAsLocal: Boolean)`
  - `fun resetSpotifyImportState()`

- [ ] **Step 1: Add unit test in `PlaylistViewModelTest.kt`**

Verify `previewSpotifyPlaylist` sets loading and preview states, and `saveSpotifyPlaylist` calls the matching engine and stores the resulting playlist.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*PlaylistViewModelTest*" --no-daemon`
Expected: FAIL

- [ ] **Step 3: Update `PlaylistViewModel.kt`**

Inject `SpotifyPlaylistFetcher` and `SpotifyMatchingEngine`. Add the state holder and methods to preview, match, and save Spotify playlists into `youTubeDao` and `userPlaylistsFlow`.

- [ ] **Step 4: Run unit tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --no-daemon`
Expected: PASS

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/viewmodel/PlaylistViewModel.kt app/src/test/java/com/quietrays/tonarc/presentation/viewmodel/
git commit -m "feat(playlist): wire Spotify playlist preview, matching state, and persistence in PlaylistViewModel"
```

---

### Task 4: Import Dialog UI, Entry Points & Share Sheet Handling

**Files:**
- Create: `app/src/main/java/com/quietrays/tonarc/presentation/components/ImportSpotifyPlaylistDialog.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/screens/LibraryScreen.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/components/PlaylistBottomSheet.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/MainActivity.kt`

**Interfaces:**
- Produces: `ImportSpotifyPlaylistDialog(visible: Boolean, playlistViewModel: PlaylistViewModel, onDismiss: () -> Unit)`
- Adds Spotify import triggers in Library screen, playlist creation dialogs, and Android `ACTION_SEND` intent receiver.

- [ ] **Step 1: Create `ImportSpotifyPlaylistDialog.kt`**

Build the Material 3 Expressive dialog with Spotify Green styling, paste button, preview card, customizable name, cloud/local checkboxes, and real-time matching progress bar.

- [ ] **Step 2: Add Entry Points in LibraryScreen and PlaylistBottomSheet**

Add "Import from Spotify" options in playlist management menus.

- [ ] **Step 3: Handle Spotify Share Sheet in `MainActivity.kt`**

In `MainActivity.kt`, inspect incoming `ACTION_SEND` intent for `spotify.com/playlist/` URLs and trigger `ImportSpotifyPlaylistDialog` with the shared link pre-filled.

- [ ] **Step 4: Verify UI and Build**

Run: `./gradlew assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/components/ImportSpotifyPlaylistDialog.kt app/src/main/java/com/quietrays/tonarc/presentation/ app/src/main/java/com/quietrays/tonarc/MainActivity.kt
git commit -m "feat(ui): add ImportSpotifyPlaylistDialog, library entry points, and Android share sheet handler"
```

---

### Task 5: End-to-End Verification & Build Check

**Files:**
- Run all test suites and build the debug APK.

- [ ] **Step 1: Execute Complete Unit Test Suite**

Run: `./gradlew :app:testDebugUnitTest --no-daemon`
Expected: 100% tests passing

- [ ] **Step 2: Assemble Debug APK**

Run: `./gradlew assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Final Commit & Push**

```bash
git push tonarc main
```
