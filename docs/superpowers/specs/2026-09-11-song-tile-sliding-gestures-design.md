# Song Tile Sliding Gestures Design Specification

## Summary

This specification defines the architecture, interaction model, visual design, and verification plan for adding quick-action sliding gestures to song list items across Tonarc. Users can perform a horizontal swipe on any song tile to quickly trigger essential playback and management actions (Add to Queue, Remove from Playlist, Toggle Favorite) backed by Material 3 Expressive dynamic capsules and fluid tactile spring physics.

## Problem Statement

Navigating through secondary menus (overflow three-dot icons or long-press bottom sheets) to add a song to the current playback queue, remove a song from a playlist, or favorite a track introduces friction during continuous listening and library exploration. Standard modern music interfaces offer directional swipe gestures on list items to perform these frequent operations instantly.

## Goals

1. **Effortless Quick Actions**:
   - **Swipe Start-to-End (Swipe Right)**: Add song to playback queue on all screens.
   - **Swipe End-to-Start (Swipe Left)**: Contextual action:
     - In Playlist Detail screen: Remove song from the current playlist with an undo snackbar.
     - In other screens (Library, Search, Albums, Artists, Daily Mixes, Genre Detail, Recently Played): Toggle Favorite / Like state.
2. **Material 3 Expressive Visuals**:
   - Dynamic stadium capsule emerging behind the translating tile.
   - Container color transitions (`primaryContainer` for queue, `errorContainer` for remove, `tertiaryContainer` for favorite).
   - Fluid spring physics and tactile haptic ticks on threshold crossings.
3. **Non-Intrusive Integration**:
   - Encapsulate gesture handling in a standalone, reusable `SwipeableSongActionRow` composable wrapper.
   - Zero gesture conflicts with vertical list scrolling in parent `LazyColumn`.
   - Automatic disabling during playlist reorder mode and multi-selection mode.
4. **Full Accessibility**:
   - Expose actions via Compose `semantics { customActions = ... }` so TalkBack users can trigger gestures without swipe dexterity.

## Non-Goals

- Replacing the existing overflow three-dot menu; all options remain accessible via the menu sheet.
- Destructive physical item dismissal (tiles always spring back to their resting position; removals animate out via standard list item placement animations).

---

## Architecture & Component Design

### 1. `SwipeableSongActionRow` Composable

- **File**: [`app/src/main/java/com/quietrays/tonarc/presentation/components/SwipeableSongActionRow.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/presentation/components/SwipeableSongActionRow.kt)
- **Role**: Reusable wrapper that nests any song tile composable inside an interactive swipe container.

```kotlin
@Composable
fun SwipeableSongActionRow(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    startAction: SwipeActionConfig? = null,
    endAction: SwipeActionConfig? = null,
    onStartActionTriggered: () -> Unit = {},
    onEndActionTriggered: () -> Unit = {},
    content: @Composable () -> Unit
)
```

#### `SwipeActionConfig` Data Model
```kotlin
data class SwipeActionConfig(
    val icon: ImageVector,
    val contentDescription: String,
    val containerColor: Color,
    val contentColor: Color,
    val labelText: String? = null
)
```

### 2. Layering & Layout Structure

A `Box` containing two distinct visual planes:
1. **Background Action Layer**:
   - Pinned behind the foreground tile.
   - Houses the Material 3 Expressive stadium capsule.
   - Depending on the drag direction (`offset > 0` vs `offset < 0`), anchors to the start or end margin.
   - Capsule width dynamically scales from `40.dp` up to `130.dp` based on swipe displacement.
   - Icon smoothly scales from `0.85f` to `1.18f` via spring curves as the activation threshold is reached.
2. **Foreground Content Layer**:
   - Encapsulates `content()`.
   - Translates horizontally via `Modifier.graphicsLayer { translationX = currentOffsetPx }`.
   - Maintains full clickable semantics for regular song taps.

---

## Gesture & Motion Mechanics

### 1. Touch Slop & Scroll Coordination
- Uses `pointerInput` with horizontal drag detection:
  - Consumes horizontal drag delta only after surpassing Compose `viewConfiguration.touchSlop`.
  - Up/down movements immediately yield pointer control to the parent `LazyColumn` for buttery-smooth vertical scrolling without accidental action locking.

### 2. Thresholds & Elastic Tension
- **Activation Threshold**: `72.dp` to `80.dp` (approx. 22-25% of standard phone screen width).
- **Elastic Resistance**: Once drag exceeds the threshold, drag deltas are multiplied by a resistance factor of `0.35f` to produce realistic physical friction.
- **Velocity Fling**: If the user releases with horizontal velocity exceeding `450.dp/s` in the direction of the swipe, the action triggers even if absolute displacement is slightly below threshold.

### 3. Tactile Feedback (Haptics)
- When the drag displacement crosses the activation threshold in either direction, triggers a single tactile haptic feedback tick (`HapticFeedbackType.TextHandleMove`).
- If the drag pulls back below threshold without releasing, the haptic latch resets.

### 4. Snapback Spring Physics
- Upon gesture release:
  - If action is triggered: Calls the respective callback (`onStartActionTriggered` or `onEndActionTriggered`), provides a confirmation haptic pulse, and snaps the foreground tile back to `0.dp` using `spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)`.
  - If released prior to threshold: Tile bounces back to `0.dp` with no callback fired.

---

## Action Mapping & Screen Integration

### 1. Standard Screens Integration
- **Target File**: [`app/src/main/java/com/quietrays/tonarc/presentation/components/subcomps/EnhancedSongListItem.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/presentation/components/subcomps/EnhancedSongListItem.kt)
- **Applicable Screens**: Home, Library, Search, Album Detail, Artist Detail, Daily Mixes, Genre Detail, Recently Played.
- **Start Action (Swipe Right)**:
  - **Action**: Add to Queue (`playerViewModel.addToQueue(song)`).
  - **Colors**: `MaterialTheme.colorScheme.primaryContainer`, `onPrimaryContainer`.
  - **Icon**: `Icons.Rounded.QueueMusic` / `PlaylistAdd`.
  - **Feedback**: Transient snackbar `"Added to queue: {song.title}"`.
- **End Action (Swipe Left)**:
  - **Action**: Toggle Favorite (`playerViewModel.toggleFavorite(song)`).
  - **Colors**: `MaterialTheme.colorScheme.tertiaryContainer`, `onTertiaryContainer`.
  - **Icon**: `Icons.Rounded.Favorite` / `FavoriteBorder`.
  - **Feedback**: Toast/Snackbar `"Added to favorites"` / `"Removed from favorites"`.

### 2. Playlist Detail Screen Integration
- **Target File**: [`app/src/main/java/com/quietrays/tonarc/presentation/components/PlaylistSongTile.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/presentation/components/PlaylistSongTile.kt)
- **Start Action (Swipe Right)**:
  - **Action**: Add to Queue (`playerViewModel.addToQueue(song)`).
  - **Colors**: `MaterialTheme.colorScheme.primaryContainer`, `onPrimaryContainer`.
- **End Action (Swipe Left)**:
  - **Action**: Remove from Playlist (`playlistViewModel.removeSongFromPlaylist(playlistId, song.id)`).
  - **Colors**: `MaterialTheme.colorScheme.errorContainer`, `onErrorContainer`.
  - **Icon**: `Icons.Rounded.DeleteOutline`.
  - **Feedback**: Snackbar `"Removed from playlist: {song.title}"` with an `"Undo"` action.
- **Reorder Mode Safety**:
  - Automatically sets `enabled = false` when `isReorderMode` is active, allowing drag-reorder handles to function cleanly without touch contention.

---

## Accessibility & Localization

1. **Semantics Custom Actions**:
   - Song items attach `CustomAccessibilityAction` to their Compose semantics:
     - `"Add to queue"`
     - `"Remove from playlist"` / `"Toggle favorite"`
   - Screen readers announce and can execute these actions directly from TalkBack's local context menu.
2. **Right-to-Left (RTL) Support**:
   - Actions honor `LocalLayoutDirection.current`:
     - Start-to-End matches natural swipe reading direction across both LTR and RTL locales.

---

## Verification & Testing Plan

### 1. Unit Tests
- Create [`app/src/test/java/com/quietrays/tonarc/presentation/components/SwipeableSongActionRowTest.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/test/java/com/quietrays/tonarc/presentation/components/SwipeableSongActionRowTest.kt):
  - `test_dragPastThreshold_triggersStartActionOnRelease`
  - `test_dragPastThreshold_triggersEndActionOnRelease`
  - `test_dragBelowThreshold_doesNotTriggerActionAndResets`
  - `test_flingPastVelocityThreshold_triggersAction`
  - `test_disabledState_ignoresTouchDeltas`

### 2. Regression & Build Checks
- Run full unit tests: `./gradlew :app:testDebugUnitTest --no-daemon`
- Run debug APK compilation: `./gradlew assembleDebug --no-daemon`
- Verify zero-emoji adherence across all codebase additions.
