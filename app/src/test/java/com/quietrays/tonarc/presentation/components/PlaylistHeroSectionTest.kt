package com.quietrays.tonarc.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistHeroSectionTest {

    @Test
    fun resolvePlaylistTagBadge_returnsExpectedTag() {
        assertEquals("Folder", resolvePlaylistTagBadge(source = "LOCAL", isFolder = true, isSmart = false))
        assertEquals("Smart Mix", resolvePlaylistTagBadge(source = "LOCAL", isFolder = false, isSmart = true))
        assertEquals("YouTube Music", resolvePlaylistTagBadge(source = "YOUTUBE", isFolder = false, isSmart = false))
        assertEquals("Spotify", resolvePlaylistTagBadge(source = "SPOTIFY", isFolder = false, isSmart = false))
        assertEquals("Tonarc Playlist", resolvePlaylistTagBadge(source = "LOCAL", isFolder = false, isSmart = false))
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
}
