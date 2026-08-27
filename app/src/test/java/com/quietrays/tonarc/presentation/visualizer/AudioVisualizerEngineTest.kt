package com.quietrays.tonarc.presentation.visualizer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AudioVisualizerEngineTest {

    @Test
    fun `computeFrame when playing synthesizes dynamic frequency spectrum and rotates vinyl`() {
        val initialFrame = VisualizerFrameData()
        val nextFrame = AudioVisualizerEngine.computeFrame(
            previousFrame = initialFrame,
            isPlaying = true,
            currentPositionMs = 1500L,
            deltaTimeSec = 0.016f
        )

        assertEquals(32, nextFrame.frequencyBands.size)
        assertEquals(64, nextFrame.wavePoints.size)

        // Rotation angle should advance when playing
        assertTrue(nextFrame.rotationAngle > initialFrame.rotationAngle)

        // Frequency bands should have active values
        assertTrue(nextFrame.frequencyBands.any { it > 0.1f })

        // Bass energy should be computed
        assertTrue(nextFrame.bassEnergy >= 0f)
    }

    @Test
    fun `computeFrame when paused decays frequency spectrum and keeps rotation steady`() {
        val activeFrame = VisualizerFrameData(
            frequencyBands = FloatArray(32) { 0.8f },
            wavePoints = FloatArray(64) { 0.5f },
            bassEnergy = 0.9f,
            rotationAngle = 45f
        )

        val decayedFrame = AudioVisualizerEngine.computeFrame(
            previousFrame = activeFrame,
            isPlaying = false,
            currentPositionMs = 1500L,
            deltaTimeSec = 0.05f
        )

        // Rotation angle should remain steady when paused
        assertEquals(45f, decayedFrame.rotationAngle)

        // Frequency magnitudes should decay downwards
        assertTrue(decayedFrame.frequencyBands.all { it < 0.8f })
        assertTrue(decayedFrame.bassEnergy < 0.9f)
    }

    @Test
    fun `VisualizerMode and VisualizerStyle storage key resolution`() {
        assertEquals(VisualizerMode.SPECTRUM_BARS, VisualizerMode.fromStorageKey("spectrum_bars"))
        assertEquals(VisualizerMode.FLUID_WAVE, VisualizerMode.fromStorageKey("fluid_wave"))
        assertEquals(VisualizerMode.CIRCULAR_PULSE, VisualizerMode.fromStorageKey("circular_pulse"))
        assertEquals(VisualizerMode.VINYL_TURNTABLE, VisualizerMode.fromStorageKey("vinyl_turntable"))
        assertEquals(VisualizerMode.VINTAGE_CASSETTE, VisualizerMode.fromStorageKey("vintage_cassette"))
        assertEquals(VisualizerMode.ANALOG_VU_METERS, VisualizerMode.fromStorageKey("analog_vu_meters"))
        assertEquals(VisualizerMode.SPECTRUM_BARS, VisualizerMode.fromStorageKey("unknown_key"))

        assertEquals(VisualizerStyle.ACCENT, VisualizerStyle.fromStorageKey("accent"))
        assertEquals(VisualizerStyle.GRADIENT, VisualizerStyle.fromStorageKey("gradient"))
        assertEquals(VisualizerStyle.MONOCHROME, VisualizerStyle.fromStorageKey("monochrome"))
        assertEquals(VisualizerStyle.ACCENT, VisualizerStyle.fromStorageKey(null))
    }

    @Test
    fun `computeFrame calculates dual channel VU needle angles and tape progress`() {
        val initialFrame = VisualizerFrameData()
        val nextFrame = AudioVisualizerEngine.computeFrame(
            previousFrame = initialFrame,
            isPlaying = true,
            currentPositionMs = 30000L,
            durationMs = 120000L,
            deltaTimeSec = 0.016f
        )

        // Needle angles should be within realistic gauge sweep range [-45 deg, +45 deg]
        assertTrue(nextFrame.leftNeedleAngle in -45f..45f)
        assertTrue(nextFrame.rightNeedleAngle in -45f..45f)

        // Tape progress at 30s / 120s should be ~0.25
        assertEquals(0.25f, nextFrame.tapeProgress, 0.02f)
    }

    @Test
    fun `VisualizerFrameData equality and hashcode`() {
        val f1 = VisualizerFrameData(frequencyBands = floatArrayOf(0.1f, 0.2f), wavePoints = floatArrayOf(0.5f), bassEnergy = 0.8f, rotationAngle = 10f, leftNeedleAngle = 5f, rightNeedleAngle = -10f, leftPeak = false, rightPeak = true, tapeProgress = 0.5f)
        val f2 = VisualizerFrameData(frequencyBands = floatArrayOf(0.1f, 0.2f), wavePoints = floatArrayOf(0.5f), bassEnergy = 0.8f, rotationAngle = 10f, leftNeedleAngle = 5f, rightNeedleAngle = -10f, leftPeak = false, rightPeak = true, tapeProgress = 0.5f)
        val f3 = VisualizerFrameData(frequencyBands = floatArrayOf(0.3f, 0.4f), wavePoints = floatArrayOf(0.5f), bassEnergy = 0.8f, rotationAngle = 10f, leftNeedleAngle = 5f, rightNeedleAngle = -10f, leftPeak = false, rightPeak = true, tapeProgress = 0.5f)

        assertEquals(f1, f2)
        assertEquals(f1.hashCode(), f2.hashCode())
        assertFalse(f1 == f3)
    }
}
