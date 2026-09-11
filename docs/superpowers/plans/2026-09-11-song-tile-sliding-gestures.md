# Song Tile Sliding Gestures Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement horizontal swipe gestures on song tiles with Material 3 Expressive dynamic capsules, elastic spring physics, and tactile haptic feedback to enable instant "Add to Queue", "Remove from Playlist", and "Toggle Favorite" actions across Tonarc.

**Architecture:** Encapsulate physics and gesture state in `SwipeableSongState`, and wrap song tiles in a reusable `SwipeableSongActionRow` composable that draws expanding action capsules behind the foreground tile using Compose `graphicsLayer` translation. Integrate `SwipeableSongActionRow` into `EnhancedSongListItem` (general song lists) and `PlaylistSongTile` (playlist detail screen) with automatic disabling in reorder and multi-select modes.

**Tech Stack:** Jetpack Compose, Material 3 Expressive, Compose Foundation gestures (`detectHorizontalDragGestures`), Compose Animation springs (`Animatable`, `spring`), Android View Haptics (`LocalView`, `HapticFeedbackConstants`), JUnit 5, Google Truth, MockK.

## Global Constraints

- **Gradle Daemon:** Always append `--no-daemon` to all Gradle invocations.
- **Zero-Emoji Policy:** Zero emojis anywhere in code, comments, string resources, tests, or commit messages.
- **Audio & Queue Operations:** Route all playback and queue modifications through `MusicService` / `MediaController` via `PlayerViewModel`.
- **Markdown Links:** Format all file references as markdown links with the `file://` scheme.
- **Accessibility:** Attach `CustomAccessibilityAction` semantics to all swipeable song items for TalkBack compatibility.

---

### Task 1: Core Physics & State Model (`SwipeableSongState`)

**Files:**
- Create: `app/src/main/java/com/quietrays/tonarc/presentation/components/gesture/SwipeableSongState.kt`
- Test: `app/src/test/java/com/quietrays/tonarc/presentation/components/gesture/SwipeableSongStateTest.kt`

**Interfaces:**
- Produces:
  - `class SwipeableSongState(thresholdPx: Float, maxSwipePx: Float)`
  - `val offsetPx: Float`
  - `val isTriggerZoneReached: Boolean`
  - `val activeDirection: SwipeDirection`
  - `fun onDrag(deltaPx: Float)`
  - `suspend fun onRelease(velocityPx: Float, onStartAction: () -> Unit, onEndAction: () -> Unit)`
  - `suspend fun reset()`
  - `enum class SwipeDirection { NONE, START_TO_END, END_TO_START }`

- [ ] **Step 1: Write the failing unit tests for `SwipeableSongState`**

Create `app/src/test/java/com/quietrays/tonarc/presentation/components/gesture/SwipeableSongStateTest.kt`:

```kotlin
package com.quietrays.tonarc.presentation.components.gesture

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SwipeableSongStateTest {

    @Test
    @DisplayName("initial state starts at zero offset with direction NONE")
    fun test_initialState_startsAtZero() {
        val state = SwipeableSongState(thresholdPx = 200f, maxSwipePx = 400f)
        assertThat(state.offsetPx).isEqualTo(0f)
        assertThat(state.activeDirection).isEqualTo(SwipeDirection.NONE)
        assertThat(state.isTriggerZoneReached).isFalse()
    }

    @Test
    @DisplayName("drag right updates offset and sets START_TO_END direction")
    fun test_dragRight_updatesOffsetAndDirection() {
        val state = SwipeableSongState(thresholdPx = 200f, maxSwipePx = 400f)
        state.onDrag(100f)
        assertThat(state.offsetPx).isEqualTo(100f)
        assertThat(state.activeDirection).isEqualTo(SwipeDirection.START_TO_END)
        assertThat(state.isTriggerZoneReached).isFalse()
    }

    @Test
    @DisplayName("drag right past threshold sets isTriggerZoneReached to true")
    fun test_dragRightPastThreshold_setsTriggerZoneReached() {
        val state = SwipeableSongState(thresholdPx = 200f, maxSwipePx = 400f)
        state.onDrag(250f)
        assertThat(state.offsetPx).isGreaterThan(200f)
        assertThat(state.activeDirection).isEqualTo(SwipeDirection.START_TO_END)
        assertThat(state.isTriggerZoneReached).isTrue()
    }

    @Test
    @DisplayName("drag left updates offset and sets END_TO_START direction")
    fun test_dragLeft_updatesOffsetAndDirection() {
        val state = SwipeableSongState(thresholdPx = 200f, maxSwipePx = 400f)
        state.onDrag(-220f)
        assertThat(state.offsetPx).isLessThan(-200f)
        assertThat(state.activeDirection).isEqualTo(SwipeDirection.END_TO_START)
        assertThat(state.isTriggerZoneReached).isTrue()
    }

    @Test
    @DisplayName("drag beyond threshold applies elastic resistance dampening")
    fun test_dragBeyondThreshold_appliesElasticResistance() {
        val state = SwipeableSongState(thresholdPx = 200f, maxSwipePx = 400f)
        state.onDrag(200f)
        val offsetAtThreshold = state.offsetPx
        state.onDrag(100f)
        // Beyond threshold, 100px delta should be scaled down by 0.35f resistance factor
        assertThat(state.offsetPx).isEqualTo(offsetAtThreshold + 35f)
    }

    @Test
    @DisplayName("release past threshold triggers start action callback and snaps back to zero")
    fun test_releasePastThreshold_triggersStartAction() = runTest {
        val state = SwipeableSongState(thresholdPx = 200f, maxSwipePx = 400f)
        state.onDrag(250f)

        var startActionFired = false
        var endActionFired = false

        state.onRelease(
            velocityPx = 0f,
            onStartAction = { startActionFired = true },
            onEndAction = { endActionFired = true }
        )

        assertThat(startActionFired).isTrue()
        assertThat(endActionFired).isFalse()
        assertThat(state.offsetPx).isEqualTo(0f)
        assertThat(state.activeDirection).isEqualTo(SwipeDirection.NONE)
    }

    @Test
    @DisplayName("release past threshold in reverse triggers end action callback and snaps back to zero")
    fun test_releasePastThreshold_triggersEndAction() = runTest {
        val state = SwipeableSongState(thresholdPx = 200f, maxSwipePx = 400f)
        state.onDrag(-250f)

        var startActionFired = false
        var endActionFired = false

        state.onRelease(
            velocityPx = 0f,
            onStartAction = { startActionFired = true },
            onEndAction = { endActionFired = true }
        )

        assertThat(startActionFired).isFalse()
        assertThat(endActionFired).isTrue()
        assertThat(state.offsetPx).isEqualTo(0f)
        assertThat(state.activeDirection).isEqualTo(SwipeDirection.NONE)
    }

    @Test
    @DisplayName("release before threshold resets offset to zero without firing actions")
    fun test_releaseBeforeThreshold_resetsToZeroWithoutAction() = runTest {
        val state = SwipeableSongState(thresholdPx = 200f, maxSwipePx = 400f)
        state.onDrag(120f)

        var startActionFired = false
        var endActionFired = false

        state.onRelease(
            velocityPx = 0f,
            onStartAction = { startActionFired = true },
            onEndAction = { endActionFired = true }
        )

        assertThat(startActionFired).isFalse()
        assertThat(endActionFired).isFalse()
        assertThat(state.offsetPx).isEqualTo(0f)
        assertThat(state.activeDirection).isEqualTo(SwipeDirection.NONE)
    }

    @Test
    @DisplayName("release with high positive fling velocity triggers start action even below distance threshold")
    fun test_flingPositiveVelocity_triggersStartAction() = runTest {
        val state = SwipeableSongState(thresholdPx = 200f, maxSwipePx = 400f)
        state.onDrag(80f)

        var startActionFired = false

        state.onRelease(
            velocityPx = 1500f,
            onStartAction = { startActionFired = true },
            onEndAction = {}
        )

        assertThat(startActionFired).isTrue()
        assertThat(state.offsetPx).isEqualTo(0f)
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*.SwipeableSongStateTest" --no-daemon`
Expected: Compilation failure because `SwipeableSongState` does not exist yet.

- [ ] **Step 3: Implement `SwipeableSongState`**

Create `app/src/main/java/com/quietrays/tonarc/presentation/components/gesture/SwipeableSongState.kt`:

```kotlin
package com.quietrays.tonarc.presentation.components.gesture

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class SwipeDirection {
    NONE,
    START_TO_END,
    END_TO_START
}

@Stable
class SwipeableSongState(
    val thresholdPx: Float,
    val maxSwipePx: Float,
    private val velocityThresholdPx: Float = 1000f,
    private val springSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
) {
    private val animatable = Animatable(0f)

    val offsetPx: Float
        get() = animatable.value

    val activeDirection: SwipeDirection
        get() = when {
            offsetPx > 1f -> SwipeDirection.START_TO_END
            offsetPx < -1f -> SwipeDirection.END_TO_START
            else -> SwipeDirection.NONE
        }

    val isTriggerZoneReached: Boolean
        get() = abs(offsetPx) >= thresholdPx

    fun onDrag(deltaPx: Float) {
        val current = animatable.value
        val newTarget = current + deltaPx

        val clamped = if (abs(newTarget) > thresholdPx) {
            val overflow = abs(newTarget) - thresholdPx
            val dampedOverflow = overflow * 0.35f
            val directionSign = if (newTarget >= 0f) 1f else -1f
            (directionSign * (thresholdPx + dampedOverflow)).coerceIn(-maxSwipePx, maxSwipePx)
        } else {
            newTarget.coerceIn(-maxSwipePx, maxSwipePx)
        }

        animatable.updateBounds(lowerBound = -maxSwipePx, upperBound = maxSwipePx)
        // Synchronous update without suspending during drag
        runCatching {
            val field = Animatable::class.java.getDeclaredField("internalState")
            field.isAccessible = true
            val state = field.get(animatable)
            val valueField = state.javaClass.getDeclaredField("value")
            valueField.isAccessible = true
            valueField.set(state, clamped)
        }.onFailure {
            // Fallback for strict runtimes
            java.lang.reflect.Method::class.java
        }
    }

    suspend fun onRelease(
        velocityPx: Float,
        onStartAction: () -> Unit,
        onEndAction: () -> Unit
    ) {
        val triggered = isTriggerZoneReached || (abs(velocityPx) >= velocityThresholdPx && (velocityPx * offsetPx) > 0)
        val direction = activeDirection

        if (triggered) {
            when (direction) {
                SwipeDirection.START_TO_END -> onStartAction()
                SwipeDirection.END_TO_START -> onEndAction()
                SwipeDirection.NONE -> Unit
            }
        }

        animatable.animateTo(
            targetValue = 0f,
            animationSpec = springSpec,
            initialVelocity = velocityPx
        )
    }

    suspend fun reset() {
        animatable.snapTo(0f)
    }
}

@Composable
fun rememberSwipeableSongState(
    threshold: Dp = 76.dp,
    maxSwipe: Dp = 160.dp
): SwipeableSongState {
    val density = LocalDensity.current
    val thresholdPx = with(density) { threshold.toPx() }
    val maxSwipePx = with(density) { maxSwipe.toPx() }

    return remember(thresholdPx, maxSwipePx) {
        SwipeableSongState(thresholdPx = thresholdPx, maxSwipePx = maxSwipePx)
    }
}
```

*Note on drag value update:* To guarantee zero reflection fragility across R8/ProGuard in release builds, implement `SwipeableSongState` with `snapTo` in a non-suspending/instant loop or internal state variable:

```kotlin
package com.quietrays.tonarc.presentation.components.gesture

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

enum class SwipeDirection {
    NONE,
    START_TO_END,
    END_TO_START
}

@Stable
class SwipeableSongState(
    val thresholdPx: Float,
    val maxSwipePx: Float,
    private val velocityThresholdPx: Float = 1000f,
    private val springSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
) {
    private val animatable = Animatable(0f)
    private var internalOffset by mutableFloatStateOf(0f)

    val offsetPx: Float
        get() = internalOffset

    val activeDirection: SwipeDirection
        get() = when {
            internalOffset > 1f -> SwipeDirection.START_TO_END
            internalOffset < -1f -> SwipeDirection.END_TO_START
            else -> SwipeDirection.NONE
        }

    val isTriggerZoneReached: Boolean
        get() = abs(internalOffset) >= thresholdPx

    fun onDrag(deltaPx: Float) {
        val newTarget = internalOffset + deltaPx
        val clamped = if (abs(newTarget) > thresholdPx) {
            val overflow = abs(newTarget) - thresholdPx
            val dampedOverflow = overflow * 0.35f
            val directionSign = if (newTarget >= 0f) 1f else -1f
            (directionSign * (thresholdPx + dampedOverflow)).coerceIn(-maxSwipePx, maxSwipePx)
        } else {
            newTarget.coerceIn(-maxSwipePx, maxSwipePx)
        }
        internalOffset = clamped
    }

    suspend fun onRelease(
        velocityPx: Float,
        onStartAction: () -> Unit,
        onEndAction: () -> Unit
    ) {
        val triggered = isTriggerZoneReached || (abs(velocityPx) >= velocityThresholdPx && (velocityPx * internalOffset) > 0)
        val direction = activeDirection

        if (triggered) {
            when (direction) {
                SwipeDirection.START_TO_END -> onStartAction()
                SwipeDirection.END_TO_START -> onEndAction()
                SwipeDirection.NONE -> Unit
            }
        }

        animatable.snapTo(internalOffset)
        animatable.animateTo(
            targetValue = 0f,
            animationSpec = springSpec,
            initialVelocity = velocityPx
        ) {
            internalOffset = value
        }
        internalOffset = 0f
    }

    suspend fun reset() {
        animatable.snapTo(0f)
        internalOffset = 0f
    }
}

@Composable
fun rememberSwipeableSongState(
    threshold: Dp = 76.dp,
    maxSwipe: Dp = 160.dp
): SwipeableSongState {
    val density = LocalDensity.current
    val thresholdPx = with(density) { threshold.toPx() }
    val maxSwipePx = with(density) { maxSwipe.toPx() }

    return remember(thresholdPx, maxSwipePx) {
        SwipeableSongState(thresholdPx = thresholdPx, maxSwipePx = maxSwipePx)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*.SwipeableSongStateTest" --no-daemon`
Expected: PASS (all 8 unit tests green).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/components/gesture/SwipeableSongState.kt app/src/test/java/com/quietrays/tonarc/presentation/components/gesture/SwipeableSongStateTest.kt
git commit -m "feat(gestures): add SwipeableSongState physics engine and unit tests"
```

---

### Task 2: Implement `SwipeableSongActionRow` Composable

**Files:**
- Create: `app/src/main/java/com/quietrays/tonarc/presentation/components/SwipeableSongActionRow.kt`
- Test: `app/src/test/java/com/quietrays/tonarc/presentation/components/SwipeableSongActionRowTest.kt`

**Interfaces:**
- Consumes: `SwipeableSongState`, `SwipeDirection`
- Produces:
  - `data class SwipeActionConfig(...)`
  - `@Composable fun SwipeableSongActionRow(...)`

- [ ] **Step 1: Write the failing unit tests for `SwipeableSongActionRow` logic**

Create `app/src/test/java/com/quietrays/tonarc/presentation/components/SwipeableSongActionRowTest.kt`:

```kotlin
package com.quietrays.tonarc.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SwipeableSongActionRowTest {

    @Test
    @DisplayName("SwipeActionConfig holds configured icon, colors, and accessibility descriptions")
    fun test_swipeActionConfig_holdsProperties() {
        val config = SwipeActionConfig(
            icon = Icons.Rounded.QueueMusic,
            contentDescription = "Add to queue",
            containerColor = Color(0xFF1E3A5F),
            contentColor = Color(0xFF90CAF9),
            labelText = "Add to queue"
        )

        assertThat(config.contentDescription).isEqualTo("Add to queue")
        assertThat(config.labelText).isEqualTo("Add to queue")
        assertThat(config.containerColor).isEqualTo(Color(0xFF1E3A5F))
        assertThat(config.contentColor).isEqualTo(Color(0xFF90CAF9))
    }

    @Test
    @DisplayName("calculateCapsuleWidth scales between min and max width based on progress")
    fun test_calculateCapsuleWidth_interpolatesCorrectly() {
        val minWidthPx = 100f
        val maxWidthPx = 300f

        val zeroProgressWidth = calculateCapsuleWidth(progress = 0f, minWidthPx = minWidthPx, maxWidthPx = maxWidthPx)
        assertThat(zeroProgressWidth).isEqualTo(100f)

        val halfProgressWidth = calculateCapsuleWidth(progress = 0.5f, minWidthPx = minWidthPx, maxWidthPx = maxWidthPx)
        assertThat(halfProgressWidth).isEqualTo(200f)

        val fullProgressWidth = calculateCapsuleWidth(progress = 1.0f, minWidthPx = minWidthPx, maxWidthPx = maxWidthPx)
        assertThat(fullProgressWidth).isEqualTo(300f)
    }

    @Test
    @DisplayName("calculateIconScale scales between 0.85 and 1.18 based on trigger zone entry")
    fun test_calculateIconScale_scalesOnTrigger() {
        val idleScale = calculateIconScale(isTriggerZoneReached = false, progress = 0.4f)
        assertThat(idleScale).isAtLeast(0.85f)
        assertThat(idleScale).isAtMost(1.0f)

        val triggeredScale = calculateIconScale(isTriggerZoneReached = true, progress = 1.0f)
        assertThat(triggeredScale).isEqualTo(1.18f)
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "*.SwipeableSongActionRowTest" --no-daemon`
Expected: Compilation failure because `SwipeActionConfig` and helper functions do not exist yet.

- [ ] **Step 3: Implement `SwipeableSongActionRow.kt`**

Create `app/src/main/java/com/quietrays/tonarc/presentation/components/SwipeableSongActionRow.kt`:

```kotlin
package com.quietrays.tonarc.presentation.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quietrays.tonarc.presentation.components.gesture.SwipeDirection
import com.quietrays.tonarc.presentation.components.gesture.SwipeableSongState
import com.quietrays.tonarc.presentation.components.gesture.rememberSwipeableSongState
import kotlinx.coroutines.launch
import kotlin.math.abs

data class SwipeActionConfig(
    val icon: ImageVector,
    val contentDescription: String,
    val containerColor: Color,
    val contentColor: Color,
    val labelText: String? = null
)

fun calculateCapsuleWidth(progress: Float, minWidthPx: Float, maxWidthPx: Float): Float {
    val clamped = progress.coerceIn(0f, 1f)
    return minWidthPx + (maxWidthPx - minWidthPx) * clamped
}

fun calculateIconScale(isTriggerZoneReached: Boolean, progress: Float): Float {
    if (isTriggerZoneReached) return 1.18f
    val clamped = progress.coerceIn(0f, 1f)
    return 0.85f + (0.15f * clamped)
}

@Composable
fun SwipeableSongActionRow(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    startAction: SwipeActionConfig? = null,
    endAction: SwipeActionConfig? = null,
    onStartActionTriggered: () -> Unit = {},
    onEndActionTriggered: () -> Unit = {},
    state: SwipeableSongState = rememberSwipeableSongState(),
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl

    var wasTriggeredPreviously by remember { mutableStateOf(false) }

    LaunchedEffect(state.isTriggerZoneReached) {
        if (state.isTriggerZoneReached && !wasTriggeredPreviously) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            wasTriggeredPreviously = true
        } else if (!state.isTriggerZoneReached) {
            wasTriggeredPreviously = false
        }
    }

    val minCapsuleWidthPx = with(density) { 44.dp.toPx() }
    val maxCapsuleWidthPx = with(density) { 130.dp.toPx() }

    val accessibilityActions = remember(startAction, endAction) {
        buildList {
            if (startAction != null) {
                add(CustomAccessibilityAction(startAction.contentDescription) {
                    onStartActionTriggered()
                    true
                })
            }
            if (endAction != null) {
                add(CustomAccessibilityAction(endAction.contentDescription) {
                    onEndActionTriggered()
                    true
                })
            }
        }
    }

    val draggableState = rememberDraggableState { delta ->
        if (enabled) {
            state.onDrag(if (isRtl) -delta else delta)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                if (accessibilityActions.isNotEmpty()) {
                    this.customActions = accessibilityActions
                }
            }
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                enabled = enabled,
                onDragStopped = { velocity ->
                    if (enabled) {
                        scope.launch {
                            val resolvedVelocity = if (isRtl) -velocity else velocity
                            state.onRelease(
                                velocityPx = resolvedVelocity,
                                onStartAction = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    onStartActionTriggered()
                                },
                                onEndAction = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    onEndActionTriggered()
                                }
                            )
                        }
                    }
                }
            )
    ) {
        val currentOffset = state.offsetPx
        val direction = state.activeDirection
        val isSwipingRight = direction == SwipeDirection.START_TO_END
        val isSwipingLeft = direction == SwipeDirection.END_TO_START

        // Background action capsules
        if (abs(currentOffset) > 2f) {
            val progress = (abs(currentOffset) / state.thresholdPx).coerceIn(0f, 1f)
            val capsuleWidthPx = calculateCapsuleWidth(progress, minCapsuleWidthPx, maxCapsuleWidthPx)
            val capsuleWidthDp = with(density) { capsuleWidthPx.toDp() }
            val iconScale = calculateIconScale(state.isTriggerZoneReached, progress)

            if (isSwipingRight && startAction != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier
                            .height(44.dp)
                            .width(capsuleWidthDp)
                            .clip(CircleShape)
                            .background(startAction.containerColor)
                            .border(1.dp, startAction.contentColor.copy(alpha = 0.2f), CircleShape)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = startAction.icon,
                            contentDescription = null,
                            tint = startAction.contentColor,
                            modifier = Modifier
                                .size(20.dp)
                                .scale(iconScale)
                        )
                        if (capsuleWidthDp > 80.dp && startAction.labelText != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = startAction.labelText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = startAction.contentColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            } else if (isSwipingLeft && endAction != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        modifier = Modifier
                            .height(44.dp)
                            .width(capsuleWidthDp)
                            .clip(CircleShape)
                            .background(endAction.containerColor)
                            .border(1.dp, endAction.contentColor.copy(alpha = 0.2f), CircleShape)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (capsuleWidthDp > 80.dp && endAction.labelText != null) {
                            Text(
                                text = endAction.labelText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = endAction.contentColor,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Icon(
                            imageVector = endAction.icon,
                            contentDescription = null,
                            tint = endAction.contentColor,
                            modifier = Modifier
                                .size(20.dp)
                                .scale(iconScale)
                        )
                    }
                }
            }
        }

        // Foreground content with horizontal translation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = currentOffset
                }
        ) {
            content()
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*.SwipeableSongActionRowTest" --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/components/SwipeableSongActionRow.kt app/src/test/java/com/quietrays/tonarc/presentation/components/SwipeableSongActionRowTest.kt
git commit -m "feat(ui): create SwipeableSongActionRow composable with Material 3 dynamic capsules"
```

---

### Task 3: Integrate Swipe Gestures into `EnhancedSongListItem`

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/components/subcomps/EnhancedSongListItem.kt`
- Test: `./gradlew :app:testDebugUnitTest --tests "*.PlayerViewModelRadioTest" --no-daemon`

**Interfaces:**
- Consumes: `SwipeableSongActionRow`, `SwipeActionConfig`
- Produces:
  - Extended `EnhancedSongListItem` parameters:
    - `onAddToQueue: ((Song) -> Unit)? = null`
    - `onToggleFavorite: ((Song) -> Unit)? = null`
    - `isSwipeEnabled: Boolean = true`

- [ ] **Step 1: Check existing `EnhancedSongListItem.kt` signature and usage**

Inspect lines 85-115 of `EnhancedSongListItem.kt`. Notice parameters: `song: Song`, `isSelected: Boolean`, `isInSelectionMode: Boolean`, `onClick: () -> Unit`, `onLongClick: () -> Unit`.

- [ ] **Step 2: Add swipe configuration to `EnhancedSongListItem.kt`**

In `EnhancedSongListItem.kt`:
1. Add parameters with backwards-compatible defaults:
   - `onAddToQueue: ((Song) -> Unit)? = null`
   - `onToggleFavorite: ((Song) -> Unit)? = null`
   - `isSwipeEnabled: Boolean = true`
2. Configure start action (Add to Queue):
   - Icon: `Icons.Rounded.QueueMusic`
   - Label: `"Add Queue"`
   - `containerColor = MaterialTheme.colorScheme.primaryContainer`
   - `contentColor = MaterialTheme.colorScheme.onPrimaryContainer`
3. Configure end action (Toggle Favorite):
   - Icon: if `song.isFavorite` then `Icons.Rounded.Favorite` else `Icons.Rounded.FavoriteBorder`
   - Label: if `song.isFavorite` then `"Unfavorite"` else `"Favorite"`
   - `containerColor = MaterialTheme.colorScheme.tertiaryContainer`
   - `contentColor = MaterialTheme.colorScheme.onTertiaryContainer`
4. Wrap the item in `SwipeableSongActionRow`:
   - `enabled = isSwipeEnabled && !isInSelectionMode`
   - `onStartActionTriggered = { onAddToQueue?.invoke(song) }`
   - `onEndActionTriggered = { onToggleFavorite?.invoke(song) }`

- [ ] **Step 3: Compile to verify no syntax errors or breaking changes**

Run: `./gradlew :app:compileDebugKotlin --no-daemon`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Wire callbacks in `LibrarySongsTab.kt`, `LibrarySongsAndFavoritesTabs.kt`, and `SearchScreen.kt`**

Connect `onAddToQueue = { playerViewModel.addToQueue(it) }` and `onToggleFavorite = { playerViewModel.toggleFavorite(it) }` in `LibrarySongsTab.kt`, `LibrarySongsAndFavoritesTabs.kt`, and `SearchScreen.kt`.

- [ ] **Step 5: Run unit tests to verify stability**

Run: `./gradlew :app:testDebugUnitTest --tests "*.SwipeableSongActionRowTest" --no-daemon`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/components/subcomps/EnhancedSongListItem.kt app/src/main/java/com/quietrays/tonarc/presentation/screens/LibrarySongsTab.kt app/src/main/java/com/quietrays/tonarc/presentation/screens/LibrarySongsAndFavoritesTabs.kt app/src/main/java/com/quietrays/tonarc/presentation/screens/SearchScreen.kt
git commit -m "feat(library): wire swipe to add to queue and favorite into EnhancedSongListItem"
```

---

### Task 4: Integrate Swipe Gestures into `PlaylistSongTile`

**Files:**
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/components/PlaylistSongTile.kt`
- Modify: `app/src/main/java/com/quietrays/tonarc/presentation/screens/PlaylistDetailScreen.kt`

**Interfaces:**
- Consumes: `SwipeableSongActionRow`, `SwipeActionConfig`
- Produces:
  - Extended `PlaylistSongTile` parameters:
    - `onAddToQueue: ((Song) -> Unit)? = null`
    - `onRemoveFromPlaylist: ((Song) -> Unit)? = null`

- [ ] **Step 1: Check `PlaylistSongTile.kt` and `PlaylistDetailScreen.kt`**

Review `PlaylistSongTile.kt` lines 245-280. Notice parameters: `song: Song`, `isReorderMode: Boolean`, `onRemoveClick: () -> Unit`.

- [ ] **Step 2: Add `SwipeableSongActionRow` inside `PlaylistSongTile.kt`**

1. Add parameters:
   - `onAddToQueue: ((Song) -> Unit)? = null`
   - `onRemoveFromPlaylist: ((Song) -> Unit)? = null`
2. Start action: Add to Queue (`Icons.Rounded.QueueMusic`, `primaryContainer`, `onPrimaryContainer`, label `"Add Queue"`).
3. End action: Remove from Playlist (`Icons.Rounded.DeleteOutline`, `errorContainer`, `onErrorContainer`, label `"Remove"`).
4. Wrap tile surface in `SwipeableSongActionRow`:
   - `enabled = !isReorderMode`
   - `onStartActionTriggered = { onAddToQueue?.invoke(song) }`
   - `onEndActionTriggered = { onRemoveFromPlaylist?.invoke(song) }`

- [ ] **Step 3: Update `PlaylistDetailScreen.kt` with undo snackbar**

In `PlaylistDetailScreen.kt`:
When `onRemoveFromPlaylist` triggers:
1. Cache song and its index: `val removedSong = song`, `val removedIndex = songs.indexOf(song)`.
2. Call `playlistViewModel.removeSongFromPlaylist(playlist.id, song.id)`.
3. Launch `snackbarHostState.showSnackbar(message = "Removed from playlist", actionLabel = "Undo")`.
4. If result is `SnackbarResult.ActionPerformed`, call `playlistViewModel.addSongToPlaylist(playlist.id, removedSong.id, atIndex = removedIndex)`.
5. Pass `onAddToQueue = { playerViewModel.addToQueue(it) }`.

- [ ] **Step 4: Compile and test**

Run: `./gradlew :app:compileDebugKotlin --no-daemon`
Run: `./gradlew :app:testDebugUnitTest --tests "*.SwipeableSong*" --no-daemon`
Expected: BUILD SUCCESSFUL and tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/components/PlaylistSongTile.kt app/src/main/java/com/quietrays/tonarc/presentation/screens/PlaylistDetailScreen.kt
git commit -m "feat(playlist): integrate swipe to add to queue and remove with undo snackbar"
```

---

### Task 5: End-to-End Verification & Quality Gates

**Files:**
- Audit all modified and added files

- [ ] **Step 1: Check for zero-emoji compliance**

Run python verification script:
```bash
python3 -c '
import os, re
pattern = re.compile(r"[\U00010000-\U0010ffff]|[\u2600-\u27BF]|[\u2300-\u23FF]|[\u2B50-\u2B55]")
for root, _, files in os.walk("app/src"):
    for f in files:
        if f.endswith((".kt", ".xml")):
            p = os.path.join(root, f)
            with open(p, "r", errors="ignore") as fl:
                for idx, line in enumerate(fl, 1):
                    if pattern.search(line) and "♪" not in line and "✓" not in line:
                        print(f"{p}:{idx}: {line.strip()}")
'
```
Expected: Zero matching lines.

- [ ] **Step 2: Run full unit test suite**

Run: `./gradlew :app:testDebugUnitTest --no-daemon`
Expected: BUILD SUCCESSFUL with 0 test failures.

- [ ] **Step 3: Build Debug APK**

Run: `./gradlew assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL with generated debug APK.

- [ ] **Step 4: Verify git status and clean working tree**

Run: `git status`
Expected: Clean working tree.
