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
        if (bitrate != null && bitrate > 0) {
            val kbps = "${bitrate / 1000} kbps"
            if (formatLabel != null) {
                add("$kbps • $formatLabel")
            } else {
                add(kbps)
            }
        } else {
            if (formatLabel != null) {
                add(formatLabel)
            }
            sampleRate?.takeIf { it > 0 }?.let { sr ->
                add(String.format(Locale.US, "%.1f kHz", sr / 1000.0))
            }
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
}

/**
 * Determines whether haptic tick feedback should be triggered when crossing second boundaries during scrub.
 */
fun shouldTriggerSecondHapticTick(lastHapticSecond: Long, currentSecond: Long): Boolean {
    return lastHapticSecond >= 0L && currentSecond != lastHapticSecond
}
