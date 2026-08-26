# Rebrand to Tonarc (`com.quietrays.tonarc`) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebrand PixelPlayerOSS to **Tonarc** and migrate the Android application identifier, namespace, and source package tree from `com.lostf1sh.pixelplayeross` to `com.quietrays.tonarc`.

**Architecture:** Systematic package rename across Gradle build config, source directories, Kotlin imports, Manifest, Proguard, and stability configuration, accompanied by a full sweep of user-facing strings (English + 11 localized locales), themes, crash logger banners, notification channels, and external client IDs.

**Tech Stack:** Kotlin, Jetpack Compose, Android SDK, Gradle Kotlin DSL, Room, Dagger Hilt, JUnit 5, MockK.

**Spec:** [`docs/superpowers/specs/2026-08-24-rebrand-to-tonarc-design.md`](file:///data/data/com.termux/files/home/PixelPlayerOSS/docs/superpowers/specs/2026-08-24-rebrand-to-tonarc-design.md)

## Global Constraints
- Target package identifier: `com.quietrays.tonarc`
- Target app display name: `Tonarc`
- All unit test suites in `app/src/test` must compile and pass cleanly
- Android release signing and ABI splits configuration must remain intact

---

### Task 1: Build Configuration, Proguard Rules & Android Manifest

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/compose_stability.conf`
- Modify: `app/proguard-rules.pro`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: Existing project structure
- Produces: Gradle namespace and applicationId set to `com.quietrays.tonarc`

- [ ] **Step 1: Update `app/build.gradle.kts`**
  - Set `namespace = "com.quietrays.tonarc"`
  - Set `applicationId = "com.quietrays.tonarc"`

- [ ] **Step 2: Update `app/compose_stability.conf` and `app/proguard-rules.pro`**
  - Replace all `com.lostf1sh.pixelplayeross` package references with `com.quietrays.tonarc`.

- [ ] **Step 3: Update `app/src/main/AndroidManifest.xml`**
  - Update application class `.TonarcApplication` (formerly `PixelPlayerApplication`).
  - Update widget receivers and media button receivers to `com.quietrays.tonarc`.
  - Update action names (`com.quietrays.tonarc.action.OPEN_PLAYER`, `com.quietrays.tonarc.ACTION_WIDGET_UPDATE_PLAYBACK_STATE`).
  - Update FileProvider authority `${applicationId}.fileprovider`.
  - Update theme styles to `@style/Theme.Tonarc`.

---

### Task 2: Rebrand User-Facing String Resources, Themes & Assets

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values/strings_screens.xml`
- Modify: `app/src/main/res/values/strings_settings.xml`
- Modify: `app/src/main/res/values/strings_components.xml`
- Modify: `app/src/main/res/values/strings_presentation_batch_g.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/values-night/themes.xml`
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values-*/strings*.xml` (all 11 localized directories: ar, de, es, fr, in, it, ko, nb, ru, tr, zh-rCN)

- [ ] **Step 1: Update base English strings**
  - Update `app_name` $\to$ `"Tonarc"`, `about_app_name` $\to$ `"Tonarc"`.
  - Update descriptions, dialog messages, setup wizard copy, and export folder display (`"Music/Tonarc Exports"`).

- [ ] **Step 2: Update localized string resource directories**
  - Replace all occurrences of `PixelPlayerOSS` and `PixelPlayer` with `Tonarc` across `values-ar`, `values-de`, `values-es`, `values-fr`, `values-in`, `values-it`, `values-ko`, `values-nb`, `values-ru`, `values-tr`, `values-zh-rCN`.

- [ ] **Step 3: Update theme definitions & colors**
  - Rename `Theme.PixelPlayer` $\to$ `Theme.Tonarc` and `Theme.PixelPlayer.ExternalPlayer` $\to$ `Theme.Tonarc.ExternalPlayer` in `themes.xml` and `values-night/themes.xml`.
  - Rename `pixelplayer_widget_background_color` $\to$ `tonarc_widget_background_color` in `colors.xml` and referencing drawables/layouts.

---

### Task 3: Source Code Package & Directory Migration

**Files:**
- Move & Rename: `app/src/main/java/com/lostf1sh/pixelplayeross/` $\to$ `app/src/main/java/com/quietrays/tonarc/`
- Move & Rename: `app/src/test/java/com/lostf1sh/pixelplayeross/` $\to$ `app/src/test/java/com/quietrays/tonarc/`
- Move & Rename: `app/src/androidTest/java/com/lostf1sh/pixelplayeross/` $\to$ `app/src/androidTest/java/com/quietrays/tonarc/`
- Rename Components:
  - `PixelPlayerApplication.kt` $\to$ `TonarcApplication.kt`
  - `PixelPlayerTheme` $\to$ `TonarcTheme`
  - `PixelPlayerDatabase.kt` $\to$ `TonarcDatabase.kt`
  - `PixelPlayerMediaButtonReceiver.kt` $\to$ `TonarcMediaButtonReceiver.kt`
  - `PixelPlayerGlanceWidget.kt` $\to$ `TonarcGlanceWidget.kt`
  - `PixelPlayerGlanceWidgetReceiver.kt` $\to$ `TonarcGlanceWidgetReceiver.kt`

- [ ] **Step 1: Move directory trees to `com/quietrays/tonarc/`**
  - Move main source tree, unit tests tree, and androidTest tree.

- [ ] **Step 2: Rename application & theme symbols**
  - Rename `PixelPlayerApplication` class to `TonarcApplication`.
  - Rename `PixelPlayerTheme` to `TonarcTheme`.
  - Rename `PixelPlayerDatabase` class to `TonarcDatabase`.
  - Rename `PixelPlayerGlanceWidget` and receiver classes.

- [ ] **Step 3: Batch update package declarations and imports**
  - Replace `package com.lostf1sh.pixelplayeross` with `package com.quietrays.tonarc` across all `.kt` files in `main`, `test`, and `androidTest`.
  - Replace `import com.lostf1sh.pixelplayeross.` with `import com.quietrays.tonarc.` across all `.kt` files.
  - Update references to `com.quietrays.tonarc.R`.

---

### Task 4: External Services, Scrobbling & Crash Logging Rebranding

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/utils/CrashHandler.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/TonarcApplication.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/data/listenbrainz/ListenBrainzRepository.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/data/musicbrainz/MusicBrainzApiService.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/data/network/navidrome/NavidromeApiService.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/data/network/jellyfin/JellyfinApiService.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/data/navidrome/model/NavidromeCredentials.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/data/backup/AppDataBackupManager.kt`

- [ ] **Step 1: Update Crash Logger & Notification Channels**
  - `CrashHandler.kt`: Set banner `appendLine("=== Tonarc Crash Report ===")`.
  - `TonarcApplication.kt`: Set notification channel name `"Tonarc Music Playback"`.

- [ ] **Step 2: Update Client User-Agents and Names**
  - `ListenBrainzRepository.kt`: `SUBMISSION_CLIENT = "Tonarc"`
  - `MusicBrainzApiService.kt`: `User-Agent: "Tonarc/${BuildConfig.VERSION_NAME}..."`
  - `NavidromeApiService.kt`: `DEFAULT_CLIENT_ID = "Tonarc"`, `User-Agent: "Tonarc/${API_VERSION}"`
  - `JellyfinApiService.kt`: `CLIENT_NAME = "Tonarc"`, `DEVICE_ID = "Tonarc-Android"`
  - `AppDataBackupManager.kt`: `"Your Tonarc backup was created successfully."`

---

### Task 5: Verification, CI & Release Build

- [ ] **Step 1: Verify Unit Tests**
  - Run `./gradlew testDebugUnitTest` and ensure all unit tests pass with zero errors.

- [ ] **Step 2: Commit and Push**
  - Commit all changes with `refactor: Rebrand application to Tonarc with package com.quietrays.tonarc`.
  - Push branch to origin `Recomendation`.

- [ ] **Step 3: Monitor CI Build & Deliver Release APK**
  - Monitor GitHub Actions workflow run `Build & Test (CI)`.
  - Download release APK artifact and copy to `/sdcard/Download/PixelPlayerOSS-release.apk`.
  - Open installer prompt on device.
