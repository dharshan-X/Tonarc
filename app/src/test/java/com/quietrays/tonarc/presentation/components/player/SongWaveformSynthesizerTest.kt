package com.quietrays.tonarc.presentation.components.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongWaveformSynthesizerTest {

    @Test
    fun generate_producesRequestedBarCount() {
        val bars = SongWaveformSynthesizer.generate(
            title = "La Madrague",
            artist = "Freddie Dredd",
            durationMs = 176_000L,
            barCount = 58
        )
        assertEquals(58, bars.size)
    }

    @Test
    fun generate_allValuesWithinValidNormalizedRange() {
        val bars = SongWaveformSynthesizer.generate(
            title = "Blinding Lights",
            artist = "The Weeknd",
            durationMs = 200_000L,
            barCount = 58
        )
        for (bar in bars) {
            assertTrue("Bar value $bar should be >= 0.14f", bar >= 0.14f)
            assertTrue("Bar value $bar should be <= 1.0f", bar <= 1.0f)
        }
    }

    @Test
    fun generate_isDeterministicForSameSong() {
        val bars1 = SongWaveformSynthesizer.generate(
            title = "Starlight Horizons",
            artist = "Solaris Nova",
            durationMs = 222_000L,
            barCount = 58
        )
        val bars2 = SongWaveformSynthesizer.generate(
            title = "Starlight Horizons",
            artist = "Solaris Nova",
            durationMs = 222_000L,
            barCount = 58
        )
        assertEquals(bars1, bars2)
    }

    @Test
    fun generate_producesDistinctWaveformsForDifferentSongs() {
        val freddieWaveform = SongWaveformSynthesizer.generate(
            title = "La Madrague (Prod. Ryan C)",
            artist = "Freddie Dredd",
            durationMs = 176_000L,
            barCount = 58
        )
        val weekndWaveform = SongWaveformSynthesizer.generate(
            title = "Blinding Lights",
            artist = "The Weeknd",
            durationMs = 200_000L,
            barCount = 58
        )
        val kendrickWaveform = SongWaveformSynthesizer.generate(
            title = "Not Like Us",
            artist = "Kendrick Lamar",
            durationMs = 274_000L,
            barCount = 58
        )

        assertNotEquals(freddieWaveform, weekndWaveform)
        assertNotEquals(freddieWaveform, kendrickWaveform)
        assertNotEquals(weekndWaveform, kendrickWaveform)
    }
}
