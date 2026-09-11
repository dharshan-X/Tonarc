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
    private var rawOffset = 0f

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
        rawOffset += deltaPx
        val absRaw = abs(rawOffset)
        internalOffset = if (absRaw > thresholdPx) {
            val overflow = absRaw - thresholdPx
            val dampedOverflow = overflow * 0.35f
            val directionSign = if (rawOffset >= 0f) 1f else -1f
            (directionSign * (thresholdPx + dampedOverflow)).coerceIn(-maxSwipePx, maxSwipePx)
        } else {
            rawOffset.coerceIn(-maxSwipePx, maxSwipePx)
        }
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

        rawOffset = 0f
        if (kotlin.coroutines.coroutineContext[androidx.compose.runtime.MonotonicFrameClock] != null) {
            animatable.snapTo(internalOffset)
            animatable.animateTo(
                targetValue = 0f,
                animationSpec = springSpec,
                initialVelocity = velocityPx
            ) {
                internalOffset = value
            }
        } else {
            animatable.snapTo(0f)
        }
        internalOffset = 0f
    }

    suspend fun reset() {
        rawOffset = 0f
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
