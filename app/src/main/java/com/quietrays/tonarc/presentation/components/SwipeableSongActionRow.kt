package com.quietrays.tonarc.presentation.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.rememberUpdatedState
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

    val currentOnStartActionTriggered by rememberUpdatedState(onStartActionTriggered)
    val currentOnEndActionTriggered by rememberUpdatedState(onEndActionTriggered)

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
                    currentOnStartActionTriggered()
                    true
                })
            }
            if (endAction != null) {
                add(CustomAccessibilityAction(endAction.contentDescription) {
                    currentOnEndActionTriggered()
                    true
                })
            }
        }
    }

    val isDragEnabled = enabled && (startAction != null || endAction != null)

    val draggableState = rememberDraggableState { delta ->
        if (isDragEnabled) {
            val logicalDelta = if (isRtl) -delta else delta
            if (logicalDelta > 0 && startAction == null && state.offsetPx >= 0f) {
                return@rememberDraggableState
            }
            if (logicalDelta < 0 && endAction == null && state.offsetPx <= 0f) {
                return@rememberDraggableState
            }
            val effectiveDelta = when {
                startAction == null && state.offsetPx < 0f && (state.offsetPx + logicalDelta) > 0f -> -state.offsetPx
                endAction == null && state.offsetPx > 0f && (state.offsetPx + logicalDelta) < 0f -> -state.offsetPx
                else -> logicalDelta
            }
            if (effectiveDelta != 0f) {
                state.onDrag(effectiveDelta)
            }
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
                enabled = isDragEnabled,
                onDragStopped = { velocity ->
                    if (isDragEnabled) {
                        scope.launch {
                            val resolvedVelocity = if (isRtl) -velocity else velocity
                            state.onRelease(
                                velocityPx = resolvedVelocity,
                                onStartAction = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    currentOnStartActionTriggered()
                                },
                                onEndAction = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    currentOnEndActionTriggered()
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
                    translationX = if (isRtl) -currentOffset else currentOffset
                }
        ) {
            content()
        }
    }
}
