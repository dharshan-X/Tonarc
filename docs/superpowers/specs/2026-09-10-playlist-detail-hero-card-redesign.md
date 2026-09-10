# Playlist Detail Screen Redesign Specification

**Date:** 2026-09-10  
**Feature:** Material 3 Expressive Dark Rounded Hero & Pastel Badge Rounded List Tiles for `PlaylistDetailScreen`  
**Reference Designs:**
- Hero Section: `/tmp/orca-paste-1789030923638-44f004a7-3c29-47dd-9164-562c1cf1372b.png`
- List Tiles: `/tmp/orca-paste-1789032899976-fe7c3ccb-a0de-4457-adb6-fdc6c615db8b.png`  
**Interactive Mockup:** `docs/superpowers/mockups/playlist-detail-mockup.html`  
**Status:** In Review  

---

## 1. Overview & Visual Architecture

Transform `PlaylistDetailScreen` to unify:
1. **Hero Section**: The dark rounded card with bottom curvature (`RoundedCornerShape(bottomStart = 38.dp, bottomEnd = 38.dp)`), centered artwork with ambient glow, bold expressive typography, and the overlapping seam Floating Action Button (`56.dp`).
2. **List Tiles**: Grouped rounded container card (`RoundedCornerShape(26.dp)`) containing list tiles with circular pastel tonal badges (`44.dp`), high-contrast two-line typography, subtle hairline dividers, and trailing duration/options.
3. **Scrollbar**: Zero scrollbars (`ExpressiveScrollBar` completely removed).

```
+-------------------------------------------------------------+
|  [≡ NavigationIcon]                     [👤 ExpressiveAvatar]| <- TopAppBar
|                                                             |
|                   [ ARTWORK / ILLUSTRATION ]                | <- Ambient glow
|                   [ WITH DROP SHADOW       ]                |
|                                                             |
|  ● TRENDING PLAYLIST                                        |
|  Music Cover (headlineLarge • Expressive Display Script)    |
+-------------------------------------------------------------+ <- RoundedCornerShape(bottom = 38.dp)
|                                                   ( ▶ )     | <- FloatingActionButton (56.dp)
+ - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +    Anchored on seam (offset y = -28.dp)
|  Top 10                                             See All |
|  +-------------------------------------------------------+  |
|  | (ılı) Sound and Haptics                       4:15  ⋮ |  | <- Pastel Badge (Blue • #7fc4fd) with equalizer
|  | ----------------------------------------------------- |  |    Hairline Divider
|  | (🛡️) Security and Privacy                     3:20  ⋮ |  | <- Pastel Badge (Lime • #dce775)
|  | ----------------------------------------------------- |  |
|  | (🔔) Notifications and Alerts                 5:05  ⋮ |  | <- Pastel Badge (Purple • #b39ddb)
|  | ----------------------------------------------------- |  |
|  | (📱) Input and Actions                        4:40  ⋮ |  | <- Pastel Badge (Orange • #ffcc80)
|  +-------------------------------------------------------+  | <- Grouped Container (RoundedCornerShape(26.dp))
+-------------------------------------------------------------+
```

---

## 2. Component Specifications

### 2.1 Hero Section (`PlaylistHeroSection`)
* **Shape**: `RoundedCornerShape(bottomStart = 38.dp, bottomEnd = 38.dp)`.
* **Surface**: `MaterialTheme.colorScheme.surfaceContainerLowest` (#16151a).
* **Artwork**: Centered high-resolution cover artwork / illustration with dynamic ambient glow tinted by `MaterialTheme.colorScheme.primary`.
* **Overlapping Play FAB**: `56.dp` circle (`FloatingActionButton`) anchored directly across the seam (`offset(y = (-28).dp)`).

### 2.2 Pastel Badge List Tiles (`PlaylistSongTile`)
* **Outer Container**: Grouped `Card` or `Surface` with `RoundedCornerShape(26.dp)`, `surfaceContainer` color, and subtle outline.
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

---

## 3. Verification & Mockup

Interactive HTML mockup updated at:
* `docs/superpowers/mockups/playlist-detail-mockup.html`

Modes available:
- **Unified Screen**: Full preview with Hero Card + Pastel Badge List Tiles.
- **Compare List Tile**: Side-by-side comparison with `/tmp/orca-paste-1789032899976-fe7c3ccb-a0de-4457-adb6-fdc6c615db8b.png`.
- **Compare Hero Card**: Side-by-side comparison with `/tmp/orca-paste-1789030923638-44f004a7-3c29-47dd-9164-562c1cf1372b.png`.
