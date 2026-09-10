# Playlist Detail Screen Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform `PlaylistDetailScreen` into an immersive, modern Material 3 Expressive experience with hero artwork (`PlaylistCover`), ambient gradient backdrop, collapsible scroll motion, and sleek pill controls.

**Architecture:** Create a dedicated `PlaylistHeroHeader` component rendering the playlist cover art, ambient glow, title, source/duration badges, and modern Play/Shuffle pills. Integrate it into `PlaylistDetailScreen` with a smooth scroll-driven collapse mechanism that transitions into a compact top app bar without breaking existing reorder, edit, and deletion flows.

**Tech Stack:** Jetpack Compose, Material 3 Expressive, Coil, Reorderable LazyColumn, Dagger Hilt.

## Global Constraints

- Append `--no-daemon` to all Gradle invocations.
- Format all file references as markdown links with the `file://` scheme.
- Maintain existing playlist capabilities: reordering songs, remove mode, adding songs, sort sheet, cloud downloads, M3U export, edit/delete dialogs.
- Keep UI rendering 60–120 FPS fluid using `graphicsLayer` where applicable.

---

### Task 1: Create `PlaylistHeroHeader` Composable Component

**Files:**
- Create: `app/src/main/java/com/quietrays/tonarc/presentation/components/PlaylistHeroHeader.kt`
- Test: `app/src/test/java/com/quietrays/tonarc/presentation/components/PlaylistHeroHeaderTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  @Composable
  fun PlaylistHeroHeader(
      playlist: Playlist,
      songs: ImmutableList<Song>,
      isFolderPlaylist: Boolean,
      isSmartPlaylist: Boolean,
      onPlayAllClick: () -> Unit,
      onShuffleClick: () -> Unit,
      modifier: Modifier = Modifier,
      scale: Float = 1.0f,
      alpha: Float = 1.0f
  )
  ```

- [ ] **Step 1: Write unit test for helper formatting / layout resolution**

Create `app/src/test/java/com/quietrays/tonarc/presentation/components/PlaylistHeroHeaderTest.kt` verifying metadata formatting (songs count, duration, source badges) and helper logic.

- [ ] **Step 2: Run unit test to verify failure**

```bash
./gradlew :app:testDebugUnitTest --tests "*.PlaylistHeroHeaderTest" --no-daemon
```

- [ ] **Step 3: Implement `PlaylistHeroHeader.kt`**

Implement `PlaylistHeroHeader.kt` containing:
- Ambient vertical gradient backdrop using playlist cover color or theme `secondaryContainer`.
- Centered 180dp $\times$ 180dp `PlaylistCover` with subtle shadow elevation.
- Prominent playlist name in `headlineMedium` (`RoundedSans`).
- Metadata row: Source badge (Spotify, YouTube, Smart Playlist), song count, total duration.
- Unified Play All (`Button` with `CircleShape`) and Shuffle (`FilledTonalButton` with `CircleShape`) 50dp pill buttons.
- `graphicsLayer` scale and alpha modifier support for smooth collapse motion.

- [ ] **Step 4: Run unit tests to verify pass**

```bash
./gradlew :app:testDebugUnitTest --tests "*.PlaylistHeroHeaderTest" --no-daemon
```

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/components/PlaylistHeroHeader.kt app/src/test/java/com/quietrays/tonarc/presentation/components/PlaylistHeroHeaderTest.kt
git commit -m "feat(playlist): create PlaylistHeroHeader component with ambient backdrop and cover art"
```

---

### Task 2: Integrate `PlaylistHeroHeader` and Collapsible Scroll Motion in `PlaylistDetailScreen.kt`

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/screens/PlaylistDetailScreen.kt`

**Interfaces:**
- Consumes:
  - `PlaylistHeroHeader` from Task 1
  - `PlaylistCover`
  - `PlaylistViewModel` & `PlayerViewModel`
- Produces:
  - Fluid collapsible header scrolling in `PlaylistDetailScreen`
  - Streamlined action chips row (+ Add Songs, Reorder, Remove, Sort)
  - Full preservation of drag-and-drop reordering, deletion, and edit sheets

- [ ] **Step 1: Wire scroll-driven collapse calculations in `PlaylistDetailScreen`**

Use `derivedStateOf` over `listState` to calculate `collapseFraction`:
- `collapseFraction` smoothly transitions from 0.0f (at top) to 1.0f (scrolled past hero header).
- In the top bar: Show title and track count when `collapseFraction > 0.6f` with animated alpha fade.
- In the hero header: Fade out and scale down slightly (`scale = 1.0f - (collapseFraction * 0.15f)`, `alpha = (1.0f - collapseFraction * 1.5f).coerceIn(0f, 1f)`).

- [ ] **Step 2: Replace legacy header with `PlaylistHeroHeader` and streamlined action chips**

In `PlaylistDetailScreen.kt`:
- Place `PlaylistHeroHeader` as the first item or header in the layout.
- Modernize the action chips row (+ Add, Reorder, Remove, Sort) with `FilterChip` / `AssistChip` styling.
- Ensure song items, drag reordering handles, and remove mode badges render cleanly.

- [ ] **Step 3: Verify with unit tests and assemble debug build**

```bash
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew assembleDebug --no-daemon
```

- [ ] **Step 4: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/screens/PlaylistDetailScreen.kt
git commit -m "feat(playlist): integrate immersive hero header and collapsible motion into PlaylistDetailScreen"
```

---

### Task 3: Update Changelogs, Full Verification & Remote Pushes

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `fastlane/metadata/android/en-US/changelogs/2.txt`

- [ ] **Step 1: Document playlist page redesign in `CHANGELOG.md` and fastlane metadata**
- [ ] **Step 2: Run all unit tests to ensure zero regressions**
- [ ] **Step 3: Commit changelogs and push to `tonarc/main` and `origin/tonarc-main`**
