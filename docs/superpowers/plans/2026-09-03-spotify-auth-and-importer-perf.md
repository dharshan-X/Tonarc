# Spotify Authentication, Private Playlist Support & Importer Performance Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement in-app Spotify WebView authentication (`sp_dc` cookie) to unlock private/unlisted playlists and Liked Songs import, provide clear private playlist guidance, integrate Spotify into the Accounts screen, and eliminate UI stutter/frame drops in `ImportSpotifyPlaylistDialog`.

**Architecture:** 
1. Use Android `CookieManager` inside an in-app WebView (`SpotifyLoginActivity`) to capture session cookie `sp_dc` upon Spotify login, persisting to Jetpack DataStore (`UserPreferencesRepository`).
2. Supply `sp_dc` cookie in `SpotifyPlaylistFetcher` to generate authenticated Web Player bearer tokens via `open.spotify.com/get_access_token`, unlocking private playlists.
3. Optimize Compose rendering in `ImportSpotifyPlaylistDialog` by memoizing shapes, removing root `animateContentSize` churn, smoothing progress bar with `animateFloatAsState`, and throttling progress updates in `SpotifyMatchingEngine`.
4. Surface Spotify under Linked Accounts in `AccountsScreen` and provide a 1-tap "Log in with Spotify" interstitial when private playlists are detected.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3 Expressive, AndroidX Media3, OkHttp, Android CookieManager, Jetpack DataStore Preferences, Hilt, JUnit 5, MockK.

## Global Constraints
- Append `--no-daemon` to all Gradle invocations.
- Per user instruction: Only run unit tests (`./gradlew :app:testDebugUnitTest --no-daemon`) for verification; do NOT run local `assembleDebug` APK builds during intermediate task verification.
- Always format file references as markdown links with the `file://` scheme.

---

### Task 1: DataStore Preferences & Spotify Models / Exceptions

**Files:**
- Modify: [`app/src/main/java/com/quietrays/tonarc/data/preferences/UserPreferencesRepository.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/data/preferences/UserPreferencesRepository.kt)
- Modify: [`app/src/main/java/com/quietrays/tonarc/data/network/spotify/SpotifyModels.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/data/network/spotify/SpotifyModels.kt)
- Test: [`app/src/test/java/com/quietrays/tonarc/data/preferences/UserPreferencesRepositoryTest.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/test/java/com/quietrays/tonarc/data/preferences/UserPreferencesRepositoryTest.kt)

**Interfaces:**
- Produces:
  - `UserPreferencesRepository.spotifyAuthCookiesFlow: Flow<String?>`
  - `UserPreferencesRepository.spotifyUserNameFlow: Flow<String?>`
  - `suspend fun UserPreferencesRepository.saveSpotifyCookies(cookies: String, userName: String? = null)`
  - `suspend fun UserPreferencesRepository.clearSpotifyAuth()`
  - `class SpotifyPrivatePlaylistException(val playlistId: String, val isUserLoggedIn: Boolean) : IOException(...)`

- [ ] **Step 1: Add `SpotifyPrivatePlaylistException` in `SpotifyModels.kt`**

```kotlin
class SpotifyPrivatePlaylistException(
    val playlistId: String,
    val isUserLoggedIn: Boolean
) : java.io.IOException(
    if (isUserLoggedIn) {
        "This playlist is private or unlisted and not accessible by the logged-in Spotify account."
    } else {
        "This Spotify playlist is private or unlisted. Log in with your Spotify account or make the playlist public in Spotify."
    }
)
```

- [ ] **Step 2: Add Spotify preference keys and methods in `UserPreferencesRepository.kt`**

```kotlin
val SPOTIFY_AUTH_COOKIES = stringPreferencesKey("spotify_auth_cookies")
val SPOTIFY_USER_NAME = stringPreferencesKey("spotify_user_name")

val spotifyAuthCookiesFlow: Flow<String?> = dataStore.data.map { preferences ->
    preferences[PreferencesKeys.SPOTIFY_AUTH_COOKIES]?.takeIf { it.isNotBlank() }
}

val spotifyUserNameFlow: Flow<String?> = dataStore.data.map { preferences ->
    preferences[PreferencesKeys.SPOTIFY_USER_NAME]?.takeIf { it.isNotBlank() }
}

suspend fun saveSpotifyCookies(cookies: String, userName: String? = null) {
    dataStore.edit { preferences ->
        preferences[PreferencesKeys.SPOTIFY_AUTH_COOKIES] = cookies
        if (userName != null) {
            preferences[PreferencesKeys.SPOTIFY_USER_NAME] = userName
        }
    }
}

suspend fun clearSpotifyAuth() {
    dataStore.edit { preferences ->
        preferences.remove(PreferencesKeys.SPOTIFY_AUTH_COOKIES)
        preferences.remove(PreferencesKeys.SPOTIFY_USER_NAME)
    }
}
```

- [ ] **Step 3: Run unit tests to verify DataStore operations**

Run: `./gradlew :app:testDebugUnitTest --tests "*.UserPreferencesRepositoryTest" --no-daemon`
Expected: PASS

- [ ] **Step 4: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/data/preferences/UserPreferencesRepository.kt app/src/main/java/com/quietrays/tonarc/data/network/spotify/SpotifyModels.kt
git commit -m "feat(spotify): add Spotify auth preferences and private playlist exception"
```

---

### Task 2: Authenticated Web Player Token, Profile & Private Playlist Fetcher

**Files:**
- Modify: [`app/src/main/java/com/quietrays/tonarc/data/network/spotify/SpotifyPlaylistFetcher.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/data/network/spotify/SpotifyPlaylistFetcher.kt)
- Test: [`app/src/test/java/com/quietrays/tonarc/data/network/spotify/SpotifyPlaylistFetcherTest.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/test/java/com/quietrays/tonarc/data/network/spotify/SpotifyPlaylistFetcherTest.kt)

**Interfaces:**
- Consumes: `UserPreferencesRepository.spotifyAuthCookiesFlow`
- Produces:
  - `suspend fun SpotifyPlaylistFetcher.getAccessToken(forceRefresh: Boolean = false): String?`
  - `suspend fun SpotifyPlaylistFetcher.fetchCurrentUserProfile(token: String): Pair<String, String>?` // id to displayName
  - Updated `fetchPlaylist(playlistId)` throwing `SpotifyPrivatePlaylistException` on 404/403

- [ ] **Step 1: Write unit test verifying authenticated token and private playlist exception**

```kotlin
@Test
fun `fetchPlaylist throws SpotifyPrivatePlaylistException on 404 when unauthenticated`() = runTest {
    // Mock 404 response from Spotify Web API
    // Assert SpotifyPrivatePlaylistException is thrown with isUserLoggedIn = false
}
```

- [ ] **Step 2: Inject `UserPreferencesRepository` and attach cookies in `SpotifyPlaylistFetcher.kt`**

```kotlin
@Singleton
class SpotifyPlaylistFetcher @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend fun getAccessToken(forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        // Check memory cache unless forceRefresh
        val savedCookies = userPreferencesRepository.spotifyAuthCookiesFlow.first()
        val requestBuilder = Request.Builder()
            .url(TOKEN_URL)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
        
        if (!savedCookies.isNullOrBlank()) {
            requestBuilder.header("Cookie", savedCookies)
        }
        
        // Execute request, cache token, and return
    }
```

- [ ] **Step 3: Update `fetchPlaylist` to catch HTTP 404/403 and throw `SpotifyPrivatePlaylistException`**

```kotlin
if (response.code == 404 || response.code == 403) {
    val isLoggedIn = !userPreferencesRepository.spotifyAuthCookiesFlow.first().isNullOrBlank()
    throw SpotifyPrivatePlaylistException(playlistId, isUserLoggedIn = isLoggedIn)
}
```

- [ ] **Step 4: Add `fetchCurrentUserProfile(token)` method**

```kotlin
suspend fun fetchCurrentUserProfile(token: String): Pair<String, String>? = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("https://api.spotify.com/v1/me")
        .header("Authorization", "Bearer $token")
        .header("User-Agent", USER_AGENT)
        .get()
        .build()
    okHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return@withContext null
        val json = JSONObject(response.body.string())
        val id = json.optString("id")
        val displayName = json.optString("display_name").ifBlank { id }
        Pair(id, displayName)
    }
}
```

- [ ] **Step 5: Run unit tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*.SpotifyPlaylistFetcherTest" --no-daemon`
Expected: PASS

- [ ] **Step 6: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/data/network/spotify/SpotifyPlaylistFetcher.kt app/src/test/java/com/quietrays/tonarc/data/network/spotify/SpotifyPlaylistFetcherTest.kt
git commit -m "feat(spotify): support authenticated web player token, profile fetch, and private playlist detection"
```

---

### Task 3: SpotifyLoginActivity & In-App Cookie Capture

**Files:**
- Create: `app/src/main/java/com/quietrays/tonarc/presentation/spotify/auth/SpotifyLoginViewModel.kt`
- Create: `app/src/main/java/com/quietrays/tonarc/presentation/spotify/auth/SpotifyLoginActivity.kt`
- Modify: [`app/src/main/AndroidManifest.xml`](file:///home/dharshan/PixelPlayerOSS/app/src/main/AndroidManifest.xml)
- Test: `app/src/test/java/com/quietrays/tonarc/presentation/spotify/auth/SpotifyLoginViewModelTest.kt`

**Interfaces:**
- Consumes: `SpotifyPlaylistFetcher`, `UserPreferencesRepository`
- Produces: `SpotifyLoginActivity` activity for WebView login and manual cookie paste

- [ ] **Step 1: Create `SpotifyLoginViewModel.kt`**

```kotlin
@HiltViewModel
class SpotifyLoginViewModel @Inject constructor(
    private val spotifyPlaylistFetcher: SpotifyPlaylistFetcher,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {
    sealed interface LoginUiState {
        data object Idle : LoginUiState
        data object Loading : LoginUiState
        data class Success(val userName: String) : LoginUiState
        data class Error(val message: String) : LoginUiState
    }
    
    val uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    
    fun processCookies(rawCookies: String) {
        // Extracts sp_dc, validates via fetchCurrentUserProfile, saves to preferences
    }
}
```

- [ ] **Step 2: Create `SpotifyLoginActivity.kt` with Material 3 Expressive UI and WebView**

- In-app WebView loading `https://accounts.spotify.com/en/login`.
- In `WebViewClient.onPageFinished`, query `CookieManager.getInstance().getCookie(".spotify.com")`.
- When `sp_dc` is detected, trigger `viewModel.processCookies(cookies)`.
- Action bar with Back button, Refresh, and a "Paste Cookie" manual dialog button.
- On success, toast confirmation and finish with `RESULT_OK`.

- [ ] **Step 3: Register `SpotifyLoginActivity` in `AndroidManifest.xml`**

```xml
<activity
    android:name=".presentation.spotify.auth.SpotifyLoginActivity"
    android:exported="false"
    android:theme="@style/Theme.Tonarc" />
```

- [ ] **Step 4: Run unit tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*.SpotifyLoginViewModelTest" --no-daemon`
Expected: PASS

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/spotify/auth/ app/src/main/AndroidManifest.xml app/src/test/java/com/quietrays/tonarc/presentation/spotify/auth/
git commit -m "feat(spotify): add SpotifyLoginActivity and ViewModel for in-app WebView authentication"
```

---

### Task 4: AccountsScreen & AccountsViewModel Integration

**Files:**
- Modify: [`app/src/main/java/com/quietrays/tonarc/presentation/viewmodel/AccountsViewModel.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/presentation/viewmodel/AccountsViewModel.kt)
- Modify: [`app/src/main/java/com/quietrays/tonarc/presentation/screens/AccountsScreen.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/presentation/screens/AccountsScreen.kt)
- Test: [`app/src/test/java/com/quietrays/tonarc/presentation/viewmodel/AccountsViewModelTest.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/test/java/com/quietrays/tonarc/presentation/viewmodel/AccountsViewModelTest.kt)

**Interfaces:**
- Adds: `ExternalServiceAccount.SPOTIFY`
- Displays Spotify linked account card with Spotify Green accent, username, connect and disconnect actions.

- [ ] **Step 1: Add `SPOTIFY` to `ExternalServiceAccount` enum**

```kotlin
enum class ExternalServiceAccount {
    NAVIDROME,
    JELLYFIN,
    YOUTUBE_MUSIC,
    LISTENBRAINZ,
    SPOTIFY
}
```

- [ ] **Step 2: Wire Spotify flows in `AccountsViewModel.kt`**

- Collect `userPreferencesRepository.spotifyAuthCookiesFlow` and `spotifyUserNameFlow`.
- When cookies exist, include `ExternalAccountUiModel` for `SPOTIFY` in `connectedAccounts`.
- Add `logout(service)` handling for `SPOTIFY` calling `userPreferencesRepository.clearSpotifyAuth()`.

- [ ] **Step 3: Update `AccountsScreen.kt` UI**

- Add Spotify card under Linked Accounts with Spotify logo/icon.
- When clicked / connecting, launch `SpotifyLoginActivity`.
- Add "Paste Cookie" option matching YouTube Music dialog.

- [ ] **Step 4: Run unit tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*.AccountsViewModelTest" --no-daemon`
Expected: PASS

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/viewmodel/AccountsViewModel.kt app/src/main/java/com/quietrays/tonarc/presentation/screens/AccountsScreen.kt app/src/test/java/com/quietrays/tonarc/presentation/viewmodel/AccountsViewModelTest.kt
git commit -m "feat(accounts): integrate Spotify into Accounts screen with connect and logout support"
```

---

### Task 5: Importer Performance Optimization & Private Playlist Guidance

**Files:**
- Modify: [`app/src/main/java/com/quietrays/tonarc/data/spotify/SpotifyMatchingEngine.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/data/spotify/SpotifyMatchingEngine.kt)
- Modify: [`app/src/main/java/com/quietrays/tonarc/presentation/viewmodel/PlaylistViewModel.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/presentation/viewmodel/PlaylistViewModel.kt)
- Modify: [`app/src/main/java/com/quietrays/tonarc/presentation/components/ImportSpotifyPlaylistDialog.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/presentation/components/ImportSpotifyPlaylistDialog.kt)
- Test: [`app/src/test/java/com/quietrays/tonarc/presentation/components/ImportSpotifyPlaylistDialogTest.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/test/java/com/quietrays/tonarc/presentation/components/ImportSpotifyPlaylistDialogTest.kt)

**Interfaces:**
- Eliminates recomposition stutter, layout jumping, and uncached squircle math.
- Surfaces private playlist guidance and in-app login CTA.

- [ ] **Step 1: Throttle progress reporting in `SpotifyMatchingEngine.kt`**

- Use atomic tracking with a 60ms throttle gate for `onProgress` emissions, while guaranteeing the final (100%) track is always emitted immediately.

- [ ] **Step 2: Optimize Compose allocations in `ImportSpotifyPlaylistDialog.kt`**

- Wrap `dialogShape` in `remember { AbsoluteSmoothCornerShape(...) }`.
- Remove `.animateContentSize()` from the root dialog `Column`.
- Set a stable minimum height (`heightIn(min = 96.dp)`) on the matching card container to eliminate height wobbling.
- Smooth the progress bar:
  ```kotlin
  val animatedProgress by animateFloatAsState(
      targetValue = if (state.total > 0) state.current.toFloat() / state.total.toFloat() else 0f,
      animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
      label = "import_progress"
  )
  ```

- [ ] **Step 3: Add Private Playlist Guidance Card**

- When `importState is SpotifyImportState.Error` and the exception is `SpotifyPrivatePlaylistException`:
  - Show 🔒 **"Private Playlist"** card with amber/green badge.
  - Primary button: **"Log in with Spotify"** (starts `SpotifyLoginActivity`).
  - Expandable tip: "How to Make Public in Spotify" (3 steps with copy).

- [ ] **Step 4: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest --no-daemon`
Expected: BUILD SUCCESSFUL (100% tests passing)

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/data/spotify/SpotifyMatchingEngine.kt app/src/main/java/com/quietrays/tonarc/presentation/viewmodel/PlaylistViewModel.kt app/src/main/java/com/quietrays/tonarc/presentation/components/ImportSpotifyPlaylistDialog.kt
git commit -m "perf(importer): eliminate dialog stutter with memoized shapes, throttled progress, and private playlist CTA"
```
