# Floating Navigation Bar Fluid Physics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement organic fluid physics (dual-edge liquid stretch & squash and active icon spring pop) for `FloatingPillNavigationBar` with zero UI thread stutter.

**Architecture:** Split active indicator positioning into an asymmetric dual-edge spring (`headOffset` and `tailOffset`). Derive dynamic horizontal stretch ($S_x$) and volume-conserving vertical squash ($S_y$) from edge separation. Render all animations inside RenderNode `Modifier.graphicsLayer` blocks to bypass Compose recomposition and relayout entirely.

**Tech Stack:** Jetpack Compose, Compose Animation Core (`animateDpAsState`, `animateFloatAsState`, `spring`, `Spring`), JUnit 5.

## Global Constraints

- Append `--no-daemon` to all Gradle invocations.
- Format all file references as markdown links with the `file://` scheme.
- Maintain existing "Default" and "Full Width" navigation bar styles without regressions.
- All animated transforms (`translationX`, `scaleX`, `scaleY`) must reside inside `Modifier.graphicsLayer { ... }` blocks to ensure 60–120 FPS performance without recompositions.
- Max horizontal stretch capped at $1.35\times$; vertical squash cannot drop below $0.85\times$.

---

### Task 1: Add Pure Physics Functions & Unit Tests in `FloatingPillNavigationBarTest.kt`

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/components/FloatingPillNavigationBar.kt`
- Modify: `app/src/test/java/com/quietrays/tonarc/presentation/components/FloatingPillNavigationBarTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class PillFluidScale(val scaleX: Float, val scaleY: Float)
  internal fun calculatePillFluidScale(headOffset: Dp, tailOffset: Dp, baseWidth: Dp): PillFluidScale
  ```

- [ ] **Step 1: Write the failing unit tests for `calculatePillFluidScale`**

In `app/src/test/java/com/quietrays/tonarc/presentation/components/FloatingPillNavigationBarTest.kt`, add:
```kotlin
@Test
fun calculatePillFluidScale_whenAtRest_returnsNeutralScale() {
    val scale = calculatePillFluidScale(headOffset = 81.dp, tailOffset = 81.dp, baseWidth = 64.dp)
    assertEquals(1.0f, scale.scaleX, 0.001f)
    assertEquals(1.0f, scale.scaleY, 0.001f)
}

@Test
fun calculatePillFluidScale_whenStretchingRight_stretchesHorizontallyAndSquashesVertically() {
    val scale = calculatePillFluidScale(headOffset = 120.dp, tailOffset = 81.dp, baseWidth = 64.dp)
    assertTrue(scale.scaleX > 1.0f)
    assertTrue(scale.scaleY < 1.0f)
}

@Test
fun calculatePillFluidScale_whenStretchingLeft_stretchesHorizontallyAndSquashesVertically() {
    val scale = calculatePillFluidScale(headOffset = 40.dp, tailOffset = 81.dp, baseWidth = 64.dp)
    assertTrue(scale.scaleX > 1.0f)
    assertTrue(scale.scaleY < 1.0f)
}

@Test
fun calculatePillFluidScale_capsMaxStretchAndSquash() {
    val scale = calculatePillFluidScale(headOffset = 300.dp, tailOffset = 0.dp, baseWidth = 64.dp)
    assertEquals(1.35f, scale.scaleX, 0.001f)
    assertTrue(scale.scaleY >= 0.85f)
}
```

- [ ] **Step 2: Run unit test to verify failure**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "*.FloatingPillNavigationBarTest" --no-daemon
```
Confirm compilation fails due to unresolved `calculatePillFluidScale` / `PillFluidScale`.

- [ ] **Step 3: Implement `PillFluidScale` and `calculatePillFluidScale` in `FloatingPillNavigationBar.kt`**

Add pure helper functions to `FloatingPillNavigationBar.kt`:
```kotlin
internal data class PillFluidScale(val scaleX: Float, val scaleY: Float)

internal fun calculatePillFluidScale(headOffset: Dp, tailOffset: Dp, baseWidth: Dp): PillFluidScale {
    if (baseWidth <= 0.dp) return PillFluidScale(1.0f, 1.0f)
    val deltaX = kotlin.math.abs(headOffset.value - tailOffset.value)
    val stretchRatio = (deltaX / baseWidth.value) * 0.45f
    val rawScaleX = 1.0f + stretchRatio
    val scaleX = rawScaleX.coerceIn(1.0f, 1.35f)
    val squashRatio = (scaleX - 1.0f) * 0.35f
    val scaleY = (1.0f - squashRatio).coerceIn(0.85f, 1.0f)
    return PillFluidScale(scaleX = scaleX, scaleY = scaleY)
}
```

- [ ] **Step 4: Run unit tests to verify pass**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "*.FloatingPillNavigationBarTest" --no-daemon
```
Confirm all tests pass cleanly.

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/components/FloatingPillNavigationBar.kt app/src/test/java/com/quietrays/tonarc/presentation/components/FloatingPillNavigationBarTest.kt
git commit -m "feat(navigation): add calculatePillFluidScale physics calculation and unit tests"
```

---

### Task 2: Integrate Dual-Spring Fluid Droplet and Icon Pop in `FloatingPillNavigationBar`

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/components/FloatingPillNavigationBar.kt:80-230`

**Interfaces:**
- Consumes:
  - `calculatePillFluidScale(headOffset: Dp, tailOffset: Dp, baseWidth: Dp)`
  - `calculatePillActiveOffset(index: Int)`
- Produces:
  - Responsive, fluid liquid droplet indicator in `FloatingPillNavigationBar`
  - Spring-popping destination icon animation inside `Modifier.graphicsLayer`

- [ ] **Step 1: Replace single offset animation with dual `headOffset` and `tailOffset` springs**

In `FloatingPillNavigationBar.kt`:
```kotlin
val targetOffset = calculatePillActiveOffset(targetIndex)

// Head spring: fast, responsive leading edge
val animatedHeadOffset by animateDpAsState(
    targetValue = targetOffset,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    ),
    label = "FloatingPillHeadOffset"
)

// Tail spring: lagging trailing edge with subtle inertia
val animatedTailOffset by animateDpAsState(
    targetValue = targetOffset,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    ),
    label = "FloatingPillTailOffset"
)
```

- [ ] **Step 2: Apply center translation and dynamic scale in indicator's `graphicsLayer`**

Update indicator `Box`:
```kotlin
val fluidScale = calculatePillFluidScale(
    headOffset = animatedHeadOffset,
    tailOffset = animatedTailOffset,
    baseWidth = FloatingPillIndicatorWidth
)
val centerTranslation = (animatedHeadOffset + animatedTailOffset) / 2

Box(
    modifier = Modifier
        .graphicsLayer {
            translationX = centerTranslation.toPx()
            scaleX = fluidScale.scaleX
            scaleY = fluidScale.scaleY
        }
        .size(width = FloatingPillIndicatorWidth, height = FloatingPillIndicatorHeight)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.secondaryContainer)
)
```

- [ ] **Step 3: Add active icon spring pop animation**

In tab icon rendering:
```kotlin
val iconScale by animateFloatAsState(
    targetValue = if (isSelected) 1.0f else 0.88f,
    animationSpec = if (isSelected) {
        spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    } else {
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )
    },
    label = "FloatingPillIconScale_${item.screen.route}"
)

Icon(
    painter = painterResource(id = iconRes),
    contentDescription = item.label,
    tint = iconTint,
    modifier = Modifier
        .graphicsLayer {
            scaleX = iconScale
            scaleY = iconScale
        }
        .size(24.dp)
)
```

- [ ] **Step 4: Verify with unit tests and build debug APK**

Run:
```bash
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew assembleDebug --no-daemon
```
Confirm build succeeds with zero errors.

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/components/FloatingPillNavigationBar.kt
git commit -m "feat(navigation): integrate dual-spring liquid stretch & squash and icon pop physics"
```
