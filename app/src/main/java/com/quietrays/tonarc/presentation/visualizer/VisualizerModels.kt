package com.quietrays.tonarc.presentation.visualizer

import androidx.compose.runtime.Immutable

@Immutable
enum class VisualizerMode(val storageKey: String, val displayName: String, val description: String) {
    SPECTRUM_BARS("spectrum_bars", "Spectrum Bars", "32-band dynamic equalizer bars with peak decay"),
    FLUID_WAVE("fluid_wave", "Fluid Wave", "Organic multi-layer fluid sine wave oscillating to rhythm"),
    CIRCULAR_PULSE("circular_pulse", "Circular Pulse", "Radial aura pulsing around album art with bass energy"),
    VINYL_TURNTABLE("vinyl_turntable", "Vinyl Turntable", "Authentic spinning vinyl record with tonearm");

    companion object {
        fun fromStorageKey(key: String?): VisualizerMode =
            entries.find { it.storageKey.equals(key, ignoreCase = true) } ?: SPECTRUM_BARS
    }
}

@Immutable
enum class VisualizerStyle(val storageKey: String, val displayName: String) {
    ACCENT("accent", "Theme Accent"),
    GRADIENT("gradient", "Dynamic Gradient"),
    MONOCHROME("monochrome", "Monochrome");

    companion object {
        fun fromStorageKey(key: String?): VisualizerStyle =
            entries.find { it.storageKey.equals(key, ignoreCase = true) } ?: ACCENT
    }
}

@Immutable
data class VisualizerFrameData(
    val frequencyBands: FloatArray = FloatArray(32),
    val wavePoints: FloatArray = FloatArray(64),
    val bassEnergy: Float = 0f,
    val rotationAngle: Float = 0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VisualizerFrameData

        if (!frequencyBands.contentEquals(other.frequencyBands)) return false
        if (!wavePoints.contentEquals(other.wavePoints)) return false
        if (bassEnergy != other.bassEnergy) return false
        if (rotationAngle != other.rotationAngle) return false

        return true
    }

    override fun hashCode(): Int {
        var result = frequencyBands.contentHashCode()
        result = 31 * result + wavePoints.contentHashCode()
        result = 31 * result + bassEnergy.hashCode()
        result = 31 * result + rotationAngle.hashCode()
        return result
    }
}
