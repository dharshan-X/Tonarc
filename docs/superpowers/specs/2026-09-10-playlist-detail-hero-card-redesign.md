# Playlist Detail Screen Redesign Specification

**Date:** 2026-09-10  
**Feature:** Material 3 Expressive Dark Rounded Hero & Overlapping Seam Play Button for `PlaylistDetailScreen`  
**Reference Design:** `/tmp/orca-paste-1789030923638-44f004a7-3c29-47dd-9164-562c1cf1372b.png`  
**Interactive Mockup:** `docs/superpowers/mockups/playlist-detail-mockup.html`  
**Status:** In Review  

---

## 1. Overview & Visual Architecture

Transform the `PlaylistDetailScreen` to faithfully replicate the visual structure, proportions, and ergonomics of the new reference design (`/tmp/orca-paste-1789030923638-44f004a7-3c29-47dd-9164-562c1cf1372b.png`) translated to Android Material 3 Expressive:

```
+-------------------------------------------------------+
|  [≡ / ←]                                          [👤] |  <- Hero Top App Bar
|                                                       |
|                     [ COVER ART / ]                   |  <- Dark Rounded Hero Section
|                     [ ILLUSTRATION]                   |     (Rounded bottom corners: 36.dp)
|                                                       |
|  Trending / Playlist                                  |
|  Music Cover / Title                                  |
+-------------------------------------------------------+
|                                              ( ▶ )    |  <- Overlapping Seam Play FAB (56.dp)
+ - - - - - - - - - - - - - - - - - - - - - - - - - - - +
|  Top 10 / All Tracks (24)                     See All |  <- Section Header Row
|  ---------------------------------------------------  |
|  Lorem Ipsum / Track Title                     4:15 ⊙ |  <- Divider-separated track items
|  ---------------------------------------------------  |
|  Lorem Ipsum / Track Title                     3:20 ⊙ |  <- Zero scrollbar on edge
|  ---------------------------------------------------  |
+-------------------------------------------------------+
```

---

## 2. Component Specifications

### 2.1 Dark Rounded Hero Section (`PlaylistHeroSection`)
* **Container**:
  * Shape: `RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)`.
  * Background: Deep tonal dark surface (`MaterialTheme.colorScheme.surfaceContainerLowest` or `#19181b`).
  * Height: ~340dp (responsive, accounting for status bar).
  * Insets: Handles `statusBarsPadding()` internally.
* **Top Navigation Row**:
  * Left: Navigation/Back icon button (`Icons.AutoMirrored.Rounded.ArrowBack` or menu).
  * Right: Profile/Playlist options button (`Icons.Rounded.MoreVert` or collaborator avatar).
* **Center Artwork**:
  * Cover art or stylized illustration scaled with `ContentScale.Fit` or `ContentScale.Crop` with soft rounded corners (`24.dp`) and drop shadow.
* **Bottom-Left Typography**:
  * **Tag / Category**: `labelMedium`, subtle tracking, e.g. "PLAYLIST" or "TRENDING".
  * **Title**: `headlineLarge` with expressive cursive / display styling (e.g. bold rounded typography or cursive display with drop shadow).

### 2.2 Overlapping Seam Play Button (`FloatingPlayActionButton`)
* **Placement**: Anchored on the horizontal seam dividing the hero card and the track list sheet.
  * `size = 56.dp`, `shape = CircleShape`.
  * Position: Aligned to `Alignment.TopEnd` with `padding(end = 24.dp)` and `offset(y = (-28).dp)`.
* **Colors & Motion**:
  * Background: `MaterialTheme.colorScheme.primaryContainer` or tonal grey (`#9a959c`).
  * Icon: `Icons.Rounded.PlayArrow` / `Icons.Rounded.Pause` in `onPrimaryContainer` with spring scale transition on click.
  * Elevation: 6dp drop shadow.

### 2.3 Elevated Track List Surface & Scrollbar Elimination
* **Scrollbar Removal**:
  * Completely remove `ExpressiveScrollBar` and any end padding associated with it from `PlaylistDetailScreen.kt`.
* **Section Header**:
  * "Top 10" / "All Tracks (Count)" on start, "Reorder / See All" on end.
* **Track Items**:
  * Clean row with full-width subtle horizontal divider (`HorizontalDivider`).
  * Start: Track title and artist subtitle.
  * End: Duration text ("4:15") + circular options/more button (`Icons.Rounded.MoreVert` or circular dot).
  * Preserves drag-and-drop reordering when reorder mode is active.

---

## 3. Verification & HTML Mockup

The interactive HTML verification file has been updated at:
* `docs/superpowers/mockups/playlist-detail-mockup.html`

Modes supported in the mockup:
1. **Exact Reference Replica**: 100% pixel-accurate match with `/tmp/orca-paste-1789030923638-44f004a7-3c29-47dd-9164-562c1cf1372b.png`.
2. **M3 Expressive Dark Theme**: Shows how this exact structure renders in dark OLED mode for Pixel / Tonarc.
3. **Side-by-Side Verification**: Direct side-by-side comparison of the rendered component against the user's reference image.
