package com.quietrays.tonarc.data.database

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class YouTubeSongEntityTest {

    @Test
    fun `toSong correctly populates youtubeId and youtube URI scheme`() {
        val entity = YouTubeSongEntity(
            id = "dQw4w9WgXcQ",
            videoId = "dQw4w9WgXcQ",
            playlistId = "__library__",
            title = "Never Gonna Give You Up",
            artist = "Rick Astley",
            album = "Whenever You Need Somebody",
            duration = 213_000L,
            thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            year = 1987,
            dateAdded = 1700000000L
        )

        val song = entity.toSong()

        assertThat(song.id).isEqualTo("youtube_dQw4w9WgXcQ")
        assertThat(song.youtubeId).isEqualTo("dQw4w9WgXcQ")
        assertThat(song.contentUriString).isEqualTo("youtube://dQw4w9WgXcQ")
        assertThat(song.title).isEqualTo("Never Gonna Give You Up")
        assertThat(song.artist).isEqualTo("Rick Astley")
        assertThat(song.album).isEqualTo("Whenever You Need Somebody")
        assertThat(song.duration).isEqualTo(213_000L)
    }
}
