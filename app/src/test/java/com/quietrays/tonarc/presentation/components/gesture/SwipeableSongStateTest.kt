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

    @Test
    @DisplayName("release with high negative fling velocity triggers end action even below distance threshold")
    fun test_flingNegativeVelocity_triggersEndAction() = runTest {
        val state = SwipeableSongState(thresholdPx = 200f, maxSwipePx = 400f)
        state.onDrag(-80f)

        var endActionFired = false

        state.onRelease(
            velocityPx = -1500f,
            onStartAction = {},
            onEndAction = { endActionFired = true }
        )

        assertThat(endActionFired).isTrue()
        assertThat(state.offsetPx).isEqualTo(0f)
    }

    @Test
    @DisplayName("reset snaps offset to zero and resets direction to NONE")
    fun test_reset_snapsToZero() = runTest {
        val state = SwipeableSongState(thresholdPx = 200f, maxSwipePx = 400f)
        state.onDrag(150f)
        assertThat(state.offsetPx).isEqualTo(150f)
        state.reset()
        assertThat(state.offsetPx).isEqualTo(0f)
        assertThat(state.activeDirection).isEqualTo(SwipeDirection.NONE)
    }

    @Test
    @DisplayName("continuous multi-frame drag past threshold increases offset monotonically")
    fun test_continuousMultiFrameDrag_increasesMonotonically() {
        val state = SwipeableSongState(thresholdPx = 200f, maxSwipePx = 400f)
        state.onDrag(200f)
        var previousOffset = state.offsetPx
        for (i in 1..10) {
            state.onDrag(10f)
            val currentOffset = state.offsetPx
            assertThat(currentOffset).isGreaterThan(previousOffset)
            previousOffset = currentOffset
        }
        // Total raw drag was 200f + 100f = 300f. Damped offset should be 200f + 35f = 235f.
        assertThat(state.offsetPx).isEqualTo(235f)
    }
}
