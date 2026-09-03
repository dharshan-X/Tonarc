package com.quietrays.tonarc.data.network.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SpotifyModelsTest {

    @Test
    fun spotifyPrivatePlaylistException_whenNotLoggedIn_hasCorrectPropertiesAndMessage() {
        val playlistId = "37i9dQZF1DXcBWIGoYBM5M"
        val exception = SpotifyPrivatePlaylistException(
            playlistId = playlistId,
            isUserLoggedIn = false
        )

        assertTrue(exception is IOException)
        assertEquals(playlistId, exception.playlistId)
        assertFalse(exception.isUserLoggedIn)
        assertEquals(
            "This Spotify playlist is private or unlisted. Log in with your Spotify account or make the playlist public in Spotify.",
            exception.message
        )
    }

    @Test
    fun spotifyPrivatePlaylistException_whenLoggedIn_hasCorrectPropertiesAndMessage() {
        val playlistId = "37i9dQZF1DXcBWIGoYBM5M"
        val exception = SpotifyPrivatePlaylistException(
            playlistId = playlistId,
            isUserLoggedIn = true
        )

        assertTrue(exception is IOException)
        assertEquals(playlistId, exception.playlistId)
        assertTrue(exception.isUserLoggedIn)
        assertEquals(
            "This playlist is private or unlisted and not accessible by the logged-in Spotify account.",
            exception.message
        )
    }
}
