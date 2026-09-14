package com.quietrays.tonarc.presentation.components.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
