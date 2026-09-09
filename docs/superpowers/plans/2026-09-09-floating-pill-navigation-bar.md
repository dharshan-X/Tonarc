# Floating Pill Navigation Bar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a modern, minimalist Floating Pill navigation bar style (`NavBarStyle.FLOATING_PILL`) with an animated sliding active indicator in Tonarc, selectable in Settings.

**Architecture:** A dedicated `FloatingPillNavigationBar` composable rendered in `MainActivity`'s bottom slot when `NavBarStyle.FLOATING_PILL` is active. An animated capsule background slides horizontally to highlight the selected destination (Home, Search, Library). Layout and MiniPlayer spacing are coordinated via `SheetVisualState`.

**Tech Stack:** Jetpack Compose, Material 3, AndroidX WindowInsets, StateFlow/DataStore preferences.

## Global Constraints
- Append `--no-daemon` to all Gradle invocations.
- Format all file references as markdown links with the `file://` scheme.
- Maintain existing "Default" and "Full Width" navigation bar styles without regressions.
- All playback modifications route through `MusicService`/`MediaController`.

---

### Task 1: Preferences & Constants Support for `NavBarStyle.FLOATING_PILL`

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/data/preferences/NavBarStyle.kt`
- Modify: `app/src/main/res/values/strings_settings.xml`
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/screens/SettingsCategoryScreen.kt`
- Test: `app/src/test/java/com/quietrays/tonarc/data/preferences/NavBarStyleTest.kt`

**Interfaces:**
- Consumes: None
- Produces: `NavBarStyle.FLOATING_PILL = "floating_pill"`, localized string resource `R.string.setcat_navbar_style_floating_pill`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/quietrays/tonarc/data/preferences/NavBarStyleTest.kt`:
```kotlin
package com.quietrays.tonarc.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavBarStyleTest {

    @Test
    fun floatingPillConstant_hasExpectedValue() {
        assertEquals("floating_pill", NavBarStyle.FLOATING_PILL)
    }

    @Test
    fun navBarStyles_containsDefaultFullWidthAndFloatingPill() {
        val styles = listOf(
            NavBarStyle.DEFAULT,
            NavBarStyle.FULL_WIDTH,
            NavBarStyle.FLOATING_PILL
        )
        assertTrue(styles.contains("floating_pill"))
        assertTrue(styles.contains("default"))
        assertTrue(styles.contains("full_width"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "*.NavBarStyleTest" --no-daemon
```
Expected: FAIL with compilation error: "Unresolved reference: FLOATING_PILL".

- [ ] **Step 3: Write minimal implementation**

1. Update `app/src/main/java/com/quietrays/tonarc/data/preferences/NavBarStyle.kt`:
```kotlin
package com.quietrays.tonarc.data.preferences

object NavBarStyle {
    const val DEFAULT = "default"
    const val FULL_WIDTH = "full_width"
    const val FLOATING_PILL = "floating_pill"
}
```

2. Add string resource to `app/src/main/res/values/strings_settings.xml`:
```xml
    <string name="setcat_navbar_style_floating_pill">Floating Pill</string>
```

3. Update `app/src/main/java/com/quietrays/tonarc/presentation/screens/SettingsCategoryScreen.kt` in the `NavBarStyle` dropdown/radio list (around line 705):
```kotlin
    NavBarStyle.DEFAULT to stringResource(R.string.setcat_navbar_style_default),
    NavBarStyle.FULL_WIDTH to stringResource(R.string.setcat_navbar_style_full_width),
    NavBarStyle.FLOATING_PILL to stringResource(R.string.setcat_navbar_style_floating_pill)
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "*.NavBarStyleTest" --no-daemon
```
Expected: PASS (`BUILD SUCCESSFUL`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/quietrays/tonarc/data/preferences/NavBarStyle.kt \
        app/src/main/res/values/strings_settings.xml \
        app/src/main/java/com/quietrays/tonarc/presentation/screens/SettingsCategoryScreen.kt \
        app/src/test/java/com/quietrays/tonarc/data/preferences/NavBarStyleTest.kt
git commit -m "feat(settings): add FLOATING_PILL navigation bar style option"
```

---

### Task 2: Implement `FloatingPillNavigationBar` Composable Component

**Files:**
- Create: `app/src/main/java/com/quietrays/tonarc/presentation/components/FloatingPillNavigationBar.kt`
- Test: `app/src/test/java/com/quietrays/tonarc/presentation/components/FloatingPillNavigationBarTest.kt`

**Interfaces:**
- Consumes: `BottomNavItem`, `NavHostController`, `navigateToTopLevelSafely`
- Produces: `FloatingPillNavigationBar(...)` composable, `FloatingPillContentHeight = 54.dp`, `FloatingPillBottomMargin = 8.dp`, `calculatePillActiveOffset(...)`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/quietrays/tonarc/presentation/components/FloatingPillNavigationBarTest.kt`:
```kotlin
package com.quietrays.tonarc.presentation.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingPillNavigationBarTest {

    @Test
    fun calculatePillActiveOffset_returnsCorrectOffsetForIndices() {
        val slotWidth = 64.dp
        val horizontalPadding = 4.dp

        assertEquals(4.dp, calculatePillActiveOffset(0, slotWidth, horizontalPadding))
        assertEquals(68.dp, calculatePillActiveOffset(1, slotWidth, horizontalPadding))
        assertEquals(132.dp, calculatePillActiveOffset(2, slotWidth, horizontalPadding))
    }

    @Test
    fun resolveActiveIndex_returnsExpectedIndexForRoutes() {
        val routes = listOf("home", "search", "library")
        assertEquals(0, resolveActiveTabIndex("home", routes))
        assertEquals(1, resolveActiveTabIndex("search", routes))
        assertEquals(2, resolveActiveTabIndex("library", routes))
        assertEquals(0, resolveActiveTabIndex("unknown_route", routes))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "*.FloatingPillNavigationBarTest" --no-daemon
```
Expected: FAIL with compilation error: "Unresolved reference: calculatePillActiveOffset".

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/com/quietrays/tonarc/presentation/components/FloatingPillNavigationBar.kt`:
```kotlin
package com.quietrays.tonarc.presentation.components

import android.os.SystemClock
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.quietrays.tonarc.BottomNavItem
import com.quietrays.tonarc.presentation.navigation.Screen
import com.quietrays.tonarc.presentation.navigation.navigateToTopLevelSafely
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val FloatingPillContentHeight = 54.dp
val FloatingPillBottomMargin = 8.dp
private val FloatingPillSlotWidth = 66.dp
private val FloatingPillHorizontalPadding = 5.dp
private val FloatingPillIndicatorHeight = 42.dp
private val FloatingPillIndicatorWidth = 58.dp

internal fun calculatePillActiveOffset(
    selectedIndex: Int,
    slotWidth: Dp = FloatingPillSlotWidth,
    horizontalPadding: Dp = FloatingPillHorizontalPadding
): Dp {
    val indicatorPadding = (slotWidth - FloatingPillIndicatorWidth) / 2
    return horizontalPadding + (slotWidth * selectedIndex.coerceAtLeast(0)) + indicatorPadding
}

internal fun resolveActiveTabIndex(currentRoute: String?, routes: List<String>): Int {
    if (currentRoute == null) return 0
    val index = routes.indexOf(currentRoute)
    return if (index >= 0) index else 0
}

@Composable
fun FloatingPillNavigationBar(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    onSearchIconDoubleTap: () -> Unit = {}
) {
    val routes = remember(navItems) { navItems.map { it.screen.route } }
    val selectedIndex = resolveActiveTabIndex(currentRoute, routes)

    val animatedIndicatorOffset by animateDpAsState(
        targetValue = calculatePillActiveOffset(selectedIndex),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "FloatingPillActiveIndicatorOffset"
    )

    val totalWidth = (FloatingPillSlotWidth * navItems.size) + (FloatingPillHorizontalPadding * 2)

    val latestCurrentRoute by rememberUpdatedState(currentRoute)
    val latestOnSearchIconDoubleTap by rememberUpdatedState(onSearchIconDoubleTap)
    val scope = rememberCoroutineScope()
    var lastSearchTapTimestamp by remember { mutableLongStateOf(0L) }

    Surface(
        modifier = modifier
            .shadow(elevation = 4.dp, shape = CircleShape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = CircleShape
            )
            .clip(CircleShape),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(
            modifier = Modifier
                .height(FloatingPillContentHeight)
                .width(totalWidth),
            contentAlignment = Alignment.CenterStart
        ) {
            // Sliding active indicator capsule
            Box(
                modifier = Modifier
                    .offset(x = animatedIndicatorOffset)
                    .size(width = FloatingPillIndicatorWidth, height = FloatingPillIndicatorHeight)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            )

            // Destination icon slots
            Row(
                modifier = Modifier
                    .height(FloatingPillContentHeight)
                    .padding(horizontal = FloatingPillHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEachIndexed { index, item ->
                    val isSelected = index == selectedIndex
                    val iconRes = if (isSelected && item.selectedIconResId != null && item.selectedIconResId != 0) {
                        item.selectedIconResId
                    } else {
                        item.iconResId
                    }
                    val iconTint = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    }

                    Box(
                        modifier = Modifier
                            .width(FloatingPillSlotWidth)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true, radius = 24.dp)
                            ) {
                                val itemRoute = item.screen.route
                                val isSearchTab = itemRoute == Screen.Search.route
                                val isAlreadySelected = latestCurrentRoute == itemRoute

                                if (isSearchTab) {
                                    val now = SystemClock.elapsedRealtime()
                                    val isDoubleTap = now - lastSearchTapTimestamp <= 350L
                                    lastSearchTapTimestamp = now

                                    if (!isAlreadySelected) {
                                        if (!navController.navigateToTopLevelSafely(itemRoute)) {
                                            lastSearchTapTimestamp = 0L
                                            return@clickable
                                        }
                                    }

                                    if (isDoubleTap) {
                                        lastSearchTapTimestamp = 0L
                                        if (isAlreadySelected) {
                                            latestOnSearchIconDoubleTap()
                                        } else {
                                            scope.launch {
                                                delay(160L)
                                                latestOnSearchIconDoubleTap()
                                            }
                                        }
                                    }
                                } else if (!isAlreadySelected) {
                                    lastSearchTapTimestamp = 0L
                                    navController.navigateToTopLevelSafely(itemRoute)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = item.label,
                            tint = iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "*.FloatingPillNavigationBarTest" --no-daemon
```
Expected: PASS (`BUILD SUCCESSFUL`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/components/FloatingPillNavigationBar.kt \
        app/src/test/java/com/quietrays/tonarc/presentation/components/FloatingPillNavigationBarTest.kt
git commit -m "feat(ui): implement FloatingPillNavigationBar with sliding active indicator"
```

---

### Task 3: Coordinate Layout in `SheetVisualState.kt` and `MainActivity.kt`

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/components/scoped/SheetVisualState.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/MainActivity.kt`
- Test: `app/src/test/java/com/quietrays/tonarc/presentation/components/scoped/SheetVisualStateTest.kt`

**Interfaces:**
- Consumes: `FloatingPillNavigationBar`, `FloatingPillContentHeight`, `FloatingPillBottomMargin`, `NavBarStyle.FLOATING_PILL`
- Produces: Integrated bottom bar rendering in `MainActivity` and correct bottom padding in `SheetVisualState`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/quietrays/tonarc/presentation/components/scoped/SheetVisualStateTest.kt`:
```kotlin
package com.quietrays.tonarc.presentation.components.scoped

import androidx.compose.ui.unit.dp
import com.quietrays.tonarc.data.preferences.NavBarStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class SheetVisualStateTest {

    @Test
    fun resolveFloatingPillOccupiedHeight_calculatesCorrectTotal() {
        val systemInset = 24.dp
        val expected = 54.dp + 8.dp + 24.dp + 8.dp // pill height (54) + bottom margin (8) + systemInset (24) + spacing (8)
        assertEquals(expected, resolveFloatingPillOccupiedHeight(systemInset))
    }

    @Test
    fun resolveEffectiveCollapsedBottomPadding_handlesFloatingPill() {
        val systemInset = 16.dp
        val defaultOccupied = 90.dp + 16.dp
        val pillOccupied = resolveFloatingPillOccupiedHeight(systemInset)

        assertEquals(defaultOccupied, resolveNavBarOccupiedHeightForStyle(NavBarStyle.DEFAULT, systemInset))
        assertEquals(pillOccupied, resolveNavBarOccupiedHeightForStyle(NavBarStyle.FLOATING_PILL, systemInset))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "*.SheetVisualStateTest" --no-daemon
```
Expected: FAIL with compilation error: "Unresolved reference: resolveFloatingPillOccupiedHeight".

- [ ] **Step 3: Write minimal implementation**

1. In `app/src/main/java/com/quietrays/tonarc/presentation/components/scoped/SheetVisualState.kt`:
Add helper functions:
```kotlin
internal fun resolveFloatingPillOccupiedHeight(systemNavBarInset: Dp): Dp =
    FloatingPillContentHeight + FloatingPillBottomMargin + systemNavBarInset + 8.dp

internal fun resolveNavBarOccupiedHeightForStyle(navBarStyle: String, systemNavBarInset: Dp): Dp {
    return when (navBarStyle) {
        NavBarStyle.FLOATING_PILL -> resolveFloatingPillOccupiedHeight(systemNavBarInset)
        else -> 90.dp + systemNavBarInset
    }
}
```
And in `collapsedCornerTarget` and `collapsedRadius`:
```kotlin
} else if (navBarStyle == NavBarStyle.FLOATING_PILL) {
    32.dp
```

2. In `app/src/main/java/com/quietrays/tonarc/MainActivity.kt`:
Import `FloatingPillNavigationBar`, `FloatingPillContentHeight`, `FloatingPillBottomMargin`.
In the bottom bar slot of `MainUI`:
When `navBarStyle == NavBarStyle.FLOATING_PILL`:
Render `FloatingPillNavigationBar`:
```kotlin
if (navBarStyle == NavBarStyle.FLOATING_PILL) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomBarPadding + FloatingPillBottomMargin)
            .graphicsLayer {
                val expansionHide = if (showPlayerContentArea) {
                    playerViewModel.playerContentExpansionFraction.value.coerceIn(0f, 1f)
                } else {
                    0f
                }
                val routeHide = (1f - navBarVisibilityProgressState.value).coerceIn(0f, 1f)
                val hideFraction = maxOf(expansionHide, routeHide)
                translationY = (componentHeightPx + shadowOverflowPx + bottomBarPaddingPx) * hideFraction
                alpha = 1f - hideFraction
            }
            .onSizeChanged { componentHeightPx = it.height },
        contentAlignment = Alignment.BottomCenter
    ) {
        FloatingPillNavigationBar(
            navController = navController,
            navItems = commonNavItems,
            currentRoute = currentRoute,
            onSearchIconDoubleTap = onSearchIconDoubleTap
        )
    }
} else {
    // Existing Surface { PlayerInternalNavigationBar(...) }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "*.SheetVisualStateTest" --no-daemon
```
Expected: PASS (`BUILD SUCCESSFUL`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/components/scoped/SheetVisualState.kt \
        app/src/main/java/com/quietrays/tonarc/MainActivity.kt \
        app/src/test/java/com/quietrays/tonarc/presentation/components/scoped/SheetVisualStateTest.kt
git commit -m "feat(navigation): integrate FloatingPillNavigationBar into MainActivity and SheetVisualState"
```

---

### Task 4: Full App Build & Regression Verification

**Files:**
- None (verification only)

- [ ] **Step 1: Run all unit tests**

Run:
```bash
./gradlew :app:testDebugUnitTest --no-daemon
```
Expected: All 60+ unit tests pass with `BUILD SUCCESSFUL`.

- [ ] **Step 2: Build debug APK**

Run:
```bash
./gradlew assembleDebug --no-daemon
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Update documentation and finalize task tracking**

```bash
git add docs/superpowers/plans/2026-09-09-floating-pill-navigation-bar.md
git commit -m "docs(plan): complete implementation plan for floating pill navigation bar"
```
