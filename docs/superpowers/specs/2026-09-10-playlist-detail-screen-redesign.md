# Playlist Detail Screen Redesign Specification

**Date:** 2026-09-10  
**Feature:** Immersive Hero Header, Ambient Backdrop & Modern Layout for `PlaylistDetailScreen`  
**Status:** Approved

---

## 1. Overview & Goal

The current `PlaylistDetailScreen` in Tonarc lacks cover artwork representation, uses hardcoded asymmetric button corners, and features a plain `LargeFlexibleTopAppBar` that leaves the playlist feeling disconnected and bare.

This redesign transforms `PlaylistDetailScreen` into an expressive, modern music experience featuring:
1. An immersive hero header featuring `PlaylistCover` (supporting custom user images, colored shapes/icons, or dynamic 4-art album collages).
2. A subtle ambient gradient backdrop reflecting the playlist's cover color or theme.
3. A collapsible header on scroll with smooth scaling and fading, ensuring the compact top bar and core controls remain accessible.
4. Modernized Material 3 Expressive pill buttons for Play All and Shuffle, paired with streamlined quick action chips (+ Add, Reorder, Remove, Sort).
5. Clean track items with index numbers, duration, and seamless drag-and-drop reordering.

---

## 2. Detailed Component Architecture

### 2.1 Immersive Hero Header & Ambient Glow
- **Cover Display**:
  - Centered 180dp $\times$ 180dp `PlaylistCover` inside a rounded container with subtle 8dp elevation shadow.
  - Automatically handles:
    - User-uploaded custom image (`playlist.coverImageUri`).
    - Custom shape & icon (`coverShapeType`, `coverColorArgb`, `coverIconName`).
    - Dynamic 4-album-art collage (`PlaylistArtCollage`) built from the songs in the playlist.
- **Ambient Backdrop**:
  - Vertical gradient box at the top of the screen:
    - Colors: `[coverColor.copy(alpha = 0.45f), MaterialTheme.colorScheme.surface]`.
    - Height: 320dp, soft fade to surface.
- **Metadata Badges**:
  - Playlist Title: Bold `headlineMedium` (`RoundedSans`), center-aligned.
  - Subtitle Chips:
    - Source badge: If imported from Spotify or YouTube, or a Smart Playlist, display a small tonal chip badge.
    - Track count: e.g., "24 tracks" (or localized string).
    - Total runtime: e.g., "1 hr 18 min".

### 2.2 Collapsible Header Motion
- **Scroll Behavior**:
  - Using a `LazyListState` and `derivedStateOf` or `NestedScrollConnection`:
  - As user scrolls down:
    - Hero cover scales from $1.0\times \rightarrow 0.8\times$ and fades $\text{alpha } 1.0 \rightarrow 0.0$.
    - Hero title and metadata fade out.
    - Top bar background transitions from transparent to solid `surfaceContainer`.
    - Compact title and song count fade into the top bar (`alpha } 0.0 \rightarrow 1.0$).
- **Top App Bar**:
  - Back button (FilledTonalIconButton).
  - Collapsed title + subtitle (visible when scrolled).
  - Actions:
    - Sort songs icon button.
    - Cloud download icon button (when cloud tracks present).
    - More options icon button (3 dots: Edit, Delete, Export M3U, Set Default Transition).

### 2.3 Action Controls
- **Primary Playback Row**:
  - Centered or full-width row with two 48dp-height pill buttons:
    - **Play All**: `Button` with `CircleShape`, Play icon, and text label in `onPrimary` color.
    - **Shuffle**: `FilledTonalButton` with `CircleShape`, Shuffle icon, and text label in `onSecondaryContainer`.
- **Quick Action Chips Row**:
  - Horizontal scrollable/spaced row:
    - `AssistChip` / `FilterChip` for "+ Add songs" (when editable).
    - Toggle chip for "Reorder" (highlighted when active).
    - Toggle chip for "Remove" (highlighted when active).
    - Button/Chip for "Sort" or "Download".

### 2.4 Song List & Reordering
- Rendered inside `LazyColumn` using `ReorderableItem`:
  - When not reordering: Track number or album thumbnail, song title, artist subtitle, duration, and 3-dots more options.
  - When in Reorder mode: Shows drag handle icon on right edge; drag gesture reorders songs with haptic feedback.
  - When in Remove mode: Shows 1-tap remove icon on left/right edge.
- Padding:
  - Bottom content padding accounts for `MiniPlayerHeight` and `bottomBarHeightDp`.

---

## 3. Backward Compatibility & Non-Regressions

- **All Existing Dialogs & BottomSheets Preserved**:
  - `SongPickerBottomSheet` (+ Add Songs)
  - `SongInfoBottomSheet` (3-dots on track)
  - `PlaylistOptionsBottomSheet` / Edit Playlist dialog
  - `DeleteConfirmation` dialog
  - `LibrarySortBottomSheet`
  - M3U export launcher
- **Folder Playlists & Smart Playlists**:
  - Folder playlists remain non-editable (hides Add/Reorder/Remove).
  - Smart playlists show smart playlist rules and badge.
