# YouTube Library & Liked Music Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate authenticated YouTube Music user library sync (Liked Music `FEmusic_liked_videos`, personal & saved playlists `FEmusic_library_playlists`), Room DB caching with offline resilience, unified Library UI rendering, and bidirectional ❤️ Like/Unlike synchronization.

**Architecture:** Extend `InnertubeApiService` with authenticated browse endpoints and like mutations; implement `YouTubeLibrarySyncEngine` to orchestrate pagination, deduplication, and Room SQLite upsert; integrate unified state streams into `LibraryViewModel` and `PlayerViewModel`.

**Tech Stack:** Kotlin 2.4, AndroidX Room SQLite, Kotlin Coroutines & StateFlow, Jetpack Compose Material 3 Expressive, OkHttp3, JUnit 5.

## Global Constraints
- Append `--no-daemon` to all Gradle invocations.
- Use Compose `StateFlow` and dedicated state holders.
- Database operations must be transactional, conflict-safe, and asynchronous.
- Format all file references as markdown links with `file://` scheme.

---

### Task 1: Innertube Liked Songs & Library Playlists Parser & API Endpoints

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/data/network/youtube/InnertubeParser.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/data/network/youtube/InnertubeApiService.kt`
- Test: `app/src/test/java/com/quietrays/tonarc/data/network/youtube/InnertubeParserTest.kt`

**Interfaces:**
- Consumes: `InnertubeApiService.buildRequest`, `InnertubeTrack`, `InnertubePlaylist`
- Produces: 
  - `InnertubeParser.parseLikedSongs(jsonString: String): Pair<List<InnertubeTrack>, String?>`
  - `InnertubeParser.parseLibraryPlaylists(jsonString: String): Pair<List<InnertubePlaylist>, String?>`
  - `InnertubeApiService.getLikedSongs(continuation: String?): Pair<List<InnertubeTrack>, String?>`
  - `InnertubeApiService.getUserPlaylists(continuation: String?): Pair<List<InnertubePlaylist>, String?>`

- [ ] **Step 1: Write the failing tests in `InnertubeParserTest.kt`**

```kotlin
@Test
fun `parseLikedSongs extracts tracks and continuation token`() {
    val sampleJson = """
    {
      "contents": {
        "singleColumnBrowseResultsRenderer": {
          "tabs": [{
            "tabRenderer": {
              "content": {
                "sectionListRenderer": {
                  "contents": [{
                    "musicPlaylistShelfRenderer": {
                      "contents": [{
                        "musicResponsiveListItemRenderer": {
                          "playlistItemData": { "videoId": "vid_liked_1" },
                          "flexColumns": [
                            { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Liked Song 1" }] } } },
                            { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Artist 1" }] } } }
                          ],
                          "fixedColumns": [
                            { "musicResponsiveListItemFixedColumnRenderer": { "text": { "runs": [{ "text": "3:45" }] } } }
                          ]
                        }
                      }],
                      "continuations": [{ "nextContinuationData": { "continuation": "cont_token_123" } }]
                    }
                  }]
                }
              }
            }
          }]
        }
      }
    }
    """.trimIndent()

    val (tracks, continuation) = InnertubeParser.parseLikedSongs(sampleJson)
    assertEquals(1, tracks.size)
    assertEquals("vid_liked_1", tracks[0].videoId)
    assertEquals("Liked Song 1", tracks[0].title)
    assertEquals("Artist 1", tracks[0].artists)
    assertEquals(225000L, tracks[0].durationMs)
    assertEquals("cont_token_123", continuation)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*.InnertubeParserTest" --no-daemon`
Expected: Compilation failure or method missing.

- [ ] **Step 3: Implement `parseLikedSongs` & `parseLibraryPlaylists` in `InnertubeParser.kt` and endpoints in `InnertubeApiService.kt`**

```kotlin
// In InnertubeParser.kt
fun parseLikedSongs(jsonString: String): Pair<List<InnertubeTrack>, String?> {
    val tracks = mutableListOf<InnertubeTrack>()
    var continuationToken: String? = null
    try {
        val root = JSONObject(jsonString)
        val tabContent = root.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")

        val shelf = tabContent?.optJSONObject(0)?.optJSONObject("musicPlaylistShelfRenderer")
            ?: root.optJSONObject("continuationContents")?.optJSONObject("musicPlaylistShelfContinuation")

        val items = shelf?.optJSONArray("contents")
        if (items != null) {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                val track = parseTrackItem(item)
                if (track != null) tracks.add(track)
            }
        }
        continuationToken = shelf?.optJSONArray("continuations")?.optJSONObject(0)
            ?.optJSONObject("nextContinuationData")?.optString("continuation")?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {}
    return tracks to continuationToken
}
```

- [ ] **Step 4: Run unit test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*.InnertubeParserTest" --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/data/network/youtube/InnertubeParser.kt \
        app/src/main/java/com/quietrays/tonarc/data/network/youtube/InnertubeApiService.kt \
        app/src/test/java/com/quietrays/tonarc/data/network/youtube/InnertubeParserTest.kt
git commit -m "feat(youtube): add Liked Songs and Library Playlists parsing and API endpoints"
```

---

### Task 2: Two-Way Like / Unlike Remote Mutation API

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/data/network/youtube/InnertubeApiService.kt`
- Test: `app/src/test/java/com/quietrays/tonarc/data/network/youtube/InnertubeApiServiceTest.kt`

**Interfaces:**
- Produces: `InnertubeApiService.setLikeStatus(videoId: String, isLiked: Boolean): Boolean`

- [ ] **Step 1: Write test for `setLikeStatus`**

```kotlin
@Test
fun `setLikeStatus sends like mutation payload and handles response`() = runTest {
    // verify request URL is BASE_URL/like/like or like/removelike with target videoId
}
```

- [ ] **Step 2: Implement `setLikeStatus` in `InnertubeApiService.kt`**

```kotlin
suspend fun setLikeStatus(videoId: String, isLiked: Boolean): Boolean = withContext(Dispatchers.IO) {
    if (authCookies.isNullOrBlank()) return@withContext false
    try {
        val endpoint = if (isLiked) "like/like" else "like/removelike"
        val body = JSONObject().apply {
            put("context", createBaseContext())
            put("target", JSONObject().apply {
                put("videoId", videoId)
            })
        }
        val request = buildRequest(endpoint, body)
        val response = okHttpClient.newCall(request).execute()
        response.isSuccessful
    } catch (e: Exception) {
        Timber.w(e, "Failed to set like status for video: $videoId")
        false
    }
}
```

- [ ] **Step 3: Verify with unit tests**

Run: `./gradlew :app:testDebugUnitTest --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/data/network/youtube/InnertubeApiService.kt
git commit -m "feat(youtube): implement remote setLikeStatus mutation endpoint"
```

---

### Task 3: `YouTubeLibrarySyncEngine` & Room Caching Layer

**Files:**
- Create: `app/src/main/java/com/quietrays/tonarc/data/network/youtube/YouTubeLibrarySyncEngine.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/data/database/MusicDao.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/data/database/PlaylistDao.kt`
- Test: `app/src/test/java/com/quietrays/tonarc/data/network/youtube/YouTubeLibrarySyncEngineTest.kt`

**Interfaces:**
- Consumes: `InnertubeApiService`, `MusicDao`, `PlaylistDao`, `UserPreferencesRepository`
- Produces: `YouTubeLibrarySyncEngine.syncLibrary(): Result<Unit>`, `val syncState: StateFlow<SyncState>`

- [ ] **Step 1: Write the failing tests for `YouTubeLibrarySyncEngine`**

```kotlin
@Test
fun `syncLibrary fetches liked music and upserts into database`() = runTest {
    // mock InnertubeApiService.getLikedSongs and verify Room DAO insertion
}
```

- [ ] **Step 2: Implement `YouTubeLibrarySyncEngine.kt`**

```kotlin
@Singleton
class YouTubeLibrarySyncEngine @Inject constructor(
    private val innertubeApiService: InnertubeApiService,
    private val musicDao: MusicDao,
    private val playlistDao: PlaylistDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    @AppScope private val scope: CoroutineScope
) {
    sealed interface SyncState {
        object Idle : SyncState
        data class Syncing(val message: String) : SyncState
        data class Success(val likedCount: Int, val playlistCount: Int) : SyncState
        data class Error(val message: String) : SyncState
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    suspend fun syncLibrary(): Result<Unit> = withContext(Dispatchers.IO) {
        _syncState.value = SyncState.Syncing("Fetching Liked Music...")
        // 1. Fetch Liked Songs (FEmusic_liked_videos)
        // 2. Fetch User Playlists (FEmusic_library_playlists)
        // 3. Upsert into Room DB with source = "YOUTUBE"
        // 4. Update syncState to Success
    }
}
```

- [ ] **Step 3: Run tests to verify passing**

Run: `./gradlew :app:testDebugUnitTest --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/data/network/youtube/YouTubeLibrarySyncEngine.kt \
        app/src/main/java/com/quietrays/tonarc/data/database/MusicDao.kt \
        app/src/main/java/com/quietrays/tonarc/data/database/PlaylistDao.kt
git commit -m "feat(youtube): add YouTubeLibrarySyncEngine for offline-first Room persistence"
```

---

### Task 4: Library Screen UI & Pull-to-Refresh Sync

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/viewmodel/LibraryViewModel.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/screens/LibraryScreen.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/components/PlaylistCard.kt`

**Interfaces:**
- Consumes: `YouTubeLibrarySyncEngine.syncState`, `YouTubeLibrarySyncEngine.syncLibrary()`
- Produces: Pinned Liked Music Hero Card, YouTube Badge on cloud playlists, Pull-to-refresh trigger.

- [ ] **Step 1: Connect `YouTubeLibrarySyncEngine` into `LibraryViewModel`**
- [ ] **Step 2: Add Pinned "❤️ Liked Music" card at top of playlists in `LibraryScreen.kt`**
- [ ] **Step 3: Add YouTube badge pill on cloud playlists in `PlaylistCard.kt`**
- [ ] **Step 4: Test UI rendering and compile check**

Run: `./gradlew assembleDebug --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit UI changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/viewmodel/LibraryViewModel.kt \
        app/src/main/java/com/quietrays/tonarc/presentation/screens/LibraryScreen.kt \
        app/src/main/java/com/quietrays/tonarc/presentation/components/PlaylistCard.kt
git commit -m "feat(ui): display synced YouTube playlists, Liked Music hero card, and sync indicator in Library"
```

---

### Task 5: Player ❤️ Like Synchronization & End-to-End Verification

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/viewmodel/PlayerViewModel.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/screens/NowPlayingScreen.kt`
- Test: `app/src/test/java/com/quietrays/tonarc/presentation/viewmodel/PlayerViewModelTest.kt`

**Interfaces:**
- Consumes: `InnertubeApiService.setLikeStatus`, `MusicDao.setSongFavorite`

- [ ] **Step 1: Implement optimistic two-way ❤️ like handling in `PlayerViewModel.kt`**

```kotlin
fun toggleLike(song: Song) {
    val newLiked = !song.isFavorite
    // 1. Optimistic local update
    viewModelScope.launch {
        musicDao.setFavorite(song.id, newLiked)
        // 2. If song is from YouTube and user is authenticated, sync to remote
        if (song.source == "YOUTUBE" && !innertubeApiService.authCookies.isNullOrBlank()) {
            val success = innertubeApiService.setLikeStatus(song.remoteId ?: song.id, newLiked)
            if (!success) {
                // Rollback on remote failure
                musicDao.setFavorite(song.id, !newLiked)
            }
        }
    }
}
```

- [ ] **Step 2: Run full unit test suite**

Run: `./gradlew :app:testDebugUnitTest --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Assemble Universal Debug APK**

Run: `./gradlew assembleDebug -Ptonarc.enableAbiSplits=false --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit and push changes**

```bash
git add -A
git commit -m "feat(player): integrate bidirectional YouTube Like sync with optimistic UI updates"
git push origin main
```
