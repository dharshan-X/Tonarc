# Playlist Detail Screen Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform `PlaylistDetailScreen` into a unified full-page scrolling experience featuring a dark rounded hero card (`#16151a`), seam-overlapping Play FAB (`56.dp`), subtle outlined flat list container (`10.dp` margin, `1dp` border, `20.dp` radius, matching background), and circular pastel badges (`44.dp`) with animated equalizer wave bars, maintaining 100% feature parity.

**Architecture:** 
1. `PlaylistHeroSection.kt`: Dark rounded bottom card (`RoundedCornerShape(bottomStart = 38.dp, bottomEnd = 38.dp)`) with dynamic ambient glow tinted by theme/artwork, clean top (no menu/profile buttons), bold typography, and seam-overlapping Play FAB (`56.dp`).
2. `PlaylistSongTile.kt`: List tile with `44.dp` pastel circular badge, live animated equalizer wave bars on active track, duration, trailing options button, and animated slots for reorder drag handle and remove delete icon.
3. `PlaylistDetailScreen.kt`: Single unified `LazyColumn` scrolling the whole screen seamlessly; hero collapses into a pinned compact top bar. Completely eliminates `ExpressiveScrollBar`. Wraps song items in a subtle 1dp outlined container (`10.dp` side margin, `20.dp` corners, matching surface background) while keeping all existing Tonarc features intact: `SongPickerBottomSheet`, reorder drag-and-drop, remove mode, `LibrarySortBottomSheet`, `PlaylistBottomSheet`, `SongInfoBottomSheet`, and M3U export.

**Tech Stack:** Jetpack Compose, Material 3 Expressive, `sh.calvin.reorderable`, Coil, Dagger Hilt.

## Global Constraints

- Append `--no-daemon` to all Gradle invocations.
- Format all file references as markdown links with the `file://` scheme.
- Maintain existing playlist capabilities: reordering songs, remove mode, adding songs, sort sheet, cloud downloads, M3U export, edit/delete dialogs.
- Zero shadow & 0dp elevation for list container; background strictly matches page background (`surface`).
- Full-page scroll: entire screen scrolls together in one `LazyColumn`; no nested inner scroll containers.

---

### Task 1: Create `PlaylistHeroSection` Composable Component

**Files:**
- Create: `app/src/main/java/com/quietrays/tonarc/presentation/components/PlaylistHeroSection.kt`
- Test: `app/src/test/java/com/quietrays/tonarc/presentation/components/PlaylistHeroSectionTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  @Composable
  fun PlaylistHeroSection(
      playlist: Playlist,
      songs: ImmutableList<Song>,
      isFolderPlaylist: Boolean,
      isSmartPlaylist: Boolean,
      isPlaying: Boolean,
      onPlayFabClick: () -> Unit,
      onBackClick: () -> Unit,
      onOptionsClick: () -> Unit,
      modifier: Modifier = Modifier
  )
  ```

- [ ] **Step 1: Write unit test for `PlaylistHeroSection` metadata formatting & badge logic**

Create `app/src/test/java/com/quietrays/tonarc/presentation/components/PlaylistHeroSectionTest.kt` testing helper functions for badge text, duration formatting, and song count resolution.

- [ ] **Step 2: Run unit test to verify failure**

```bash
./gradlew :app:testDebugUnitTest --tests "*.PlaylistHeroSectionTest" --no-daemon
```

- [ ] **Step 3: Implement `PlaylistHeroSection.kt`**

Implement `PlaylistHeroSection.kt` in `presentation/components/`:
- `Surface` container with `color = MaterialTheme.colorScheme.surfaceContainerLowest` (#16151a).
- `shape = RoundedCornerShape(bottomStart = 38.dp, bottomEnd = 38.dp)`.
- Status bar padding and clean top row: Back button (`Icons.AutoMirrored.Rounded.ArrowBack`) and Options button (`Icons.Filled.MoreVert`). Completely remove menu and profile buttons.
- Centered artwork (`176.dp`) with dynamic ambient glow matching `playlist.coverColorArgb` or `MaterialTheme.colorScheme.primary`.
- Playlist metadata: tag badge (`● Tonarc Playlist`), title (`headlineMedium`, `RoundedSans`), duration and lossless/track info.
- Overlapping seam Play FAB: `FloatingActionButton` (`56.dp`, `CircleShape`) positioned at `Alignment.BottomEnd` with `offset(x = (-24).dp, y = 28.dp)` relative to hero card, toggling Play and Pause icons with spring bounce.

- [ ] **Step 4: Run unit tests to verify pass**

```bash
./gradlew :app:testDebugUnitTest --tests "*.PlaylistHeroSectionTest" --no-daemon
```

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/components/PlaylistHeroSection.kt app/src/test/java/com/quietrays/tonarc/presentation/components/PlaylistHeroSectionTest.kt
git commit -m "feat(playlist): create PlaylistHeroSection with dark rounded bottom card and seam Play FAB"
```

---

### Task 2: Create `PlaylistSongTile` Composable Component with Pastel Circular Badges

**Files:**
- Create: `app/src/main/java/com/quietrays/tonarc/presentation/components/PlaylistSongTile.kt`
- Test: `app/src/test/java/com/quietrays/tonarc/presentation/components/PlaylistSongTileTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  @Composable
  fun PlaylistSongTile(
      song: Song,
      index: Int,
      isCurrentSong: Boolean,
      isPlaying: Boolean,
      isReorderMode: Boolean,
      isRemoveMode: Boolean,
      onClick: () -> Unit,
      onRemoveClick: () -> Unit,
      onMoreOptionsClick: (Song) -> Unit,
      dragHandle: @Composable (() -> Unit)? = null,
      showDivider: Boolean = true,
      modifier: Modifier = Modifier
  )
  ```

- [ ] **Step 1: Write unit test for pastel badge color mapping**

Create `app/src/test/java/com/quietrays/tonarc/presentation/components/PlaylistSongTileTest.kt` verifying that pastel background and icon tint colors are deterministically resolved by track index / title hash (Blue, Lime, Purple, Orange, Chartreuse, Mint, Sage, Coral, Sky, Lavender).

- [ ] **Step 2: Run unit test to verify failure**

```bash
./gradlew :app:testDebugUnitTest --tests "*.PlaylistSongTileTest" --no-daemon
```

- [ ] **Step 3: Implement `PlaylistSongTile.kt`**

Implement `PlaylistSongTile.kt` with:
- Drag handle slot animated when `isReorderMode` is true (`Icons.Rounded.DragIndicator`).
- Remove button slot animated when `isRemoveMode` is true (`Icons.Default.RemoveCircleOutline` with error tint).
- `44.dp` circular badge (`CircleShape`) with pastel background and saturated icon / album art.
- Animated 3-bar equalizer wave bars (`mini-wave-box`) displayed inside the badge when `isCurrentSong && isPlaying`.
- Two-line typography: Title (`titleMedium`, `FontWeight.Bold`, `primary` when active) and Subtitle (`bodySmall`, `onSurfaceVariant`).
- Trailing duration text (`labelMedium`) and 3-dots `IconButton` (`Icons.Filled.MoreVert`).
- `HorizontalDivider` with hairline 0.8dp thickness (`MaterialTheme.colorScheme.surfaceContainerHigh`) below each tile except the last.

- [ ] **Step 4: Run unit tests to verify pass**

```bash
./gradlew :app:testDebugUnitTest --tests "*.PlaylistSongTileTest" --no-daemon
```

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/components/PlaylistSongTile.kt app/src/test/java/com/quietrays/tonarc/presentation/components/PlaylistSongTileTest.kt
git commit -m "feat(playlist): create PlaylistSongTile with pastel circular badges and animated equalizer"
```

---

### Task 3: Refactor `PlaylistDetailScreen.kt` with Full-Page Unified Scroll Architecture

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/screens/PlaylistDetailScreen.kt`

**Interfaces:**
- Consumes:
  - `PlaylistHeroSection` from Task 1
  - `PlaylistSongTile` from Task 2
  - `PlaylistViewModel` & `PlayerViewModel`
- Produces:
  - Single continuous `LazyColumn` scrolling edge-to-edge
  - Action chips row (`+ Add Songs`, `↕ Reorder`, `✕ Remove`, `🔀 Shuffle`, `⇅ Sort`)
  - Subtle outlined container (`10.dp` margin, `1dp` border, `20.dp` radius, matching background)
  - Pinned compact top bar fading in on scroll
  - 100% feature parity preserved: `SongPickerBottomSheet`, `ReorderableItem`, remove mode, `LibrarySortBottomSheet`, `PlaylistBottomSheet`, `SongInfoBottomSheet`, `m3uExportLauncher`.

- [ ] **Step 1: Replace split scroll layout with unified `LazyColumn`**

In `PlaylistDetailScreen.kt`:
- Eliminate inner nested scroll containers and remove `ExpressiveScrollBar`.
- Top-level `Box` containing:
  - Single `LazyColumn` with state `listState`.
  - Item 0: `PlaylistHeroSection` with seam Play FAB.
  - Item 1: Action chips row with horizontal scroll:
    - `+ Add Songs` button (opens `SongPickerBottomSheet`).
    - `↕ Reorder` button (toggles `isReorderModeEnabled`).
    - `✕ Remove` button (toggles `isRemoveModeEnabled`).
    - `🔀 Shuffle` button (calls `playerViewModel.playSongsShuffled`).
    - `⇅ Sort` button (calls `playerViewModel.showSortingSheet()`).
  - Item 2: Section header ("Tracklist • X Songs").
  - Item 3: Outlined list container:
    - `Modifier.padding(horizontal = 10.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color.Transparent)`
    - Inside, iterate over `localReorderableSongs` with `ReorderableItem(state = reorderableState, key = song.id)`.
    - Render `PlaylistSongTile` for each song.

- [ ] **Step 2: Implement pinned collapsed TopAppBar**

Fade in pinned collapsed top app bar when `listState.firstVisibleItemIndex > 0`:
- Back navigation button (`Icons.AutoMirrored.Rounded.ArrowBack`).
- Playlist title & track count text.
- Options button (`Icons.Filled.MoreVert`).
- Status bar padding.

- [ ] **Step 3: Verify all dialogs and sheets work with zero regressions**

Verify:
- `SongPickerBottomSheet` opens on `+ Add Songs`.
- Drag-and-drop reordering works with haptics and persists to database.
- Remove mode deletes songs.
- `LibrarySortBottomSheet` sorts playlist tracks.
- `PlaylistBottomSheet` triggers Edit, Delete, Transition, M3U export.
- `SongInfoBottomSheet` triggers Favorite, Play Next, Add to Queue.

- [ ] **Step 4: Run unit tests and assemble debug APK**

```bash
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew assembleDebug --no-daemon
```

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/screens/PlaylistDetailScreen.kt
git commit -m "feat(playlist): implement full-page scroll, outlined list container, and action chips in PlaylistDetailScreen"
```

---

### Task 4: Documentation & Final Verification

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `fastlane/metadata/android/en-US/changelogs/2.txt`

- [ ] **Step 1: Update CHANGELOG.md with playlist screen redesign**
- [ ] **Step 2: Run full unit test suite to verify zero regressions**

```bash
./gradlew :app:testDebugUnitTest --no-daemon
```

- [ ] **Step 3: Commit final documentation**

```bash
git add CHANGELOG.md fastlane/metadata/android/en-US/changelogs/2.txt
git commit -m "docs: document playlist detail screen Material 3 redesign in changelogs"
```
