package com.quietrays.tonarc.data.network.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeGenreCatalogTest {

    @Test
    fun testCatalogHasGenresAndMoods() {
        assertTrue(YouTubeGenreCatalog.genres.isNotEmpty())
        assertTrue(YouTubeGenreCatalog.moods.isNotEmpty())
        assertEquals(YouTubeGenreCatalog.genres.size + YouTubeGenreCatalog.moods.size, YouTubeGenreCatalog.all.size)
    }

    @Test
    fun testFindGenreOrMoodExactMatch() {
        val pop = YouTubeGenreCatalog.findGenreOrMood("pop")
        assertEquals("pop", pop.id)
        assertEquals("Pop", pop.title)

        val rock = YouTubeGenreCatalog.findGenreOrMood("Rock & Alt")
        assertEquals("rock", rock.id)
    }

    @Test
    fun testFindGenreOrMoodCaseInsensitiveAndPartialMatch() {
        val lofi = YouTubeGenreCatalog.findGenreOrMood("LOFI")
        assertEquals("lofi", lofi.id)

        val workout = YouTubeGenreCatalog.findGenreOrMood("workout")
        assertEquals("workout", workout.id)
        assertEquals("Mood", workout.category)
    }

    @Test
    fun testFindGenreOrMoodFallback() {
        val custom = YouTubeGenreCatalog.findGenreOrMood("synthwave_future")
        assertNotNull(custom)
        assertEquals("synthwave_future", custom.id)
        assertEquals("Synthwave_future", custom.title)
    }
}
