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
3. **Pastel Badge List Tiles**: Full-width edge-to-edge flat list tiles (zero shadow, zero elevation, 100% width spanning borders) with circular pastel tonal badges (`44.dp`), high-contrast two-line typography, subtle hairline dividers, and trailing duration/options.
4. **Zero Scrollbars**: Completely clean edges (`ExpressiveScrollBar` eliminated).

```
+-------------------------------------------------------------+
|                                                             | <- Clean Hero Top (No Menu/Profile Btns)
|                                                             |
|                   [ ARTWORK / ILLUSTRATION ]                | <- Ambient glow
|                   [ WITH DROP SHADOW       ]                |
|                                                             |
|  ● TRENDING PLAYLIST                                        |
|  Music Cover (headlineLarge • Expressive Display Script)    |
|                                                   ( ▶ )     | <- Floating Play FAB (56.dp)
+-------------------------------------------------------------+    Overlaps seam (offset y = -28.dp)
|  Top 10                                             See All |
|  +-------------------------------------------------------+  |
|  | (ılı) Sound and Haptics                       4:15  ⋮ |  | <- Pastel Blue (#7fc4fd) with animated equalizer
|  | ----------------------------------------------------- |  |    Hairline Divider
|  | (🛡️) Security and Privacy                     3:20  ⋮ |  | <- Pastel Lime (#dce775)
|  | ----------------------------------------------------- |  |
|  | (🔔) Notifications and Alerts                 5:05  ⋮ |  | <- Pastel Purple (#b39ddb)
|  | ----------------------------------------------------- |  |
|  | (📱) Input and Actions                        4:40  ⋮ |  | <- Pastel Orange (#ffcc80)
|  | ----------------------------------------------------- |  |
|  | (⊞) Widgets                                   3:20  ⋮ |  | <- Pastel Chartreuse (#d4e157)
|  | ----------------------------------------------------- |  |
|  | (🖥️) Display                                  4:12  ⋮ |  | <- Pastel Mint (#a5d6a7)
|  | ----------------------------------------------------- |  |
|  | (⌚) Watch                                    2:48  ⋮ |  | <- Pastel Sage (#c5e1a5)
|  +-------------------------------------------------------+  | <- Flat Surface with Hairline Dividers (0dp Elevation)
+-------------------------------------------------------------+
   ▲ ▲ ▲  ENTIRE SCREEN SCROLLS TOGETHER (NO INNER SCROLL CONTAINER)
```

---

## 2. Component Specifications

### 2.1 Full-Page Scroll Container (`PlaylistDetailScreen`)
* Implemented via a single `LazyColumn` extending edge-to-edge.
* `item { PlaylistHeroSection(...) }` as the first item.
* `item { SectionHeaderRow(...) }` as the second item.
* `itemsIndexed(songs) { ... }` rendering the list tiles inside the rounded card group.
* No nested inner scroll view; no scrollbar.

### 2.2 Hero Section (`PlaylistHeroSection`)
* **Shape**: `RoundedCornerShape(bottomStart = 38.dp, bottomEnd = 38.dp)`.
* **Surface**: `MaterialTheme.colorScheme.surfaceContainerLowest` (#16151a).
* **Artwork**: Centered high-resolution cover artwork / illustration with dynamic ambient glow.
* **Overlapping Play FAB**: `56.dp` circle (`FloatingActionButton`) anchored directly across the seam (`offset(y = (-28).dp)`).

### 2.3 Pastel Badge List Tiles (`PlaylistSongTile`)
* **Full-Width Edge-to-Edge List**: Tiles span 100% full width with 0dp elevation, zero shadow, and 1dp horizontal dividers spanning edge-to-edge, framed by 20dp internal horizontal padding.
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
  * When track is active: Displays live animated equalizer wave bars (`mini-wave-box`).
* **Content Hierarchy**:
  * Title: `titleMedium` (`FontWeight.Bold`), `onSurface` color.
  * Subtitle: `bodySmall` (`FontWeight.Normal`), `onSurfaceVariant` color.
* **Trailing Actions**:
  * Duration text (`labelMedium`, e.g. "4:15").
  * More options button (`IconButton`, `Icons.Rounded.MoreVert`).
* **Dividers**: `HorizontalDivider` between tiles, with no divider below the last item.
