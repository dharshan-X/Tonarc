package com.quietrays.tonarc.data.network.spotify

data class SpotifyTrack(
    val id: String,
    val title: String,
    val artist: String,
    val artists: List<String> = listOf(artist),
    val album: String? = null,
    val durationMs: Long = 0L,
    val coverUri: String? = null
)

data class SpotifyPlaylist(
    val id: String,
    val title: String,
    val description: String? = null,
    val author: String? = null,
    val coverUri: String? = null,
    val trackCount: Int = 0,
    val tracks: List<SpotifyTrack> = emptyList()
)
