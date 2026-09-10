package com.quietrays.tonarc.presentation.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlaylistSongTileTest {

    @Test
    fun resolvePastelBadgePalette_returnsExpectedPaletteByIndex() {
        val palette0 = resolvePastelBadgePalette(0)
        assertEquals(Color(0xFF7FC4FD), palette0.background)
        assertEquals(Color(0xFF0D47A1), palette0.iconTint)

        val palette1 = resolvePastelBadgePalette(1)
        assertEquals(Color(0xFFDCE775), palette1.background)
        assertEquals(Color(0xFF556B2F), palette1.iconTint)

        val palette2 = resolvePastelBadgePalette(2)
        assertEquals(Color(0xFFB39DDB), palette2.background)
        assertEquals(Color(0xFF4A148C), palette2.iconTint)
    }

    @Test
    fun resolvePastelBadgePalette_wrapsAroundForLargeIndices() {
        val palette0 = resolvePastelBadgePalette(0)
        val palette10 = resolvePastelBadgePalette(10)
        val palette20 = resolvePastelBadgePalette(20)

        assertEquals(palette0.background, palette10.background)
        assertEquals(palette0.iconTint, palette10.iconTint)
        assertEquals(palette0.background, palette20.background)
    }

    @Test
    fun resolvePastelBadgePalette_handlesNegativeIndicesSafely() {
        val palette = resolvePastelBadgePalette(-1)
        assertNotNull(palette.background)
        assertNotNull(palette.iconTint)
    }

    @Test
    fun resolvePlaylistTileShape_returnsExpectedShape() {
        val singleShape = resolvePlaylistTileShape(isFirst = true, isLast = true)
        val firstShape = resolvePlaylistTileShape(isFirst = true, isLast = false)
        val middleShape = resolvePlaylistTileShape(isFirst = false, isLast = false)
        val lastShape = resolvePlaylistTileShape(isFirst = false, isLast = true)

        assertNotNull(singleShape)
        assertNotNull(firstShape)
        assertNotNull(middleShape)
        assertNotNull(lastShape)
    }
}

