# Playlist Detail Screen Hero Card Redesign Specification

**Date:** 2026-09-10  
**Feature:** Material 3 Expressive Hero Card & Modern Control Bar for `PlaylistDetailScreen`  
**Status:** Approved by User  

---

## 1. Overview & Goals

Transform the playlist detail experience to match the modern media presentation reference (`/tmp/orca-paste-1789030107512-af2fa749-c691-4f66-abea-3d3f6dcb6eef.png`) translated to Material 3 Expressive design:
1. **Full-Bleed Rounded Hero Card**: Replace the small detached cover artwork with a full-bleed rounded card (`RoundedCornerShape(32.dp)`, ~320dp height) with multi-stop dark gradient scrim, overlaid typography, badges, and quick-action icon.
2. **Modern Three-Part Control Bar**: Centered circular 56dp primary Play button, flanked by a 44dp Shuffle pill on the left and a Vinyl Record / track counter pill on the right, followed by a streamlined action chips row (+ Add, Reorder, Remove).
3. **Scrollbar Removal**: Completely eliminate `ExpressiveScrollBar` for a cleaner, modern full-bleed aesthetic.
4. **Card-Style Track Items**: Render playlist tracks in clean rounded card containers (`RoundedCornerShape(20.dp)`).
5. **Fluid Collapsible Motion**: Smooth collapse on scroll into a compact top app bar, maintaining drag-and-drop safety for reordering.

---

## 2. Component Architecture

### 2.1 Immersive Full-Bleed Hero Card (`PlaylistHeroCard`)
Located in `app/src/main/java/com/quietrays/tonarc/presentation/components/PlaylistHeroCard.kt`.

* **Card Container**:
  * Shape: `RoundedCornerShape(32.dp)`
  * Height: ~320dp
  * Padding: `horizontal = 16.dp, top = 8.dp, bottom = 12.dp`
  * Shadow: 12dp elevation with ambient drop shadow.
* **Full-Bleed Artwork**:
  * Renders `PlaylistCover` filling the entire card area (`ContentScale.Crop`).
* **Protective Gradient Scrim**:
  * Multi-stop vertical gradient:
    * Top 30%: Subtle top vignette `Color.Black.copy(alpha = 0.4f) -> Color.Transparent` for top icons/badges.
    * Middle 30%: `Color.Transparent`.
    * Bottom 40%: Deep dark scrim `Color.Transparent -> Color.Black.copy(alpha = 0.6f) -> Color.Black.copy(alpha = 0.92f)` for high-contrast white text.
* **Overlaid Controls & Badges**:
  * **Top-Left Badge**: Translucent glass pill (`Surface`, `CircleShape`, container `Color.Black.copy(alpha = 0.45f)`) displaying source icon + badge text (e.g. 🎵 Smart Mix, 🔴 YouTube Music, 🟢 Spotify, 📁 Folder).
  * **Top-Right Action**: Circular tonal button (`size = 40.dp`, `CircleShape`, container `Color.Black.copy(alpha = 0.45f)`) with options or sort icon.
  * **Bottom-Left Overlaid Typography**:
    * **Title**: `headlineMedium`, `RoundedSans`, bold, white color (`Color.White`), with drop shadow.
    * **Metadata Row**: Icons + labels for total duration (`⏱ 1 hr 18 min`) and track count (`🎵 24 songs`).
  * **Bottom-Right Mini Inset Badge**:
    * Overlapping rounded badge (`RoundedCornerShape(16.dp)`) with mini vinyl disk / thumbnail graphic reflecting the reference mockup's avatar corner element.

### 2.2 Control Bar Below Hero Card
* **Center Play Button**:
  * Large circular button: `size = 56.dp`, `shape = CircleShape`.
  * Background: `MaterialTheme.colorScheme.primary`, icon `Icons.Rounded.PlayArrow` in `onPrimary`.
* **Left Shuffle Pill**:
  * Height: `44.dp`, `shape = CircleShape`.
  * Background: `MaterialTheme.colorScheme.secondaryContainer`, content `onSecondaryContainer`.
  * Icon: `Icons.Rounded.Shuffle` + "Shuffle" text label.
* **Right Track Counter & Vinyl Pill**:
  * Height: `44.dp`, `shape = CircleShape`.
  * Background: `MaterialTheme.colorScheme.surfaceContainerHigh`.
  * Content: Track counter text (e.g. `1 / 24` or song count) + vinyl record icon graphic (`R.drawable.music_record_icon` or vector).
* **Action Chips Row**:
  * Horizontal scrolling row placed directly beneath the playback controls:
    * **+ Add Songs** (when editable): `tertiaryContainer` pill.
    * **Reorder**: Toggle pill, highlighted in `primary` when active.
    * **Remove**: Toggle pill, highlighted in `errorContainer` when active.

### 2.3 Track List & Scroll Bar Removal
* **Remove Scroll Bar**:
  * Remove `ExpressiveScrollBar` and its end-padding offset from `PlaylistDetailScreen.kt`.
* **Track Items Styling**:
  * Each item rendered via `QueuePlaylistSongItem` inside `ReorderableItem`.
  * Preserves drag handle, remove buttons, and 3-dots more options.
  * Margins and padding refined to ensure comfortable tap targets and spacing.

### 2.4 Collapsible Motion Integration
* `NestedScrollConnection` dynamically scales down the hero card and controls ($1.0\times \rightarrow 0.88\times$) and fades alpha on scroll.
* Top app bar smoothly transitions to `surfaceContainerHigh` when scrolled, displaying the compact playlist title and song count.
* While reordering (`reorderableState.isAnyItemDragging`), scroll collapse is bypassed.

---

## 3. Backward Compatibility & Verification

* All existing actions preserved:
  * Song picker sheet (+ Add Songs)
  * Song info bottom sheet (3 dots on track)
  * Playlist options sheet (Edit, Delete, M3U Export, Set Default Transition)
  * Cloud downloads
  * Delete confirmation dialog
* Verification steps:
  * Unit tests: `./gradlew :app:testDebugUnitTest --no-daemon`
  * Debug APK build: `./gradlew assembleDebug --no-daemon`
