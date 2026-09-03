# Spotify Authentication, Private Playlist Support & Importer Performance Optimization

## Overview
This specification details the end-to-end design for:
1. **In-App Spotify Authentication & Private Playlist Access**: Allowing users to log into their Spotify account via an in-app WebView, securely capturing the session cookie (`sp_dc`), generating authenticated Web Player tokens, and importing private/unlisted playlists and Liked Songs.
2. **Private Playlist Error Guidance**: Detecting 404/403 private playlist errors and presenting actionable guidance (one-tap in-app login or 3-step "Make Public" guide).
3. **Playlist Importer Performance & Stutter-Free Polish**: Eliminating frame drops, layout jitter, and recomposition churn in `ImportSpotifyPlaylistDialog`.

---

## 1. Problem Statement

### 1.1 Private Playlists Unreachable
When a user pastes or shares a private Spotify playlist link, Spotify's Web API returns HTTP 404 (Not Found) or 403 (Forbidden) to unauthenticated requests. Currently, `SpotifyPlaylistFetcher` uses an anonymous web player token which cannot access private playlists.

### 1.2 Importer Dialog Stutter & UI Churn
`ImportSpotifyPlaylistDialog` stutters during import due to:
- **Uncached Shape Allocation**: `AbsoluteSmoothCornerShape(...)` is instantiated inside the Composable body on every frame, re-executing squircle curve math and allocating new objects during rapid recompositions.
- **Root-level `animateContentSize` Churn**: `animateContentSize()` on the root dialog `Column` continuously interrupts and restarts spring layout animations whenever track titles flip between 1 line and 2 lines multiple times per second.
- **High-Frequency Main Thread State Updates**: `SpotifyMatchingEngine` invokes `onProgress` concurrently on every single track completion across multiple parallel coroutines, flooding the Compose runtime with state updates without throttling.
- **Un-smoothed Progress Bar**: `LinearProgressIndicator` updates abruptly without tween interpolation.

---

## 2. Technical Architecture & Design

### 2.1 Spotify Authentication Flow (`sp_dc` Session Cookie)

```
┌────────────────────────────────────────────────────────────────────────┐
│                   AccountsScreen / Importer Dialog                     │
│  "Connect Spotify" / "Private Playlist Detected -> Log in to Spotify"  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                        (Starts SpotifyLoginActivity)
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│               SpotifyLoginActivity (Secure In-App WebView)             │
│   • URL: https://accounts.spotify.com/en/login                         │
│   • Captures `sp_dc` cookie via Android CookieManager                  │
│   • Alternative: Manual cookie paste dialog (sp_dc)                    │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     UserPreferencesRepository                          │
│   • `spotify_auth_cookies`: Stores `sp_dc=...`                         │
│   • `spotify_user_name`: Stores user display name                      │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                       SpotifyPlaylistFetcher                           │
│   • Injects UserPreferencesRepository                                  │
│   • Passes `Cookie: sp_dc=...` to open.spotify.com/get_access_token    │
│   • Receives authenticated token (`isAnonymous = false`)               │
│   • Calls `https://api.spotify.com/v1/playlists/{id}` (private access) │
│   • Calls `https://api.spotify.com/v1/me` (profile display name)       │
└────────────────────────────────────────────────────────────────────────┘
```

#### Why `sp_dc` Web Player Token?
- **Zero Developer Secret Hassle**: Traditional Spotify Web API OAuth requires every user or app to register a client ID and maintain developer dashboard redirect URIs.
- **Full Parity with YouTube Music**: Tonarc already uses this exact pattern with YouTube Music (`YouTubeLoginActivity` capturing `SAPISID` / Google cookies), making the architecture familiar, robust, and completely self-contained.
- **Full User Scope**: An authenticated web player token has complete read access to the user's private playlists, collaborative playlists, and saved tracks (`me/tracks`).

---

### 2.2 Performance Optimizations for Stutter-Free Importer UI

#### 1. Remember Shapes and Heavy Calculations
Memoize `dialogShape` and colors using `remember`:
```kotlin
val dialogShape = remember {
    AbsoluteSmoothCornerShape(
        cornerRadiusTL = 24.dp,
        smoothnessAsPercentTL = 60,
        cornerRadiusTR = 24.dp,
        smoothnessAsPercentTR = 60,
        cornerRadiusBL = 36.dp,
        smoothnessAsPercentBL = 60,
        cornerRadiusBR = 36.dp,
        smoothnessAsPercentBR = 60
    )
}
```

#### 2. Eliminate Layout Jitter (Remove Root `animateContentSize`)
- Remove `.animateContentSize()` from the root `Column`.
- Keep the matching card container with a stable minimum height (`Modifier.fillMaxWidth().heightIn(min = 100.dp)`).
- Use `AnimatedContent` with `Crossfade` or `fadeIn() + fadeOut()` strictly for transitions between primary dialog states (`Idle`, `Preview`, `Matching`, `Success`, `Error`).

#### 3. Smooth Progress Interpolation
Use `animateFloatAsState` for the progress indicator:
```kotlin
val targetProgress = if (state.total > 0) state.current.toFloat() / state.total.toFloat() else 0f
val animatedProgress by animateFloatAsState(
    targetValue = targetProgress,
    animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
    label = "spotify_import_progress"
)
```

#### 4. Conflated & Throttled Progress Dispatching
In `SpotifyMatchingEngine.kt`, throttle progress updates to the UI so updates are emitted at most every 60ms, preventing main-thread recomposition storms while maintaining snappy visual feedback.

---

### 2.3 Private Playlist Guidance & Login Interstitial

When `SpotifyPlaylistFetcher.fetchPlaylist` encounters HTTP 404 or 403 on a private playlist:
1. Define a specific exception:
   ```kotlin
   class SpotifyPrivatePlaylistException(
       val playlistId: String,
       val isUserLoggedIn: Boolean
   ) : IOException(
       if (isUserLoggedIn) {
           "This playlist is private or unlisted and not accessible by the logged-in Spotify account."
       } else {
           "This Spotify playlist is private or unlisted. Log in with your Spotify account or make the playlist public in Spotify."
       }
   )
   ```
2. In `ImportSpotifyPlaylistDialog`:
   - If unauthenticated:
     - Render a distinct Private Playlist banner in Spotify Green & Amber.
     - Action 1: **"Log in with Spotify"** button (launches `SpotifyLoginActivity`).
     - Action 2: **"How to Make Public"** expandable tip:
       > 1. Open playlist in Spotify app.
       > 2. Tap **•••** (options).
       > 3. Tap **"Make Public"** and re-paste the link.

---

### 2.4 Accounts & Settings Integration

1. Update `ExternalServiceAccount`:
   ```kotlin
   enum class ExternalServiceAccount {
       NAVIDROME,
       JELLYFIN,
       YOUTUBE_MUSIC,
       LISTENBRAINZ,
       SPOTIFY
   }
   ```
2. In `AccountsScreen.kt`:
   - Add Spotify account card with Spotify branding (`SpotifyGreen`, Spotify icon).
   - Display connection status: `"Connected as <display_name>"` or `"Link your Spotify account to import private playlists & liked songs"`.
   - "Connect" button opens `SpotifyLoginActivity`.
   - "Disconnect" button clears credentials from `UserPreferencesRepository`.

---

## 3. Data Flow & Verification Plan

### Test Scenarios:
1. **Anonymous Token Fetching**:
   - Verify `getAnonymousToken()` continues to work cleanly when no user is logged in.
2. **Authenticated Token Fetching**:
   - Verify that when `spotify_auth_cookies` contains `sp_dc=...`, `getAccessToken()` attaches the cookie and returns the authenticated token.
3. **Private Playlist Handling**:
   - Mock Spotify Web API returning HTTP 404/403.
   - Assert `SpotifyPrivatePlaylistException` is thrown with correct `isUserLoggedIn` state.
4. **Performance Verification**:
   - Verify `ImportSpotifyPlaylistDialog` renders without frame drops during a 100-track matching operation.
5. **Unit Test Suite**:
   - Execute `./gradlew :app:testDebugUnitTest --no-daemon` to ensure 100% test pass rate.
