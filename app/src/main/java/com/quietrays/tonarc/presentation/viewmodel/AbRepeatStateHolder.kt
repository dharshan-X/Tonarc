package com.quietrays.tonarc.presentation.viewmodel

import android.os.SystemClock
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AbRepeatState(
    val pointA: Long? = null,
    val pointB: Long? = null,
    val isEnabled: Boolean = false
) {
    val isValidLoop: Boolean
        get() = pointA != null && pointB != null && pointB > pointA

    val isLoopActive: Boolean
        get() = isEnabled && isValidLoop
}

@Singleton
class AbRepeatStateHolder(
    private val elapsedRealtimeProvider: () -> Long
) {
    @Inject
    constructor() : this({ SystemClock.elapsedRealtime() })

    private val _abRepeatState = MutableStateFlow(AbRepeatState())
    val abRepeatState: StateFlow<AbRepeatState> = _abRepeatState.asStateFlow()

    private var lastLoopSeekRealtimeMs: Long = 0L
    private var lastTrackMediaId: String? = null

    companion object {
        const val MIN_LOOP_INTERVAL_MS = 500L
        const val DEFAULT_FINE_TUNE_DELTA_MS = 1000L
        const val LOOP_SEEK_DEBOUNCE_MS = 300L
    }

    fun isLoopActive(): Boolean {
        val state = _abRepeatState.value
        return state.isEnabled && state.isValidLoop
    }

    fun setPointA(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        val current = _abRepeatState.value
        val currentB = current.pointB

        if (currentB != null) {
            if (safePos >= currentB) {
                // Point A must precede Point B. Reset Point B if Point A moves past it.
                _abRepeatState.value = current.copy(
                    pointA = safePos,
                    pointB = null,
                    isEnabled = false
                )
            } else {
                _abRepeatState.value = current.copy(
                    pointA = safePos,
                    isEnabled = true
                )
            }
        } else {
            _abRepeatState.value = current.copy(
                pointA = safePos,
                isEnabled = false
            )
        }
    }

    fun setPointB(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        val current = _abRepeatState.value
        val currentA = current.pointA

        if (currentA == null) {
            // If Point A is not yet marked, default Point A to start of track.
            val validB = safePos.coerceAtLeast(MIN_LOOP_INTERVAL_MS)
            _abRepeatState.value = current.copy(
                pointA = 0L,
                pointB = validB,
                isEnabled = true
            )
        } else {
            val validB = if (safePos <= currentA) {
                currentA + MIN_LOOP_INTERVAL_MS
            } else {
                safePos
            }
            _abRepeatState.value = current.copy(
                pointB = validB,
                isEnabled = true
            )
        }
    }

    fun adjustPointA(deltaMs: Long, totalDuration: Long) {
        val current = _abRepeatState.value
        val currentA = current.pointA ?: 0L
        val currentB = current.pointB ?: totalDuration.takeIf { it > 0L } ?: (currentA + MIN_LOOP_INTERVAL_MS * 2)
        val maxAllowed = (currentB - MIN_LOOP_INTERVAL_MS).coerceAtLeast(0L)
        val newA = (currentA + deltaMs).coerceIn(0L, maxAllowed)

        _abRepeatState.value = current.copy(
            pointA = newA
        )
    }

    fun adjustPointB(deltaMs: Long, totalDuration: Long) {
        val current = _abRepeatState.value
        val currentA = current.pointA ?: 0L
        val currentB = current.pointB ?: (currentA + MIN_LOOP_INTERVAL_MS)
        val minAllowed = currentA + MIN_LOOP_INTERVAL_MS
        val maxAllowed = if (totalDuration > 0L) totalDuration.coerceAtLeast(minAllowed) else Long.MAX_VALUE
        val newB = (currentB + deltaMs).coerceIn(minAllowed, maxAllowed)

        _abRepeatState.value = current.copy(
            pointB = newB
        )
    }

    fun toggleLoop(): Boolean {
        val current = _abRepeatState.value
        if (current.isValidLoop) {
            val newEnabled = !current.isEnabled
            _abRepeatState.value = current.copy(isEnabled = newEnabled)
            return newEnabled
        }
        return false
    }

    fun setLoopEnabled(enabled: Boolean) {
        val current = _abRepeatState.value
        if (current.isValidLoop) {
            _abRepeatState.value = current.copy(isEnabled = enabled)
        }
    }

    fun clear() {
        _abRepeatState.value = AbRepeatState()
        lastLoopSeekRealtimeMs = 0L
    }

    fun onSongChanged(newMediaId: String?) {
        if (newMediaId != null && newMediaId != lastTrackMediaId) {
            lastTrackMediaId = newMediaId
            clear()
        }
    }

    fun checkAndLoop(player: Player, currentPositionMs: Long): Boolean {
        val state = _abRepeatState.value
        if (!state.isEnabled || !state.isValidLoop) return false
        val targetA = state.pointA ?: return false
        val targetB = state.pointB ?: return false

        val now = elapsedRealtimeProvider()
        if (now - lastLoopSeekRealtimeMs < LOOP_SEEK_DEBOUNCE_MS) return false

        if (currentPositionMs >= targetB) {
            lastLoopSeekRealtimeMs = now
            player.seekTo(targetA)
            return true
        }
        return false
    }
}
