package com.quietrays.tonarc.presentation.viewmodel

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SpotifyShareIntentExtractionTest {

    private fun extractSpotifyPlaylistUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val spotifyWebRegex = Regex("""https?://[^\s]*spotify\.com/(?:intl-[a-zA-Z0-9_-]+/)?(?:embed/)?playlist/[a-zA-Z0-9]+(?:\?[^\s]*)?""")
        val spotifyUriRegex = Regex("""spotify:playlist:[a-zA-Z0-9]+(?:\?[^\s]*)?""")
        val webMatch = spotifyWebRegex.find(text)
        if (webMatch != null) return webMatch.value
        val uriMatch = spotifyUriRegex.find(text)
        if (uriMatch != null) return uriMatch.value
        if (text.contains("spotify.com/playlist") || text.contains("spotify:playlist:")) {
            return text.trim()
        }
        return null
    }

    @Test
    fun `extractSpotifyPlaylistUrl parses standard open spotify url`() {
        val input = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"
        val result = extractSpotifyPlaylistUrl(input)
        assertThat(result).isEqualTo("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M")
    }

    @Test
    fun `extractSpotifyPlaylistUrl parses open spotify url with share text and query parameters`() {
        val input = "Check out this playlist on Spotify: https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=12345abcde and enjoy!"
        val result = extractSpotifyPlaylistUrl(input)
        assertThat(result).isEqualTo("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=12345abcde")
    }

    @Test
    fun `extractSpotifyPlaylistUrl parses localized open spotify url`() {
        val input = "https://open.spotify.com/intl-es/playlist/37i9dQZF1DXcBWIGoYBM5M"
        val result = extractSpotifyPlaylistUrl(input)
        assertThat(result).isEqualTo("https://open.spotify.com/intl-es/playlist/37i9dQZF1DXcBWIGoYBM5M")
    }

    @Test
    fun `extractSpotifyPlaylistUrl parses spotify uri`() {
        val input = "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M"
        val result = extractSpotifyPlaylistUrl(input)
        assertThat(result).isEqualTo("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M")
    }

    @Test
    fun `extractSpotifyPlaylistUrl returns null for non-spotify text`() {
        assertThat(extractSpotifyPlaylistUrl("https://music.youtube.com/playlist?list=PL123")).isNull()
        assertThat(extractSpotifyPlaylistUrl("Random message with no link")).isNull()
        assertThat(extractSpotifyPlaylistUrl(null)).isNull()
        assertThat(extractSpotifyPlaylistUrl("")).isNull()
    }
}
