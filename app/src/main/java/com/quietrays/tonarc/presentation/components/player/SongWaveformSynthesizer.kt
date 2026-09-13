package com.quietrays.tonarc.presentation.components.player

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object SongWaveformSynthesizer {

    /**
     * Synthesizes a deterministic, realistic acoustic amplitude fingerprint based on the song's musical identity.
     * Every song receives a distinct waveform profile reflecting realistic musical structure:
     * intro entry, verse rhythmic cadence, pre-chorus buildup, chorus dynamic wall of sound,
     * bridge breakdown, chorus climax, and outro decrescendo.
     */
    fun generate(
        title: String,
        artist: String,
        durationMs: Long,
        barCount: Int = 58
    ): List<Float> {
        val durationSec = (durationMs / 1000L).coerceAtLeast(30L)
        val key = "${title.trim().lowercase()}__${artist.trim().lowercase()}__$durationSec"
        var h = 0
        for (ch in key) {
            h = ((h shl 5) - h) + ch.code
        }
        var seed = if (h != 0) abs(h.toLong()) else 1234567L

        fun lcg(): Float {
            seed = (seed * 1664525L + 1013904223L) and 0xFFFFFFFFL
            return ((seed ushr 16) and 0xFFFFL).toFloat() / 65536f
        }

        val kickInterval = 2 + (lcg() * 3f).toInt()
        val tempoFactor = 3f + lcg() * 5f
        val harmFreq = 6f + lcg() * 6f
        val chorusPeak = 0.86f + lcg() * 0.14f

        return (0 until barCount).map { i ->
            val t = i.toFloat() / (barCount - 1).coerceAtLeast(1)
            val macro = when {
                t < 0.10f -> 0.18f + (t / 0.10f) * 0.36f
                t < 0.32f -> 0.42f + 0.16f * sin(t * tempoFactor * PI.toFloat())
                t < 0.42f -> {
                    val p = (t - 0.32f) / 0.10f
                    0.54f + p * 0.30f
                }
                t < 0.62f -> 0.82f + 0.14f * cos(t * harmFreq * PI.toFloat())
                t < 0.72f -> 0.34f + 0.18f * sin(t * 3.5f * PI.toFloat())
                t < 0.88f -> 0.88f + 0.12f * sin(t * harmFreq * 1.3f * PI.toFloat())
                else -> {
                    val p = (t - 0.88f) / 0.12f
                    0.72f * (1f - p * 0.75f)
                }
            }

            val isKick = (i % kickInterval == 0)
            val kickBoost = if (isKick) 0.18f else 0f
            val jitter = (lcg() - 0.5f) * 0.22f

            val raw = (macro + kickBoost + jitter) * chorusPeak
            raw.coerceIn(0.14f, 1.0f)
        }
    }
}
