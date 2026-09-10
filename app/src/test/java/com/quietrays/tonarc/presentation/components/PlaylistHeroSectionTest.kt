package com.quietrays.tonarc.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.quietrays.tonarc.data.model.Song

class PlaylistHeroSectionTest {

    private fun createDummySong(id: String, albumArtUri: String?): Song {
        return Song(
            id = id,
            title = "Song $id",
            artist = "Artist $id",
            artistId = 1L,
            album = "Album",
            albumId = 1L,
            path = "/path/$id.mp3",
            contentUriString = "content://music/$id",
            albumArtUriString = albumArtUri,
            duration = 180_000L,
            mimeType = "audio/mp3",
            bitrate = 320,
            sampleRate = 44100
        )
    }

    @Test
    fun resolvePlaylistTagBadge_returnsExpectedTag() {
        assertEquals("Folder", resolvePlaylistTagBadge(source = "LOCAL", isFolder = true, isSmart = false))
        assertEquals("Smart Mix", resolvePlaylistTagBadge(source = "LOCAL", isFolder = false, isSmart = true))
        assertNull(resolvePlaylistTagBadge(source = "YOUTUBE", isFolder = false, isSmart = false))
        assertEquals("Spotify", resolvePlaylistTagBadge(source = "SPOTIFY", isFolder = false, isSmart = false))
        assertNull(resolvePlaylistTagBadge(source = "LOCAL", isFolder = false, isSmart = false))
    }

    @Test
    fun resolvePlaylistSubtitleMeta_formatsCorrectly() {
        val meta = resolvePlaylistSubtitleMeta(songCount = 10, totalDurationText = "41:20", formatTag = "Lossless")
        assertEquals("10 songs • 41:20 • Lossless", meta)
    }

    @Test
    fun resolvePlaylistSubtitleMeta_singleSong_formatsCorrectly() {
        val meta = resolvePlaylistSubtitleMeta(songCount = 1, totalDurationText = "3:45", formatTag = "Lossless")
        assertEquals("1 song • 3:45 • Lossless", meta)
    }

    @Test
    fun extractHeroAlbumArts_usesCoverImageUriWhenPresent() {
        val arts = extractHeroAlbumArts(
            coverImageUri = "https://example.com/cover.jpg",
            songs = emptyList()
        )
        assertEquals(listOf("https://example.com/cover.jpg"), arts)
    }

    @Test
    fun extractHeroAlbumArts_extractsDistinctNonBlankArtsFromSongs() {
        val song1 = createDummySong(id = "1", albumArtUri = "art1")
        val song2 = createDummySong(id = "2", albumArtUri = "art2")
        val song3 = createDummySong(id = "3", albumArtUri = "art1") // duplicate
        val song4 = createDummySong(id = "4", albumArtUri = "") // blank
        val song5 = createDummySong(id = "5", albumArtUri = null) // null
        val song6 = createDummySong(id = "6", albumArtUri = "art3")
        val song7 = createDummySong(id = "7", albumArtUri = "art4")
        val song8 = createDummySong(id = "8", albumArtUri = "art5") // 5th distinct

        val arts = extractHeroAlbumArts(
            coverImageUri = null,
            songs = listOf(song1, song2, song3, song4, song5, song6, song7, song8)
        )
        assertEquals(listOf("art1", "art2", "art3", "art4"), arts)
    }

    @Test
    fun extractHeroAlbumArts_returnsEmptyWhenNoArts() {
        val arts = extractHeroAlbumArts(
            coverImageUri = null,
            songs = emptyList()
        )
        assertEquals(emptyList<String>(), arts)
    }
}
