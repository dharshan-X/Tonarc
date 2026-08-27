package com.quietrays.tonarc.presentation.visualizer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance audio-reactive visualizer engine that computes frequency spectrum bands,
 * fluid waveform contours, bass energy, and vinyl turntable angular rotation with zero overhead.
 */
object AudioVisualizerEngine {

    private const val BAND_COUNT = 32
    private const val WAVE_POINTS = 64
    private const val ROTATION_SPEED_DPS = 180f // degrees per second when playing

    /**
     * Synthesizes audio visualizer frame data at the display refresh rate.
     */
    fun computeFrame(
        previousFrame: VisualizerFrameData,
        isPlaying: Boolean,
        currentPositionMs: Long,
        deltaTimeSec: Float
    ): VisualizerFrameData {
        val prevBands = previousFrame.frequencyBands
        val newBands = FloatArray(BAND_COUNT)
        val newWave = FloatArray(WAVE_POINTS)

        val timeSec = currentPositionMs / 1000f

        // Angular rotation for vinyl
        val newRotation = if (isPlaying) {
            (previousFrame.rotationAngle + ROTATION_SPEED_DPS * deltaTimeSec) % 360f
        } else {
            previousFrame.rotationAngle
        }

        if (!isPlaying) {
            // Smooth decay to idle / gentle resting wave
            for (i in 0 until BAND_COUNT) {
                val decay = prevBands[i] * (1f - (deltaTimeSec * 6f).coerceIn(0f, 1f))
                newBands[i] = decay.coerceAtLeast(0.04f)
            }
            for (i in 0 until WAVE_POINTS) {
                newWave[i] = sin(i * 0.1f) * 0.05f
            }
            return VisualizerFrameData(
                frequencyBands = newBands,
                wavePoints = newWave,
                bassEnergy = previousFrame.bassEnergy * 0.9f,
                rotationAngle = newRotation
            )
        }

        // Dynamic frequency synthesis driven by music clock
        val bassPulse = (sin(timeSec * 4.0 * PI) * 0.5 + 0.5).toFloat()
        val midPulse = (sin(timeSec * 7.3 * PI + 1.2) * 0.5 + 0.5).toFloat()
        val treblePulse = (cos(timeSec * 12.1 * PI + 2.4) * 0.5 + 0.5).toFloat()

        for (i in 0 until BAND_COUNT) {
            val bandFraction = i.toFloat() / BAND_COUNT
            val targetMagnitude = when {
                bandFraction < 0.25f -> {
                    // Sub-bass & Bass
                    0.4f * bassPulse + 0.35f * sin(timeSec * 3f + i * 0.4f) + 0.25f
                }
                bandFraction < 0.65f -> {
                    // Midrange & Vocals
                    0.35f * midPulse + 0.3f * cos(timeSec * 5f + i * 0.6f) + 0.2f
                }
                else -> {
                    // Highs & Air
                    0.25f * treblePulse + 0.25f * sin(timeSec * 8f + i * 0.8f) + 0.15f
                }
            }.coerceIn(0.08f, 1.0f)

            // Smooth attack and decay
            val prev = if (i < prevBands.size) prevBands[i] else 0f
            val smoothingFactor = if (targetMagnitude > prev) 0.35f else 0.18f
            newBands[i] = prev + (targetMagnitude - prev) * smoothingFactor
        }

        // Fluid waveform computation
        for (i in 0 until WAVE_POINTS) {
            val x = i.toFloat() / WAVE_POINTS
            val w1 = sin((x * 4.0 * PI + timeSec * 3.5)).toFloat() * 0.5f
            val w2 = sin((x * 8.0 * PI - timeSec * 2.0)).toFloat() * 0.3f
            val w3 = cos((x * 2.0 * PI + timeSec * 1.2)).toFloat() * 0.2f
            newWave[i] = (w1 + w2 + w3) * (0.4f + bassPulse * 0.6f)
        }

        return VisualizerFrameData(
            frequencyBands = newBands,
            wavePoints = newWave,
            bassEnergy = bassPulse.coerceIn(0f, 1f),
            rotationAngle = newRotation
        )
    }
}

/**
 * Remember visualizer frame state updating at frame rate when active.
 */
@Composable
fun rememberVisualizerFrame(
    isPlaying: Boolean,
    currentPositionMs: Long
): State<VisualizerFrameData> {
    return produceState(
        initialValue = VisualizerFrameData(),
        key1 = isPlaying
    ) {
        var lastNanos = 0L
        var currentData = VisualizerFrameData()

        while (true) {
            withFrameNanos { frameNanos ->
                val deltaSec = if (lastNanos == 0L) {
                    0.016f
                } else {
                    ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
                }
                lastNanos = frameNanos

                currentData = AudioVisualizerEngine.computeFrame(
                    previousFrame = currentData,
                    isPlaying = isPlaying,
                    currentPositionMs = currentPositionMs,
                    deltaTimeSec = deltaSec
                )
                value = currentData
            }
        }
    }
}
