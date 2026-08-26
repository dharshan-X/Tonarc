# Design Spec: Rebrand PixelPlayerOSS to Tonarc (`com.quietrays.tonarc`)

## 1. Overview
This specification details the comprehensive rebranding of PixelPlayerOSS into **Tonarc** and the migration of the application package identifier and root namespace from `com.lostf1sh.pixelplayeross` to `com.quietrays.tonarc`.

## 2. Goals & Success Criteria
- **Application ID & Namespace**: `com.quietrays.tonarc` across Gradle build scripts, AndroidManifest, and source files.
- **Source Code Tree**: Directory paths and package statements moved to `com/quietrays/tonarc/` across `main`, `test`, and `androidTest`.
- **User-Facing Branding**: All occurrences of "PixelPlayer" / "PixelPlayerOSS" in English and localized string resources (`res/values*`), notification channels, crash logging, and dialogs updated to "Tonarc".
- **External Services**: Client identifiers and User-Agents updated to "Tonarc" in ListenBrainz, MusicBrainz, Navidrome, and Jellyfin integrations.
- **Build & Test Verification**: Clean compilation (`assembleDebug`, `assembleRelease`) and 100% passing unit tests in local test suite and CI.

## 3. Detailed Changes

### 3.1 Build & Android Configuration
- `app/build.gradle.kts`:
  - `namespace = "com.quietrays.tonarc"`
  - `applicationId = "com.quietrays.tonarc"`
- `app/compose_stability.conf`:
  - Update all class paths from `com.lostf1sh.pixelplayeross.*` to `com.quietrays.tonarc.*`.
- `app/proguard-rules.pro`:
  - Update keep rules from `com.lostf1sh.pixelplayeross.*` to `com.quietrays.tonarc.*`.
- `app/src/main/AndroidManifest.xml`:
  - Update application class name to `.TonarcApplication`.
  - Update themes to `@style/Theme.Tonarc`.
  - Update custom intent actions:
    - `com.quietrays.tonarc.action.OPEN_PLAYER`
    - `com.quietrays.tonarc.ACTION_WIDGET_UPDATE_PLAYBACK_STATE`
  - Update FileProvider authority to `${applicationId}.fileprovider`.

### 3.2 Directory Structure & Package Refactoring
- Rename/move directory trees:
  - `app/src/main/java/com/lostf1sh/pixelplayeross/` $\to$ `app/src/main/java/com/quietrays/tonarc/`
  - `app/src/test/java/com/lostf1sh/pixelplayeross/` $\to$ `app/src/test/java/com/quietrays/tonarc/`
  - `app/src/androidTest/java/com/lostf1sh/pixelplayeross/` $\to$ `app/src/androidTest/java/com/quietrays/tonarc/`
- Update all Kotlin and Java files:
  - `package com.lostf1sh.pixelplayeross...` $\to$ `package com.quietrays.tonarc...`
  - `import com.lostf1sh.pixelplayeross...` $\to$ `import com.quietrays.tonarc...`
- Component Renaming:
  - `PixelPlayerApplication.kt` $\to$ `TonarcApplication.kt`
  - `PixelPlayerTheme` $\to$ `TonarcTheme` (in `Theme.kt`, `MainActivity.kt`, `ExternalPlayerActivity.kt`)
  - `PixelPlayerDatabase.kt` $\to$ `TonarcDatabase.kt` (with Room DB name migration / fallback support)
  - `PixelPlayerMediaButtonReceiver.kt` $\to$ `TonarcMediaButtonReceiver.kt`
  - `PixelPlayerGlanceWidget.kt` $\to$ `TonarcGlanceWidget.kt`
  - `PixelPlayerGlanceWidgetReceiver.kt` $\to$ `TonarcGlanceWidgetReceiver.kt`

### 3.3 User-Facing Branding & Resources
- Resource files (`app/src/main/res/values*`):
  - `app_name` $\to$ `"Tonarc"`
  - `about_app_name` $\to$ `"Tonarc"`
  - Update all description strings across `strings.xml`, `strings_screens.xml`, `strings_settings.xml`, `strings_components.xml`, `strings_presentation_batch_g.xml`.
  - Update all localized string tables (`values-ar`, `values-de`, `values-es`, `values-fr`, `values-in`, `values-it`, `values-ko`, `values-nb`, `values-ru`, `values-tr`, `values-zh-rCN`).
  - Themes: `Theme.PixelPlayer` $\to$ `Theme.Tonarc` in `themes.xml` and `values-night/themes.xml`.
  - Colors: `pixelplayer_widget_background_color` $\to$ `tonarc_widget_background_color`.
  - Export directory label: `"Music/Tonarc Exports"`.
  - Backup filename format: `"Tonarc_Backup_%1$d.tonarc"`.

### 3.4 Diagnostics, Logging & External Integrations
- `CrashHandler.kt`:
  - Update crash banner to `"=== Tonarc Crash Report ==="`.
- Notification Channels:
  - Playback channel name: `"Tonarc Music Playback"`.
- External APIs:
  - ListenBrainz: `SUBMISSION_CLIENT = "Tonarc"`
  - MusicBrainz: `User-Agent: "Tonarc/${BuildConfig.VERSION_NAME}..."`
  - Navidrome: `clientId = "Tonarc"`, `User-Agent: "Tonarc/${API_VERSION}"`
  - Jellyfin: `CLIENT_NAME = "Tonarc"`, `DEVICE_ID = "Tonarc-Android"`

## 4. Verification Plan
1. **Automated Unit Tests**:
   - Run `./gradlew testDebugUnitTest` verifying all unit tests pass with the new package namespace and imports.
2. **Build Compilation**:
   - Run `./gradlew assembleDebug assembleRelease` verifying APK compilation succeeds without errors.
3. **CI Pipeline**:
   - Push branch and verify GitHub Actions `Build & Test (CI)` workflow completes with green status.
