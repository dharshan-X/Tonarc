package com.quietrays.tonarc.presentation.viewmodel

import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class AbRepeatStateHolderTest {

    private var fakeElapsedRealtime: Long = 1000L
    private val stateHolder = AbRepeatStateHolder(elapsedRealtimeProvider = { fakeElapsedRealtime })
    private val mockPlayer: Player = mockk(relaxed = true)

    @Test
    @DisplayName("initial state is empty and loop is inactive")
    fun test_initialState() {
        val state = stateHolder.abRepeatState.value
        assertThat(state.pointA).isNull()
        assertThat(state.pointB).isNull()
        assertThat(state.isEnabled).isFalse()
        assertThat(stateHolder.isLoopActive()).isFalse()
    }

    @Test
    @DisplayName("setPointA sets Point A and keeps loop disabled until Point B is marked")
    fun test_setPointA_withoutB() {
        stateHolder.setPointA(5000L)
        val state = stateHolder.abRepeatState.value
        assertThat(state.pointA).isEqualTo(5000L)
        assertThat(state.pointB).isNull()
        assertThat(state.isEnabled).isFalse()
        assertThat(stateHolder.isLoopActive()).isFalse()
    }

    @Test
    @DisplayName("setPointB without Point A defaults Point A to 0 and enables loop")
    fun test_setPointB_withoutA() {
        stateHolder.setPointB(8000L)
        val state = stateHolder.abRepeatState.value
        assertThat(state.pointA).isEqualTo(0L)
        assertThat(state.pointB).isEqualTo(8000L)
        assertThat(state.isEnabled).isTrue()
        assertThat(stateHolder.isLoopActive()).isTrue()
    }

    @Test
    @DisplayName("setPointA and setPointB establishes active valid loop")
    fun test_setPointA_thenB() {
        stateHolder.setPointA(4000L)
        stateHolder.setPointB(12000L)
        val state = stateHolder.abRepeatState.value
        assertThat(state.pointA).isEqualTo(4000L)
        assertThat(state.pointB).isEqualTo(12000L)
        assertThat(state.isEnabled).isTrue()
        assertThat(stateHolder.isLoopActive()).isTrue()
    }

    @Test
    @DisplayName("setPointB less than Point A enforces minimum interval")
    fun test_setPointB_lessThanA_clampsInterval() {
        stateHolder.setPointA(10000L)
        stateHolder.setPointB(9000L)
        val state = stateHolder.abRepeatState.value
        assertThat(state.pointA).isEqualTo(10000L)
        assertThat(state.pointB).isEqualTo(10000L + AbRepeatStateHolder.MIN_LOOP_INTERVAL_MS)
        assertThat(state.isEnabled).isTrue()
    }

    @Test
    @DisplayName("setPointA at or past Point B resets Point B and disables loop")
    fun test_setPointA_pastB_resetsB() {
        stateHolder.setPointA(5000L)
        stateHolder.setPointB(10000L)
        assertThat(stateHolder.isLoopActive()).isTrue()

        // Move Point A past Point B
        stateHolder.setPointA(12000L)
        val state = stateHolder.abRepeatState.value
        assertThat(state.pointA).isEqualTo(12000L)
        assertThat(state.pointB).isNull()
        assertThat(state.isEnabled).isFalse()
        assertThat(stateHolder.isLoopActive()).isFalse()
    }

    @Test
    @DisplayName("adjustPointA respects 0L lower bound and Point B upper bound")
    fun test_adjustPointA() {
        stateHolder.setPointA(5000L)
        stateHolder.setPointB(10000L)

        // Decrement by 1000ms
        stateHolder.adjustPointA(-1000L, 60000L)
        assertThat(stateHolder.abRepeatState.value.pointA).isEqualTo(4000L)

        // Increment by 10000ms (clamped to pointB - MIN_LOOP_INTERVAL_MS = 9500L)
        stateHolder.adjustPointA(10000L, 60000L)
        assertThat(stateHolder.abRepeatState.value.pointA).isEqualTo(9500L)

        // Decrement past 0 (clamped to 0L)
        stateHolder.adjustPointA(-20000L, 60000L)
        assertThat(stateHolder.abRepeatState.value.pointA).isEqualTo(0L)
    }

    @Test
    @DisplayName("adjustPointB respects Point A lower bound and total duration upper bound")
    fun test_adjustPointB() {
        stateHolder.setPointA(5000L)
        stateHolder.setPointB(10000L)

        // Increment by 1000ms
        stateHolder.adjustPointB(1000L, 60000L)
        assertThat(stateHolder.abRepeatState.value.pointB).isEqualTo(11000L)

        // Decrement past pointA + MIN_LOOP_INTERVAL_MS (clamped to 5500L)
        stateHolder.adjustPointB(-20000L, 60000L)
        assertThat(stateHolder.abRepeatState.value.pointB).isEqualTo(5500L)

        // Increment past totalDuration (clamped to 60000L)
        stateHolder.adjustPointB(100000L, 60000L)
        assertThat(stateHolder.abRepeatState.value.pointB).isEqualTo(60000L)
    }

    @Test
    @DisplayName("toggleLoop toggles isEnabled when valid loop exists")
    fun test_toggleLoop() {
        // When invalid, toggle returns false and stays disabled
        assertThat(stateHolder.toggleLoop()).isFalse()
        assertThat(stateHolder.abRepeatState.value.isEnabled).isFalse()

        stateHolder.setPointA(3000L)
        stateHolder.setPointB(8000L)
        assertThat(stateHolder.abRepeatState.value.isEnabled).isTrue()

        // Toggle off
        val offResult = stateHolder.toggleLoop()
        assertThat(offResult).isFalse()
        assertThat(stateHolder.abRepeatState.value.isEnabled).isFalse()
        assertThat(stateHolder.isLoopActive()).isFalse()

        // Toggle back on
        val onResult = stateHolder.toggleLoop()
        assertThat(onResult).isTrue()
        assertThat(stateHolder.abRepeatState.value.isEnabled).isTrue()
        assertThat(stateHolder.isLoopActive()).isTrue()
    }

    @Test
    @DisplayName("clear resets all state and loop becomes inactive")
    fun test_clear() {
        stateHolder.setPointA(2000L)
        stateHolder.setPointB(6000L)
        assertThat(stateHolder.isLoopActive()).isTrue()

        stateHolder.clear()
        val state = stateHolder.abRepeatState.value
        assertThat(state.pointA).isNull()
        assertThat(state.pointB).isNull()
        assertThat(state.isEnabled).isFalse()
        assertThat(stateHolder.isLoopActive()).isFalse()
    }

    @Test
    @DisplayName("onSongChanged resets loop when mediaId changes")
    fun test_onSongChanged() {
        stateHolder.onSongChanged("song_1")
        stateHolder.setPointA(1000L)
        stateHolder.setPointB(4000L)
        assertThat(stateHolder.isLoopActive()).isTrue()

        // Same songId should not reset
        stateHolder.onSongChanged("song_1")
        assertThat(stateHolder.isLoopActive()).isTrue()

        // Different songId should reset
        stateHolder.onSongChanged("song_2")
        assertThat(stateHolder.isLoopActive()).isFalse()
        assertThat(stateHolder.abRepeatState.value.pointA).isNull()
    }

    @Test
    @DisplayName("checkAndLoop triggers player seekTo Point A when position reaches Point B")
    fun test_checkAndLoop_seeksWhenReached() {
        clearMocks(mockPlayer)
        stateHolder.setPointA(2000L)
        stateHolder.setPointB(8000L)

        // Position before B -> no seek
        val beforeResult = stateHolder.checkAndLoop(mockPlayer, 7500L)
        assertThat(beforeResult).isFalse()
        verify(exactly = 0) { mockPlayer.seekTo(2000L) }

        // Position at B -> seeks to A
        val atResult = stateHolder.checkAndLoop(mockPlayer, 8000L)
        assertThat(atResult).isTrue()
        verify(exactly = 1) { mockPlayer.seekTo(2000L) }

        // Debounce: immediately calling again should be ignored
        val debounceResult = stateHolder.checkAndLoop(mockPlayer, 8050L)
        assertThat(debounceResult).isFalse()
        verify(exactly = 1) { mockPlayer.seekTo(2000L) }

        // After debounce interval expires -> can trigger again
        fakeElapsedRealtime += 500L
        val afterDebounceResult = stateHolder.checkAndLoop(mockPlayer, 8100L)
        assertThat(afterDebounceResult).isTrue()
        verify(exactly = 2) { mockPlayer.seekTo(2000L) }
    }

    @Test
    @DisplayName("checkAndLoop does nothing when loop is disabled")
    fun test_checkAndLoop_disabled() {
        clearMocks(mockPlayer)
        stateHolder.setPointA(2000L)
        stateHolder.setPointB(8000L)
        stateHolder.setLoopEnabled(false)

        val result = stateHolder.checkAndLoop(mockPlayer, 9000L)
        assertThat(result).isFalse()
        verify(exactly = 0) { mockPlayer.seekTo(any()) }
    }
}
