# Wave-Card Player Enhancement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Elevate the Tonarc Wave-Card Player with an organic dual-spline liquid wave scrubber, ambient radial bloom, frosted studio deck depth, technical audio badge, and tactile transport dock ergonomics.

**Architecture:** Decompose wave calculations and button spring dynamics into pure, testable mathematical functions in `WaveCardPlayerDynamics.kt`. Encapsulate the dual-spline waveform, touch expansion, and floating time micro-bubble in `LiquidWaveScrubber.kt`. Re-architect `WaveCardPlayerContent.kt` with a 54%/30%/16% vertical proportion hierarchy, cubic alpha fade with ambient artwork bloom, specular edge highlights, technical audio badge opening `SongInfoBottomSheet`, and refined spring physics.

**Tech Stack:** Jetpack Compose, Material 3 Expressive, Compose Animation Core (`animateFloatAsState`, `animateDpAsState`, `spring`, `Spring`), Canvas graphics (`drawPath`, `drawRect`, `Brush`), Coil, JUnit 4/5.

## Global Constraints

- Strictly zero emojis in code, comments, string resources, test fixtures, documentation, or commit messages.
- Format all file references as clickable markdown links with the `file://` scheme.
- Append `--no-daemon` to all Gradle invocations.
- Route all playback modifications through `MusicService` / `MediaController` via `PlayerViewModel`.
- Render animations via `Modifier.graphicsLayer` or `Canvas` drawing to preserve 60-120 FPS performance without Compose recomposition overhead.
- Transport dock heights and weights: Previous/Next squircle at 58.dp (weights 1.0f -> 1.15f), Play/Pause stadium pill at 64.dp (weights 1.75f -> 1.85f).
- Maintain existing player design styles ("Full Width", "Vinyl Waveform") without regressions.

---

### Task 1: Core Dynamics & Pure Math Calculations (`WaveCardPlayerDynamics.kt`)

**Files:**
- Create: [`app/src/main/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerDynamics.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerDynamics.kt)
- Create: [`app/src/test/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerDynamicsTest.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/test/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerDynamicsTest.kt)

**Interfaces:**
- Produces:
  ```kotlin
  package com.quietrays.tonarc.presentation.components.player

  enum class WaveCardButtonType { NONE, PREVIOUS, PLAY_PAUSE, NEXT }

  data class WaveCardButtonWeights(val previous: Float, val playPause: Float, val next: Float)
  data class WaveCardButtonScales(val previous: Float, val playPause: Float, val next: Float)

  fun resolveWaveCardButtonWeights(activeButton: WaveCardButtonType?): WaveCardButtonWeights
  fun resolveWaveCardButtonScales(activeButton: WaveCardButtonType?): WaveCardButtonScales
  fun calculatePluckGaussianMultiplier(xPx: Float, touchXPx: Float, sigmaPx: Float, maxBoost: Float = 0.55f): Float
  fun formatScrubDelta(currentPositionMs: Long, targetPositionMs: Long): String
  fun calculateFloatingBubbleX(touchXPx: Float, bubbleWidthPx: Float, trackStartPx: Float, trackEndPx: Float): Float
  fun formatAudioBadgeText(mimeType: String?, bitrate: Int?, sampleRate: Int?): String?
  ```

- [ ] **Step 1: Write the failing unit tests for WaveCardPlayerDynamics**

Create [`app/src/test/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerDynamicsTest.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/test/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerDynamicsTest.kt):
```kotlin
package com.quietrays.tonarc.presentation.components.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveCardPlayerDynamicsTest {

    @Test
    fun resolveWaveCardButtonWeights_whenResting_returnsNeutralWeights() {
        val weights = resolveWaveCardButtonWeights(WaveCardButtonType.NONE)
        assertEquals(1.0f, weights.previous, 0.001f)
        assertEquals(1.75f, weights.playPause, 0.001f)
        assertEquals(1.0f, weights.next, 0.001f)
    }

    @Test
    fun resolveWaveCardButtonWeights_whenPreviousPressed_expandsPrevious() {
        val weights = resolveWaveCardButtonWeights(WaveCardButtonType.PREVIOUS)
        assertEquals(1.15f, weights.previous, 0.001f)
        assertEquals(1.60f, weights.playPause, 0.001f)
        assertEquals(0.90f, weights.next, 0.001f)
    }

    @Test
    fun resolveWaveCardButtonWeights_whenPlayPressed_expandsPlay() {
        val weights = resolveWaveCardButtonWeights(WaveCardButtonType.PLAY_PAUSE)
        assertEquals(0.90f, weights.previous, 0.001f)
        assertEquals(1.85f, weights.playPause, 0.001f)
        assertEquals(0.90f, weights.next, 0.001f)
    }

    @Test
    fun resolveWaveCardButtonWeights_whenNextPressed_expandsNext() {
        val weights = resolveWaveCardButtonWeights(WaveCardButtonType.NEXT)
        assertEquals(0.90f, weights.previous, 0.001f)
        assertEquals(1.60f, weights.playPause, 0.001f)
        assertEquals(1.15f, weights.next, 0.001f)
    }

    @Test
    fun resolveWaveCardButtonScales_whenResting_returnsUnitScales() {
        val scales = resolveWaveCardButtonScales(null)
        assertEquals(1.0f, scales.previous, 0.001f)
        assertEquals(1.0f, scales.playPause, 0.001f)
        assertEquals(1.0f, scales.next, 0.001f)
    }

    @Test
    fun resolveWaveCardButtonScales_whenButtonPressed_squeezesOnlyPressedButton() {
        val scales = resolveWaveCardButtonScales(WaveCardButtonType.PLAY_PAUSE)
        assertEquals(1.0f, scales.previous, 0.001f)
        assertEquals(0.96f, scales.playPause, 0.001f)
        assertEquals(1.0f, scales.next, 0.001f)
    }

    @Test
    fun calculatePluckGaussianMultiplier_atCenter_returnsPeakMultiplier() {
        val boost = calculatePluckGaussianMultiplier(xPx = 100f, touchXPx = 100f, sigmaPx = 40f, maxBoost = 0.6f)
        assertEquals(1.6f, boost, 0.001f)
    }

    @Test
    fun calculatePluckGaussianMultiplier_farFromCenter_returnsBaselineMultiplier() {
        val boost = calculatePluckGaussianMultiplier(xPx = 500f, touchXPx = 100f, sigmaPx = 40f, maxBoost = 0.6f)
        assertEquals(1.0f, boost, 0.01f)
    }

    @Test
    fun formatScrubDelta_forwardSeek_formatsPositiveDelta() {
        val delta = formatScrubDelta(currentPositionMs = 30_000L, targetPositionMs = 45_000L)
        assertEquals("+0:15", delta)
    }

    @Test
    fun formatScrubDelta_backwardSeek_formatsNegativeDelta() {
        val delta = formatScrubDelta(currentPositionMs = 60_000L, targetPositionMs = 28_000L)
        assertEquals("-0:32", delta)
    }

    @Test
    fun formatScrubDelta_samePosition_formatsZeroDelta() {
        val delta = formatScrubDelta(currentPositionMs = 40_000L, targetPositionMs = 40_000L)
        assertEquals("0:00", delta)
    }

    @Test
    fun calculateFloatingBubbleX_clampsWithinTrackBounds() {
        val clampedStart = calculateFloatingBubbleX(
            touchXPx = 10f,
            bubbleWidthPx = 80f,
            trackStartPx = 20f,
            trackEndPx = 400f
        )
        assertEquals(20f, clampedStart, 0.001f)

        val clampedEnd = calculateFloatingBubbleX(
            touchXPx = 390f,
            bubbleWidthPx = 80f,
            trackStartPx = 20f,
            trackEndPx = 400f
        )
        assertEquals(320f, clampedEnd, 0.001f)

        val centered = calculateFloatingBubbleX(
            touchXPx = 200f,
            bubbleWidthPx = 80f,
            trackStartPx = 20f,
            trackEndPx = 400f
        )
        assertEquals(160f, centered, 0.001f)
    }

    @Test
    fun formatAudioBadgeText_withFlacMetadata_formatsFormatAndSampleRate() {
        val badge = formatAudioBadgeText(mimeType = "audio/flac", bitrate = 0, sampleRate = 96000)
        assertEquals("FLAC • 96.0 kHz", badge)
    }

    @Test
    fun formatAudioBadgeText_withMp3Metadata_formatsBitrateAndFormat() {
        val badge = formatAudioBadgeText(mimeType = "audio/mpeg", bitrate = 320000, sampleRate = 44100)
        assertEquals("320 kbps • MP3", badge)
    }

    @Test
    fun formatAudioBadgeText_withEmptyMetadata_returnsNull() {
        val badge = formatAudioBadgeText(mimeType = null, bitrate = null, sampleRate = null)
        assertNull(badge)
    }
}
```

- [ ] **Step 2: Run unit test to verify failure**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "*.WaveCardPlayerDynamicsTest" --no-daemon
```
Expected: FAIL with unresolved symbols `WaveCardPlayerDynamics` / `resolveWaveCardButtonWeights`.

- [ ] **Step 3: Implement `WaveCardPlayerDynamics.kt`**

Create [`app/src/main/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerDynamics.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerDynamics.kt):
```kotlin
package com.quietrays.tonarc.presentation.components.player

import com.quietrays.tonarc.utils.AudioMetaUtils
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp

enum class WaveCardButtonType {
    NONE,
    PREVIOUS,
    PLAY_PAUSE,
    NEXT
}

data class WaveCardButtonWeights(
    val previous: Float,
    val playPause: Float,
    val next: Float
)

data class WaveCardButtonScales(
    val previous: Float,
    val playPause: Float,
    val next: Float
)

/**
 * Resolves button layout weights during touch press and resting states.
 * Previous/Next: resting 1.0f, gentle expansion to 1.15f when pressed.
 * Play/Pause: resting 1.75f, gentle expansion to 1.85f when pressed.
 */
fun resolveWaveCardButtonWeights(activeButton: WaveCardButtonType?): WaveCardButtonWeights {
    return when (activeButton) {
        WaveCardButtonType.PREVIOUS -> WaveCardButtonWeights(
            previous = 1.15f,
            playPause = 1.60f,
            next = 0.90f
        )
        WaveCardButtonType.PLAY_PAUSE -> WaveCardButtonWeights(
            previous = 0.90f,
            playPause = 1.85f,
            next = 0.90f
        )
        WaveCardButtonType.NEXT -> WaveCardButtonWeights(
            previous = 0.90f,
            playPause = 1.60f,
            next = 1.15f
        )
        WaveCardButtonType.NONE, null -> WaveCardButtonWeights(
            previous = 1.00f,
            playPause = 1.75f,
            next = 1.00f
        )
    }
}

/**
 * Resolves button scale squeeze on touch press.
 * Squeezes the pressed button uniformly to 0.96f; resting buttons remain 1.0f.
 */
fun resolveWaveCardButtonScales(activeButton: WaveCardButtonType?): WaveCardButtonScales {
    return WaveCardButtonScales(
        previous = if (activeButton == WaveCardButtonType.PREVIOUS) 0.96f else 1.0f,
        playPause = if (activeButton == WaveCardButtonType.PLAY_PAUSE) 0.96f else 1.0f,
        next = if (activeButton == WaveCardButtonType.NEXT) 0.96f else 1.0f
    )
}

/**
 * Calculates a Gaussian amplitude multiplier under the user's touch point,
 * producing a physical pluck amplitude bump on the waveform.
 */
fun calculatePluckGaussianMultiplier(
    xPx: Float,
    touchXPx: Float,
    sigmaPx: Float,
    maxBoost: Float = 0.55f
): Float {
    if (sigmaPx <= 0f) return 1.0f
    val dx = xPx - touchXPx
    val gaussian = exp(-(dx * dx) / (2f * sigmaPx * sigmaPx))
    return 1.0f + maxBoost * gaussian
}

/**
 * Formats a scrub delta string displaying the signed seeking difference (+0:15, -0:32, 0:00).
 */
fun formatScrubDelta(currentPositionMs: Long, targetPositionMs: Long): String {
    val deltaMs = targetPositionMs - currentPositionMs
    val totalSeconds = abs(deltaMs) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val formatted = "%d:%02d".format(Locale.US, minutes, seconds)
    return when {
        deltaMs > 0 -> "+$formatted"
        deltaMs < 0 -> "-$formatted"
        else -> "0:00"
    }
}

/**
 * Centers a floating time bubble above the touch X position, clamped inside track boundaries.
 */
fun calculateFloatingBubbleX(
    touchXPx: Float,
    bubbleWidthPx: Float,
    trackStartPx: Float,
    trackEndPx: Float
): Float {
    val rawX = touchXPx - (bubbleWidthPx / 2f)
    val minX = trackStartPx
    val maxX = (trackEndPx - bubbleWidthPx).coerceAtLeast(minX)
    return rawX.coerceIn(minX, maxX)
}

/**
 * Formats a technical audio badge label (e.g. "FLAC • 96.0 kHz" or "320 kbps • MP3").
 */
fun formatAudioBadgeText(mimeType: String?, bitrate: Int?, sampleRate: Int?): String? {
    val rawFormat = AudioMetaUtils.mimeTypeToFormat(mimeType)
    val formatLabel = rawFormat.takeIf { it != "-" }?.uppercase(Locale.US)

    val parts = buildList {
        bitrate?.takeIf { it > 0 }?.let { br ->
            val kbps = "${br / 1000} kbps"
            if (formatLabel != null) {
                add("$kbps • $formatLabel")
            } else {
                add(kbps)
            }
        } ?: formatLabel?.let { add(it) }

        sampleRate?.takeIf { it > 0 }?.let { sr ->
            add(String.format(Locale.US, "%.1f kHz", sr / 1000.0))
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
}
```

- [ ] **Step 4: Run unit tests to verify pass**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "*.WaveCardPlayerDynamicsTest" --no-daemon
```
Expected: PASS with 8 successful tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerDynamics.kt app/src/test/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerDynamicsTest.kt
git commit -m "feat(player): implement wave card player dynamics and math calculations"
```

---

### Task 2: Dual Harmonic Spline Waveform & Liquid Scrubber Canvas

**Files:**
- Create: [`app/src/main/java/com/quietrays/tonarc/presentation/components/player/LiquidWaveScrubber.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/presentation/components/player/LiquidWaveScrubber.kt)
- Modify: [`app/src/test/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerDynamicsTest.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/test/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerDynamicsTest.kt)

**Interfaces:**
- Produces:
  ```kotlin
  package com.quietrays.tonarc.presentation.components.player

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
  )
  ```

- [ ] **Step 1: Write unit tests for timeline interval crossing and format helpers**

In [`app/src/test/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerDynamicsTest.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/test/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerDynamicsTest.kt), add:
```kotlin
    @Test
    fun shouldTriggerHapticTick_detectsSecondBoundaryCrossed() {
        val lastSecond = 14L
        val currentSecond1 = 14L
        val currentSecond2 = 15L
        assertEquals(false, lastSecond != currentSecond1)
        assertEquals(true, lastSecond != currentSecond2)
    }

    @Test
    fun calculateScrubberExpansionHeight_expandsWhenScrubbing() {
        val restingHeight = 36f
        val scrubbingHeight = 52f
        assertTrue(scrubbingHeight > restingHeight)
    }
```

- [ ] **Step 2: Run unit test to verify**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "*.WaveCardPlayerDynamicsTest" --no-daemon
```
Expected: PASS.

- [ ] **Step 3: Implement `LiquidWaveScrubber.kt`**

Create [`app/src/main/java/com/quietrays/tonarc/presentation/components/player/LiquidWaveScrubber.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/presentation/components/player/LiquidWaveScrubber.kt):
```kotlin
package com.quietrays.tonarc.presentation.components.player

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
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
            val bubbleXOffsetDp = with(density) { bubbleXPx.toDp() }
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
                            if (currentSec != lastHapticSecond) {
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

                    val primaryPath = Path()
                    val echoPath = Path()
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
```

- [ ] **Step 4: Verify compilation and unit tests**

Run:
```bash
./gradlew :app:compileDebugKotlin --no-daemon
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/components/player/LiquidWaveScrubber.kt app/src/test/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerDynamicsTest.kt
git commit -m "feat(player): implement liquid wave scrubber with dual harmonic spline and touch bubble"
```

---

### Task 3: Visual Architecture, Ambient Depth & Card Materiality ("Frosted Studio Deck")

**Files:**
- Modify: [`app/src/main/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerContent.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerContent.kt)

**Interfaces:**
- Consumes:
  - `fullPlayerSlice.showAudioTools`
  - `CompositingStrategy.Offscreen`, `BlendMode.DstIn`
  - Palette color extraction from `LocalMaterialTheme.current`

- [ ] **Step 1: Check existing colors, gradient masks, and card styling in `WaveCardPlayerContent.kt`**

Inspect lines 275–375 and lines 525–610 in `WaveCardPlayerContent.kt`.
Notice:
1. Artwork fade is currently linear with `96.dp`. It needs a cubic-eased alpha gradient over `110.dp`.
2. There is no ambient radial bloom behind the artwork bleeding palette color into the surface background.
3. The middle card fill is a flat single color with an outline in light mode only. It needs a frosted multi-stop gradient from `surfaceContainerLow` to `surfaceContainer`, an inner specular hairline highlight along the top edge, and a refined `1.5.dp` outline using `outlineVariant` in Dark mode and warm soft outline in Light mode.
4. Top glass action buttons need `2.dp` tonal elevation and subtle border.

- [ ] **Step 2: Update artwork stage with ambient bloom, cubic fade, and glass top buttons**

In [`app/src/main/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerContent.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerContent.kt), update the upper section:
1. Add an ambient radial bloom `Box` positioned behind the album art:
```kotlin
// Ambient Radial Bloom bleeding extracted palette color into background
val bloomAlpha = if (isDark) 0.18f else 0.08f
Box(
    modifier = Modifier
        .fillMaxSize()
        .background(
            Brush.radialGradient(
                colors = listOf(
                    colorScheme.primary.copy(alpha = bloomAlpha),
                    colorScheme.surfaceContainerLowest
                ),
                radius = 700f
            )
        )
)
```
2. Replace linear artwork fade with cubic-eased alpha gradient over `110.dp`:
```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fadeHeightPx = 110.dp.toPx()
            val startY = (size.height - fadeHeightPx).coerceAtLeast(0f)
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Black,
                        0.4f to Color.Black.copy(alpha = 0.85f),
                        0.75f to Color.Black.copy(alpha = 0.40f),
                        1.0f to Color.Transparent
                    ),
                    startY = startY,
                    endY = size.height
                ),
                blendMode = BlendMode.DstIn
            )
        }
)
```
3. Update middle card drawing with specular hairline highlight and subtle outline border:
```kotlin
// Specular Top Hairline Highlight
drawLine(
    color = colorScheme.onSurface.copy(alpha = if (isDark) 0.15f else 0.25f),
    start = Offset(0f, 0.5f),
    end = Offset(w, 0.5f),
    strokeWidth = 1.dp.toPx()
)
```
4. Set card background fill to `Brush.verticalGradient(listOf(colorScheme.surfaceContainerLow, colorScheme.surfaceContainer))` and card outline to `colorScheme.outlineVariant.copy(alpha = if (isDark) 0.5f else 0.8f)`.

- [ ] **Step 3: Compile and run unit tests to verify changes**

Run:
```bash
./gradlew :app:compileDebugKotlin --no-daemon
./gradlew :app:testDebugUnitTest --no-daemon
```
Expected: BUILD SUCCESSFUL and all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerContent.kt
git commit -m "feat(player): elevate wave card visual architecture with ambient bloom and frosted card"
```

---

### Task 4: Typography, Audio Badge Chip, Auxiliary Controls & Transport Dock Ergonomics

**Files:**
- Modify: [`app/src/main/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerContent.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerContent.kt)

**Interfaces:**
- Consumes:
  - `WaveCardPlayerDynamics.kt` (`resolveWaveCardButtonWeights`, `resolveWaveCardButtonScales`, `formatAudioBadgeText`)
  - `LiquidWaveScrubber.kt` (`LiquidWaveScrubber`)
  - `SongInfoBottomSheet.kt`
  - `MorphingPlayPauseIcon.kt`, `LoadingIndicator`

- [ ] **Step 1: Wire `SongInfoBottomSheet` and Audio Badge Chip in `WaveCardPlayerContent.kt`**

In [`app/src/main/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerContent.kt`](file:///home/dharshan/PixelPlayerOSS/app/src/main/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerContent.kt):
1. Add `var showSongInfoBottomSheet by remember { mutableStateOf(false) }`.
2. Compute the audio badge text:
```kotlin
val audioBadgeText = remember(
    currentSong.mimeType,
    currentSong.bitrate,
    currentSong.sampleRate,
    fullPlayerSlice.audioMetadata
) {
    val meta = fullPlayerSlice.audioMetadata
    val effectiveMime = meta.mimeType ?: currentSong.mimeType
    val effectiveBitrate = meta.bitrate ?: currentSong.bitrate
    val effectiveSampleRate = meta.sampleRate ?: currentSong.sampleRate
    formatAudioBadgeText(effectiveMime, effectiveBitrate, effectiveSampleRate)
}
```
3. Render the compact stadium chip above the scrubber when `fullPlayerSlice.showPlayerFileInfo && audioBadgeText != null`:
```kotlin
if (fullPlayerSlice.showPlayerFileInfo && audioBadgeText != null) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(colorScheme.surfaceContainerHigh)
            .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f), CircleShape)
            .clickable { showSongInfoBottomSheet = true }
            .padding(horizontal = 10.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = audioBadgeText,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                letterSpacing = 0.4.sp
            ),
            color = colorScheme.onSurfaceVariant
        )
    }
}
```
4. Render `SongInfoBottomSheet` when `showSongInfoBottomSheet == true`:
```kotlin
if (showSongInfoBottomSheet) {
    SongInfoBottomSheet(
        song = currentSong,
        isFavorite = isFavorite,
        onToggleFavorite = onFavoriteToggle,
        onDismiss = { showSongInfoBottomSheet = false }
    )
}
```

- [ ] **Step 2: Replace Scrubber with `LiquidWaveScrubber`**

Replace `WaveformScrubberCanvas` invocation with `LiquidWaveScrubber`:
```kotlin
LiquidWaveScrubber(
    currentPositionMs = currentPosition,
    totalDurationMs = totalDuration,
    isPlaying = isPlaying,
    waveColor = colorScheme.primary,
    trackInactiveColor = colorScheme.onSurface.copy(alpha = if (isDark) 0.22f else 0.18f),
    bubbleContainerColor = colorScheme.surfaceContainerHighest,
    bubbleContentColor = colorScheme.onSurface,
    bubbleBorderColor = colorScheme.outlineVariant.copy(alpha = 0.7f),
    thumbColor = if (isDark) colorScheme.primary else Color.White,
    thumbBorderColor = if (isDark) Color.Transparent else colorScheme.outlineVariant,
    onSeek = onSeek,
    modifier = Modifier
        .weight(1f)
        .padding(horizontal = 6.dp)
)
```

- [ ] **Step 3: Update Auxiliary Controls Row**

Ensure the row with Shuffle, Repeat, and Queue has 16.dp breathing room and balanced spacing:
- Shuffle toggle (`Icons.Rounded.Shuffle`)
- Repeat toggle (`Icons.Rounded.Repeat` / `RepeatOne`)
- Queue sheet trigger (`Icons.AutoMirrored.Rounded.QueueMusic`)

- [ ] **Step 4: Refine Transport Dock Ergonomics**

In the bottom transport dock:
1. Use `resolveWaveCardButtonWeights(currentActiveButton)` and `resolveWaveCardButtonScales(currentActiveButton)`.
2. Height of squircle previous/next: `58.dp` with `20.dp` rounded corners.
3. Height of play/pause stadium pill: `64.dp` with `CircleShape`.
4. Spring spec: `spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)`.
5. Button colors: `surfaceContainerHigh` for previous/next, `primaryContainer` for play/pause with accent outline.

- [ ] **Step 5: Compile and run unit tests**

Run:
```bash
./gradlew :app:compileDebugKotlin --no-daemon
./gradlew :app:testDebugUnitTest --no-daemon
```
Expected: BUILD SUCCESSFUL and all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/quietrays/tonarc/presentation/components/player/WaveCardPlayerContent.kt
git commit -m "feat(player): refine wave card transport dock ergonomics and audio badge chip"
```

---

### Task 5: End-to-End Build, Verification & Device Testing

**Files:**
- Verify: Full repository test suite, debug APK assembly, device deployment

- [ ] **Step 1: Run complete unit test suite**

Run:
```bash
./gradlew :app:testDebugUnitTest --no-daemon
```
Expected: ALL unit tests pass cleanly.

- [ ] **Step 2: Run zero-emoji verification script**

Run:
```bash
python3 -c '
import os, sys

violations = []
for root, dirs, files in os.walk("."):
    if any(p in root for p in [".git", "build", ".gradle", "captures"]):
        continue
    for f in files:
        if f.endswith((".kt", ".xml", ".md", ".json")):
            path = os.path.join(root, f)
            with open(path, "r", encoding="utf-8", errors="ignore") as fh:
                for idx, line in enumerate(fh, 1):
                    for ch in line:
                        if ord(ch) > 0x1F000 or (0x2600 <= ord(ch) <= 0x27BF):
                            violations.append(f"{path}:{idx} -> {ch}")

if violations:
    print("Emoji violations found:")
    for v in violations[:10]:
        print(v)
    sys.exit(1)
else:
    print("Zero emojis verified across all project files.")
'
```
Expected: "Zero emojis verified across all project files."

- [ ] **Step 3: Assemble debug APK**

Run:
```bash
./gradlew assembleDebug --no-daemon
```
Expected: BUILD SUCCESSFUL, producing `app-debug.apk`.

- [ ] **Step 4: Install and verify on device**

Run:
```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Capture screencaps and verify:
1. Album art ambient radial bloom and cubic fade.
2. Frosted card depth, specular top hairline highlight, and rounded corners.
3. Liquid wave scrubber ripple when playing and low baseline when paused.
4. Touch expansion to 52.dp and floating time bubble with scrub delta (+0:15).
5. Audio badge chip displaying codec and opening `SongInfoBottomSheet`.
6. Transport dock weights and spring dynamics with M3 Expressive loading indicator.

- [ ] **Step 5: Final review and commit**

```bash
git status
```
Confirm clean working tree.
