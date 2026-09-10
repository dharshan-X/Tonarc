package com.quietrays.tonarc.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistHeroHeaderTest {

    @Test
    fun resolvePlaylistBadgeText_returnsExpectedBadges() {
        assertEquals("Folder", resolvePlaylistBadgeText(source = "LOCAL", isFolder = true, isSmart = false))
        assertEquals("Smart Mix", resolvePlaylistBadgeText(source = "LOCAL", isFolder = false, isSmart = true))
        assertEquals("YouTube Music", resolvePlaylistBadgeText(source = "YOUTUBE", isFolder = false, isSmart = false))
        assertEquals("Spotify", resolvePlaylistBadgeText(source = "SPOTIFY", isFolder = false, isSmart = false))
        assertNull(resolvePlaylistBadgeText(source = "LOCAL", isFolder = false, isSmart = false))
    }
}
