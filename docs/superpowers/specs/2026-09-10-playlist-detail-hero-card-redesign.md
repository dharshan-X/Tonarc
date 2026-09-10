# Playlist Detail Screen Redesign Specification

**Date:** 2026-09-10  
**Feature:** Material 3 Expressive Dark Rounded Hero & Pastel Badge Rounded List Tiles with Full-Page Scrolling for `PlaylistDetailScreen`  
**Reference Designs:**
- Hero Section: `/tmp/orca-paste-1789030923638-44f004a7-3c29-47dd-9164-562c1cf1372b.png`
- List Tiles: `/tmp/orca-paste-1789032899976-fe7c3ccb-a0de-4457-adb6-fdc6c615db8b.png`  
**Interactive Mockup:** `docs/superpowers/mockups/playlist-detail-mockup.html`  
**Status:** In Review  

---

## 1. Overview & Scroll Architecture

The playlist detail screen is built on a **Full-Page Unified Scroll Architecture**:
1. **Unified Scroll Stream (`LazyColumn`)**: The **entire screen** scrolls as a single continuous document from top to bottom. There are **NO nested scroll containers**.
   - As the user scrolls down through the track list, the dark hero section scrolls up naturally (with optional subtle parallax/scale collapse into a compact top app bar).
   - The list tiles flow naturally down the screen, providing maximum vertical reading comfort.
2. **Hero Section**: The dark rounded card with bottom curvature (`RoundedCornerShape(bottomStart = 38.dp, bottomEnd = 38.dp)`), centered artwork with ambient glow, bold expressive typography, and the overlapping seam Floating Action Button (`56.dp`).
3. **Pastel Badge List Tiles**: Near full-width list with subtle 10.dp margin, 1dp outline border all around (top, bottom, left, right) with 20.dp rounded corners, zero shadow, and 0dp elevation with circular pastel tonal badges (`44.dp`), high-contrast two-line typography, subtle hairline dividers, and trailing duration/options.
4. **Zero Scrollbars**: Completely clean edges (`ExpressiveScrollBar` eliminated).

```
+-------------------------------------------------------------+
|                                                             | <- Clean Hero Top (No Menu/Profile Btns)
|                                                             |
|                 [ TONARC COVER ARTWORK ]                    | <- Dynamic Ambient Glow
|                 [ HIGH-RES VINYL / DISK ]                   |
|                                                             |
|  ● TONARC PLAYLIST                                          |
|  Midnight Lo-Fi Echoes                                      |
|  10 Tracks • 41 mins • Lossless FLAC                        |
|                                                   ( ▶ )     | <- Floating Play FAB (56.dp)
+-------------------------------------------------------------+    Overlaps seam (offset y = -28.dp)
|  [+ Add]  [↕ Reorder]  [✕ Remove]  [🔀 Shuffle]  [⇅ Sort]   | <- Action Chips Row
|                                                             |
|  Tracklist                                         10 Tracks|
|  +-------------------------------------------------------+  |
|  | (ılı) Midnight City - M83                     4:03  ⋮ |  | <- Pastel Blue (#7fc4fd) with animated equalizer
|  | ----------------------------------------------------- |  |    Hairline Divider
|  | (💿) Resonance - HOME                         3:32  ⋮ |  | <- Pastel Lime (#dce775)
|  | ----------------------------------------------------- |  |
|  | (⭐) Starboy - The Weeknd, Daft Punk          3:50  ⋮ |  | <- Pastel Purple (#b39ddb)
|  | ----------------------------------------------------- |  |
|  | (🌙) After Dark - Mr.Kitty                    4:19  ⋮ |  | <- Pastel Orange (#ffcc80)
|  | ----------------------------------------------------- |  |
|  | (🏎️) Nightcall - Kavinsky                     4:18  ⋮ |  | <- Pastel Chartreuse (#d4e157)
|  | ----------------------------------------------------- |  |
|  | (🎵) Memory Reboot - VØJ, Narvent             3:29  ⋮ |  | <- Pastel Mint (#a5d6a7)
|  | ----------------------------------------------------- |  |
|  | (💡) Blinding Lights - The Weeknd             3:20  ⋮ |  | <- Pastel Sage (#c5e1a5)
|  | ----------------------------------------------------- |  |
|  | (✨) The Less I Know The Better - Tame Impala 3:36  ⋮ |  | <- Pastel Coral (#ffab91)
|  | ----------------------------------------------------- |  |
|  | (🤖) Instant Crush - Daft Punk                5:37  ⋮ |  | <- Pastel Sky (#90caf9)
|  | ----------------------------------------------------- |  |
|  | (📼) Little Dark Age - MGMT                   4:59  ⋮ |  | <- Pastel Lavender (#ce93d8)
|  +-------------------------------------------------------+  | <- Flat Surface with Hairline Dividers (0dp Elevation)
+-------------------------------------------------------------+
   ▲ ▲ ▲  ENTIRE SCREEN SCROLLS TOGETHER (NO INNER SCROLL CONTAINER)
```

---

## 2. Color Schemes (6 Authentic Material 3 Palettes)

The mockup supports 6 distinct, accessible Material 3 Expressive palettes that map dynamically to Tonarc's theme system and cover art tinting:

| Color Scheme | Surface (Background & List Fill) | Hero Card (`surfaceContainerLowest`) | Primary Accent (FAB & Highlights) | Ambient Glow |
|---|---|---|---|---|
| **1. Violet Night (Default Tonarc)** | `#0f0e13` | `#16151a` | `#d0bcff` (Lavender) | `rgba(208, 188, 255, 0.38)` |
| **2. Terracotta Sunset** | `#140e0c` | `#1a1412` | `#ffb59d` (Warm Rust) | `rgba(255, 181, 157, 0.38)` |
| **3. Emerald Moss** | `#0a1210` | `#111b17` | `#81e6d9` (Mint Pine) | `rgba(129, 230, 217, 0.38)` |
| **4. Cyber Amber** | `#121008` | `#1b170f` | `#ffd166` (Golden Hour) | `rgba(255, 209, 102, 0.38)` |
| **5. Glacier Cyan** | `#0a1017` | `#101822` | `#80d8ff` (Frost Ice) | `rgba(128, 216, 255, 0.38)` |
| **6. Pure Light Mode** | `#f7f6fa` | `#edeaf4` | `#6750a4` (Deep Amethyst) | `rgba(103, 80, 164, 0.22)` |

---

## 3. Component Specifications & Feature Parity

### 3.1 Full-Page Scroll Container (`PlaylistDetailScreen`)
* Implemented via a single `LazyColumn` extending edge-to-edge.
* `item { PlaylistHeroSection(...) }` as the first item.
* `item { ActionChipsRow(...) }` as the second item (`+ Add Songs`, `Reorder`, `Remove`, `Shuffle`, `Sort`).
* `item { SectionHeaderRow(...) }` as the third item.
* `itemsIndexed(songs) { ... }` rendering the list tiles inside the rounded card group.
* Pinned compact top bar fading in on scroll (`listState.firstVisibleItemIndex > 0`).
* No nested inner scroll view; no scrollbar.

### 3.2 Hero Section (`PlaylistHeroSection`)
* **Shape**: `RoundedCornerShape(bottomStart = 38.dp, bottomEnd = 38.dp)`.
* **Surface**: `MaterialTheme.colorScheme.surfaceContainerLowest` (#16151a in Violet Night).
* **Clean Top**: Menu and profile buttons are completely removed. Single back button and playlist options trigger.
* **Artwork**: Centered high-resolution cover artwork / illustration with dynamic ambient glow tinted by playlist cover / theme color.
* **Overlapping Play FAB**: `56.dp` circle (`FloatingActionButton`) anchored directly across the dividing seam (`offset(y = (-28).dp)`), spring bounce animation, toggling Play/Pause state.

### 3.3 Pastel Badge List Tiles (`PlaylistSongTile`)
* **Subtle Outlined List Container**: Card spans nearly full width with a slim 10.dp margin from the screen edges, framed by a crisp 1dp outline border on all four sides (`RoundedCornerShape(20.dp)`), with background color identical to the page background (`surface`), 0dp elevation, zero shadow, and hairline dividers between items.
* **Reorder & Remove Modes**:
  * In Reorder mode: Drag handle (`Icons.Rounded.DragIndicator`) animates into view on each item, wired to `ReorderableItem`.
  * In Remove mode: Red circle remove button (`Icons.Default.RemoveCircleOutline`) animates into view on each item.
* **Leading Pastel Badge**:
  * Circular badge (`size = 44.dp`, `shape = CircleShape`).
  * Dynamic pastel tinted background with matching saturated icon:
    * Blue (`#7fc4fd`, icon `#0d47a1`)
    * Lime (`#dce775`, icon `#556b2f`)
    * Purple (`#b39ddb`, icon `#4a148c`)
    * Orange (`#ffcc80`, icon `#e65100`)
    * Chartreuse (`#d4e157`, icon `#33691e`)
    * Mint (`#a5d6a7`, icon `#1b5e20`)
    * Sage (`#c5e1a5`, icon `#2e7d32`)
    * Coral (`#ffab91`, icon `#b71c1c`)
    * Sky (`#90caf9`, icon `#0d47a1`)
    * Lavender (`#ce93d8`, icon `#4a148c`)
  * When track is active: Displays live animated equalizer wave bars (`mini-wave-box`).
* **Content Hierarchy**:
  * Title: `titleMedium` (`FontWeight.Bold`), `onSurface` color (or `primary` when active).
  * Subtitle: `bodySmall` (`FontWeight.Normal`), `onSurfaceVariant` color.
* **Trailing Actions**:
  * Duration text (`labelMedium`, e.g. "4:03").
  * More options button (`IconButton`, `Icons.Rounded.MoreVert`) opening `SongInfoBottomSheet`.
* **Dividers**: `HorizontalDivider` between tiles, with no divider below the last item.

### 3.4 Full Feature Parity Checklist
1. **Add Songs**: `SongPickerBottomSheet` triggered from `+ Add Songs` chip or empty playlist state.
2. **Reorder Songs**: Drag-and-drop using `ReorderableItem` and `rememberReorderableLazyListState`, persisting order changes to `PlaylistViewModel`.
3. **Remove Mode**: Toggleable remove buttons on each song, confirming removal from playlist.
4. **Sort Songs**: `LibrarySortBottomSheet` offering Title, Artist, Album, Date Added, and Duration sorting options.
5. **Playlist Options Sheet**: `PlaylistBottomSheet` offering Edit Details dialog, Delete Confirmation dialog, Set Default Transition route, and M3U playlist file export via `rememberLauncherForActivityResult`.
6. **Song Info Sheet**: `SongInfoBottomSheet` offering Favorite toggle, Add to Queue, Play Next, View Album, View Artist, and Metadata Edit.

