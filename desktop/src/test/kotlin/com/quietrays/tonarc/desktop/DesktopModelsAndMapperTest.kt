package com.quietrays.tonarc.desktop

import com.google.common.truth.Truth.assertThat
import com.quietrays.tonarc.data.network.youtube.InnertubeTrack
import com.quietrays.tonarc.desktop.data.toDesktopTrack
import com.quietrays.tonarc.desktop.model.DesktopTrack
import com.quietrays.tonarc.desktop.model.EqualizerPreset
import com.quietrays.tonarc.desktop.model.PersistedDesktopSettings
import com.quietrays.tonarc.desktop.model.TrackSource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class DesktopModelsAndMapperTest {

    @Test
    fun `InnertubeTrack maps to DesktopTrack with upscaled artwork`() {
        val ytTrack = InnertubeTrack(
            videoId = "abc123",
            title = "Midnight City",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            durationSeconds = 243,
            thumbnailUri = "https://lh3.googleusercontent.com/xyz=w120-h120-l90-rj"
        )

        val mapped = ytTrack.toDesktopTrack()
        assertThat(mapped.id).isEqualTo("yt:abc123")
        assertThat(mapped.source).isEqualTo(TrackSource.YOUTUBE)
        assertThat(mapped.durationMs).isEqualTo(243_000L)
        assertThat(mapped.artworkUrl).contains("=w544-h544-l90-rj")
    }

    @Test
    fun `Equalizer presets all contain 10 bands`() {
        assertThat(EqualizerPreset.BAND_FREQUENCIES).hasSize(10)
        assertThat(EqualizerPreset.BAND_LABELS).hasSize(10)
        EqualizerPreset.PRESETS.forEach { preset ->
            assertThat(preset.bands).hasSize(10)
        }
    }

    @Test
    fun `DesktopTrack codecBadge formats hi-res metadata`() {
        val flac = DesktopTrack(
            id = "local:1",
            title = "Song",
            artist = "Artist",
            source = TrackSource.LOCAL,
            mimeType = "FLAC",
            sampleRateHz = 96000,
            bitrateKbps = 2800
        )
        assertThat(flac.codecBadge).isEqualTo("FLAC • 96.0kHz • 2800kbps")
    }

    @Test
    fun `PersistedDesktopSettings serializes and deserializes cleanly`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val original = PersistedDesktopSettings(
            musicFolders = listOf("/home/user/Music"),
            favoriteTrackIds = setOf("local:1", "yt:abc"),
            volume = 72
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<PersistedDesktopSettings>(encoded)
        assertThat(decoded).isEqualTo(original)
    }
}
