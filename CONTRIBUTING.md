# Contributing to Tonarc

Thank you for your interest in contributing to Tonarc (PixelPlayerOSS)! We welcome contributions of all kinds: bug fixes, new features, UI/UX polish, performance enhancements, translations, and documentation.

This guide outlines our development workflow, architecture guidelines, and engineering conventions.

---

## 🛠️ Getting Started

### 1. Prerequisites
- **JDK 21** (Temurin or OpenJDK 21)
- **Android SDK**: `compileSdk 37`, `targetSdk 37`, `minSdk 30` (Android 11+)
- **Git**

### 2. Clone the Repository
```bash
git clone https://github.com/KDharshana/PixelPlayerOSS.git
cd PixelPlayerOSS
```

### 3. Build the Project
```bash
# Build universal debug APK for testing
./gradlew :app:assembleDebug -Ptonarc.enableAbiSplits=false --no-daemon

# Run all unit tests
./gradlew :app:testDebugUnitTest --no-daemon
```

---

## 🧪 Local Verification & Pre-PR Checks

Before opening a pull request, ensure all local verification checks pass:

```bash
# 1. Compile Kotlin
./gradlew :app:compileDebugKotlin --no-daemon

# 2. Run Linting
./gradlew :app:lintDebug --no-daemon

# 3. Run Unit Tests (650+ tests)
./gradlew :app:testDebugUnitTest --no-daemon
```

### Running Targeted Unit Tests
To run a specific test class or method:
```bash
./gradlew :app:testDebugUnitTest --tests "com.quietrays.tonarc.data.analytics.TasteProfileManagerTest" --no-daemon
```

> **Note**: Tonarc uses **JUnit Jupiter** (`org.junit.jupiter.api.Test`), not JUnit 4.

---

## 📐 Architecture & Engineering Conventions

### 1. Presentation & State Management
- **Compose State Slicing**: Avoid observing monolithic state objects directly. Slice `PlayerUiState` with `.map { it.toSlice() }.distinctUntilChanged()` to prevent recomposition storms during playback position ticks.
- **Compose Stability**: Prefer `ImmutableList<T>` over `List<T>` in `@Composable` parameter lists (`kotlinx.collections.immutable`). Add new domain models to `app/compose_stability.conf` if needed.
- **Material 3 Expressive**: Follow Material 3 Expressive design tokens, utilizing `AbsoluteSmoothCornerShape` for rounded containers and fluid transitions.

### 2. Audio & Media Playback
- **Media3 Routing**: Route all playback mutations through `MusicService` / `MediaController` rather than directly manipulating player instances in UI.
- **DualPlayerEngine**: Custom engine for gapless playback, smart crossfades, and volume leveling.

### 3. Database & Storage
- **Room SQLite & Migrations**: Any schema change in `PixelPlayerDatabase` must include an incremental migration in `app/src/main/java/com/quietrays/tonarc/data/database/Migrations.kt` and an exported schema JSON in `app/schemas/`.
- **Offline First**: All remote operations (YouTube Music, Navidrome, Jellyfin, LRCLIB) must cache locally into Room DAOs (`YouTubeDao`, `LyricsDao`, `OfflineTrackDao`, etc.).

### 4. Navigation & Logging
- **Safe Navigation**: Use `navController.navigateSafely(...)` from `NavControllerExtensions.kt` to handle rapid tap events gracefully.
- **Logging**: Use `Timber`, never raw `android.util.Log`. Debug builds output to `DebugTree`; release builds automatically restrict logs to WARN/ERROR/WTF.

### 5. Localization & Strings
- All user-facing strings must reside in `app/src/main/res/values/strings.xml` and localized variant folders (`values-ar`, `values-de`, `values-es`, `values-fr`, `values-in`, `values-it`, `values-ko`, `values-nb`, `values-ru`, `values-tr`, `values-zh-rCN`).

---

## 🚀 Submitting Pull Requests

1. **Focus**: Keep PRs focused on a single logical feature, bugfix, or refactor.
2. **Branching**: Create a feature branch off `main` (e.g. `feat/your-feature` or `fix/issue-description`).
3. **Commit Messages**: Follow [Conventional Commits](https://www.conventionalcommits.org/) (e.g. `feat(radio): ...`, `fix(lyrics): ...`, `style(ui): ...`).
4. **Description**: Detail what was changed, why, and include output from `./gradlew :app:testDebugUnitTest --no-daemon` and `./gradlew assembleDebug --no-daemon`.
5. **No AI Trailers**: Do not add `Co-Authored-By:` trailers to commits or PR descriptions.

---

## 🐛 Reporting Bugs & Feature Requests

- Check [GitHub Issues](https://github.com/KDharshana/PixelPlayerOSS/issues) before opening a new ticket.
- Include:
  - App version (e.g. `v0.1.0-alpha.5`)
  - Device model & Android version
  - Music source involved (Local, YouTube Music, Navidrome, Jellyfin, ListenBrainz)
  - Clear steps to reproduce and relevant log snippets (via `adb logcat -s Tonarc:*`)
