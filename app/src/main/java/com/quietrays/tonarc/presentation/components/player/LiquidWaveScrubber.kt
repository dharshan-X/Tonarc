package com.quietrays.tonarc.presentation.components.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.min
import kotlin.math.sin

/**
 * Organic Liquid Wave Scrubber:
 * 1. Dual-Layer Harmonic Spline Waveform (Primary crest + Harmonic echo wave at 40% alpha, 1.5x frequency).
 * 2. Elastic vertical touch expansion from 36.dp to 52.dp.
 * 3. Local Gaussian pluck amplitude bump under user touch point.
 * 4. Floating time micro-bubble displaying current position and scrub delta (+0:15 / -0:30).
 * 5. Tactile tick haptics when crossing second boundaries.
 */
@Composable
fun LiquidWaveScrubber(
    currentPositionMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    waveColor: Color,
    trackInactiveColor: Color,
    bubbleContainerColor: Color,
    bubbleContentColor: Color,
    bubbleBorderColor: Color,
    thumbColor: Color = Color.White,
    thumbBorderColor: Color = Color.Transparent,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var touchXPx by remember { mutableFloatStateOf(0f) }
    var lastHapticSecond by remember { mutableLongStateOf(-1L) }
    val primaryPath = remember { Path() }
    val echoPath = remember { Path() }

    val safeDuration = totalDurationMs.coerceAtLeast(1L)
    val realFraction = (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val displayFraction = if (isScrubbing) scrubFraction else realFraction
    val scrubbedPositionMs = (displayFraction * safeDuration).toLong()

    // Elastic vertical expansion spring (36.dp resting -> 52.dp scrubbing)
    val scrubberHeight by animateDpAsState(
        targetValue = if (isScrubbing) 52.dp else 36.dp,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "scrubberHeight"
    )

    // Breathing amplitude animation when playing (8.dp to 12.dp)
    val infiniteTransition = rememberInfiniteTransition(label = "liquidWaveOscillation")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 2800 else 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "liquidWavePhase"
    )

    val breathingAmpMultiplier by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveBreathingAmp"
    )

    val primaryAmplitudePx = with(density) {
        val baseAmp = if (isPlaying) 10.dp.toPx() else 4.dp.toPx()
        baseAmp * breathingAmpMultiplier
    }
    val echoAmplitudePx = primaryAmplitudePx * 0.55f
    val touchSigmaPx = with(density) { 44.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(scrubberHeight)
    ) {
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val bubbleWidthDp = 92.dp
        val bubbleWidthPx = with(density) { bubbleWidthDp.toPx() }
        val trackMarginPx = with(density) { 4.dp.toPx() }
        val trackStartPx = trackMarginPx
        val trackEndPx = containerWidthPx - trackMarginPx

        // Floating Time Micro-Bubble
        if (isScrubbing) {
            val bubbleXPx = calculateFloatingBubbleX(
                touchXPx = touchXPx,
                bubbleWidthPx = bubbleWidthPx,
                trackStartPx = trackStartPx,
                trackEndPx = trackEndPx
            )
            val scrubDeltaText = formatScrubDelta(
                currentPositionMs = currentPositionMs,
                targetPositionMs = scrubbedPositionMs
            )

            Box(
                modifier = Modifier
                    .offset { IntOffset(x = bubbleXPx.toInt(), y = (-32).dp.roundToPx()) }
                    .width(bubbleWidthDp)
                    .height(26.dp)
                    .clip(CircleShape)
                    .background(bubbleContainerColor)
                    .border(1.dp, bubbleBorderColor, CircleShape)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDuration(scrubbedPositionMs),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = bubbleContentColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = scrubDeltaText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 9.sp
                        ),
                        color = bubbleContentColor.copy(alpha = 0.75f)
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(scrubberHeight)
                .pointerInput(safeDuration) {
                    detectTapGestures { offset ->
                        val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSeek((frac * safeDuration).toLong())
                    }
                }
                .pointerInput(safeDuration) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isScrubbing = true
                            touchXPx = offset.x
                            scrubFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            lastHapticSecond = (scrubFraction * safeDuration / 1000L).toLong()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            touchXPx = change.position.x
                            val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            scrubFraction = frac

                            val currentSec = (frac * safeDuration / 1000L).toLong()
                            if (shouldTriggerSecondHapticTick(lastHapticSecond, currentSec)) {
                                lastHapticSecond = currentSec
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = {
                            isScrubbing = false
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSeek((scrubFraction * safeDuration).toLong())
                        },
                        onDragCancel = {
                            isScrubbing = false
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height
            val centerY = h / 2f

            val trackWidth = (trackEndPx - trackStartPx).coerceAtLeast(1f)
            val thumbX = trackStartPx + displayFraction * trackWidth
            val thumbWPx = if (isScrubbing) 8.dp.toPx() else 6.dp.toPx()
            val thumbHPx = if (isScrubbing) 26.dp.toPx() else 20.dp.toPx()
            val gapPx = 6.dp.toPx()

            // 1. Inactive Track (Unplayed)
            val inactStart = thumbX + (thumbWPx / 2f) + gapPx
            if (inactStart < trackEndPx) {
                drawLine(
                    color = trackInactiveColor,
                    start = Offset(inactStart, centerY),
                    end = Offset(trackEndPx, centerY),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 2. Active Track (Played)
            val actEnd = thumbX - (thumbWPx / 2f) - gapPx
            if (actEnd > trackStartPx) {
                if (isPlaying || isScrubbing) {
                    val primaryWavelengthPx = 32.dp.toPx()
                    val echoWavelengthPx = primaryWavelengthPx / 1.5f
                    val echoPhaseOffset = (Math.PI / 4.0).toFloat()

                    primaryPath.reset()
                    echoPath.reset()
                    var firstPoint = true
                    var x = trackStartPx
                    val stepPx = 2f

                    while (x <= actEnd) {
                        val distFromEnds = min(x - trackStartPx, actEnd - x)
                        val edgeEnvelope = (distFromEnds / 8.dp.toPx()).coerceIn(0f, 1f)

                        val pluckMultiplier = if (isScrubbing) {
                            calculatePluckGaussianMultiplier(x, touchXPx, touchSigmaPx, maxBoost = 0.55f)
                        } else {
                            1.0f
                        }

                        val primaryAngle = ((x - trackStartPx) / primaryWavelengthPx) * 2 * Math.PI - phase
                        val primaryY = centerY + (sin(primaryAngle).toFloat() * primaryAmplitudePx * edgeEnvelope * pluckMultiplier)

                        val echoAngle = ((x - trackStartPx) / echoWavelengthPx) * 2 * Math.PI - phase + echoPhaseOffset
                        val echoY = centerY + (sin(echoAngle).toFloat() * echoAmplitudePx * edgeEnvelope * pluckMultiplier)

                        if (firstPoint) {
                            primaryPath.moveTo(x, primaryY)
                            echoPath.moveTo(x, echoY)
                            firstPoint = false
                        } else {
                            primaryPath.lineTo(x, primaryY)
                            echoPath.lineTo(x, echoY)
                        }
                        x += stepPx
                    }

                    // Draw Harmonic Echo Wave first (background layer)
                    drawPath(
                        path = echoPath,
                        color = waveColor.copy(alpha = 0.40f),
                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw Primary Crest Wave on top (foreground layer)
                    drawPath(
                        path = primaryPath,
                        color = waveColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                } else {
                    // Calm Paused Baseline: straight sleek line
                    drawLine(
                        color = waveColor,
                        start = Offset(trackStartPx, centerY),
                        end = Offset(actEnd, centerY),
                        strokeWidth = 3.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // 3. Capsule Thumb
            val thumbLeft = thumbX - thumbWPx / 2f
            val thumbTop = centerY - thumbHPx / 2f
            val radiusPx = 3.dp.toPx()

            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(thumbLeft, thumbTop),
                size = Size(thumbWPx, thumbHPx),
                cornerRadius = CornerRadius(radiusPx, radiusPx)
            )

            if (thumbBorderColor != Color.Transparent) {
                drawRoundRect(
                    color = thumbBorderColor,
                    topLeft = Offset(thumbLeft, thumbTop),
                    size = Size(thumbWPx, thumbHPx),
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(Locale.US, minutes, seconds)
}
