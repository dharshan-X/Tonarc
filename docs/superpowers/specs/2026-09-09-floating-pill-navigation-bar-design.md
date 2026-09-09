# Floating Pill Navigation Bar Design Specification

- **Feature Name**: Floating Pill Navigation Bar
- **Author**: Antigravity Agent
- **Date**: 2026-09-09
- **Status**: Approved

---

## 1. Overview & Objective

Introduce a new modern, minimalist **Floating Pill** navigation bar style into Tonarc, matching the floating segmented capsule aesthetic requested by the user. 

The bar floats above the system navigation bar as a stadium-shaped pill containing Tonarc's core destinations (Home, Search, Library) in an icon-only format. An animated inner pill indicator slides smoothly behind the active tab. This is added as a user-selectable option (`NavBarStyle.FLOATING_PILL`) in **Settings > Appearance > Navigation Bar Style**, preserving the existing "Default" and "Full Width" styles.

---

## 2. Visual & UI Specifications

### 2.1 Container & Geometry
- **Shape**: Fully rounded stadium pill (`CircleShape` / `RoundedCornerShape(percent = 50)`).
- **Height**: 54dp content height.
- **Width**: Content-hugging capsule (~216dp to 228dp total width, ~64dp to 68dp per tab slot with 4dp horizontal padding).
- **Background**: `MaterialTheme.colorScheme.surfaceContainer` (or `surfaceColorAtElevation(3.dp)`), providing high contrast in both dark and light themes.
- **Border**: 1dp outline with `MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)` for crisp edge definition on varied screen backgrounds.
- **Elevation**: 4dp shadow elevation to produce the floating effect.
- **Placement**: Centered horizontally (`Alignment.BottomCenter`) with bottom margin of `8.dp + WindowInsets.navigationBars.bottomPadding`.

### 2.2 Active Tab Indicator (Segmented Capsule Selector)
- **Geometry**: Inner rounded capsule (`RoundedCornerShape(percent = 50)`), height ~42dp, width ~60dp.
- **Motion**: Horizontal translation animated using `animateDpAsState` (with a snappy spring spec: `dampingRatio = Spring.DampingRatioLowBouncy`, `stiffness = Spring.StiffnessMediumLow`) to smoothly glide between tabs upon selection.
- **Colors**:
  - Active indicator background: `MaterialTheme.colorScheme.secondaryContainer`.
  - Active icon tint: `MaterialTheme.colorScheme.onSecondaryContainer`.
  - Inactive icon tint: `MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)`.

### 2.3 Destinations & Interactions
- **Destinations**: 3 core tabs:
  1. **Home**: `R.drawable.rounded_home_24` (unselected), `R.drawable.home_24_rounded_filled` (selected).
  2. **Search**: `R.drawable.rounded_search_24`. Supports existing double-tap gesture to focus search or scroll up.
  3. **Library**: `R.drawable.rounded_library_music_24` (unselected), `R.drawable.round_library_music_24` (selected).
- **Haptic Feedback**: Performs subtle haptic tap on tab change.

---

## 3. Architecture & Component Structure

```
                             User Preferences
                        (PreferencesKeys.NAV_BAR_STYLE)
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                             MainActivity                                 │
│                                                                          │
│  navBarStyle == FLOATING_PILL ?                                          │
│    FloatingPillNavigationBar(...)                                        │
│  : PlayerInternalNavigationBar(...)                                      │
└────────────────────────────────────┬─────────────────────────────────────┘
                                     │
                                     ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                      FloatingPillNavigationBar                           │
│  ├── Stadium Surface (CircleShape, 54dp, 4dp shadow, 1dp border)        │
│  ├── Sliding Indicator Box (animateDpAsState offset, secondaryContainer) │
│  └── Tab Icons Row (Home, Search [double-tap], Library)                  │
└──────────────────────────────────────────────────────────────────────────┘
```

### 3.1 Preference Persistence
- Update `NavBarStyle.kt` (`data/preferences/NavBarStyle.kt`):
  ```kotlin
  object NavBarStyle {
      const val DEFAULT = "default"
      const val FULL_WIDTH = "full_width"
      const val FLOATING_PILL = "floating_pill"
  }
  ```
- Expose the option in `SettingsCategoryScreen.kt` under Appearance with localized string `setcat_navbar_style_floating_pill`.

### 3.2 Component Implementation: `FloatingPillNavigationBar.kt`
- Location: `app/src/main/java/com/quietrays/tonarc/presentation/components/FloatingPillNavigationBar.kt`
- Accepts:
  - `navController: NavHostController`
  - `navItems: ImmutableList<BottomNavItem>`
  - `currentRoute: String?`
  - `onSearchIconDoubleTap: () -> Unit`
  - `modifier: Modifier`
- Manages:
  - Selected tab index tracking (`0`, `1`, `2`) based on `currentRoute`.
  - Animated horizontal offset of the active pill indicator based on the selected index.
  - Search double-tap detection (350ms window) consistent with `PlayerInternalNavigationBar`.

### 3.3 Layout & MiniPlayer Coordination
- **Scaffold Integration (`MainActivity.kt`)**:
  - When `navBarStyle == NavBarStyle.FLOATING_PILL`, host `FloatingPillNavigationBar` in the bottom bar slot.
  - Applies identical translation hiding logic (`translationY = (componentHeightPx + shadowOverflowPx + bottomBarPaddingPx) * hideFraction`) when player expands or on sub-routes (`routesWithHiddenNavigationBar`).
- **Collapsed MiniPlayer Positioning (`SheetVisualState.kt`)**:
  - Calculate `collapsedStateBottomPadding`:
    - For `FLOATING_PILL`, the occupied height is `FloatingPillHeight (54.dp) + FloatingPillBottomMargin (8.dp) + systemNavBarInset + 8.dp (spacing)`.
    - MiniPlayer rests cleanly above the floating pill without overlap.

---

## 4. Verification & Testing

1. **Unit Tests**:
   - Add unit tests in `UserPreferencesRepositoryTest` verifying persistence and retrieval of `NavBarStyle.FLOATING_PILL`.
2. **UI & Regression Verification**:
   - Run `./gradlew :app:testDebugUnitTest --no-daemon` to ensure all existing and new unit tests pass.
   - Build debug APK with `./gradlew assembleDebug --no-daemon` to confirm compile-time integrity and layout generation.
   - Verify that switching between "Default", "Full Width", and "Floating Pill" in Settings operates smoothly with zero UI regressions.
