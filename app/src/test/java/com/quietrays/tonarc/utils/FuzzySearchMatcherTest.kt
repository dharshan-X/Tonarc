package com.quietrays.tonarc.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzySearchMatcherTest {

    @Test
    fun testExactMatch() {
        val score = FuzzySearchMatcher.scoreMatch("Adele - Easy On Me", "Adele")
        assertTrue("Expected score >= 0.85 for exact substring", score >= 0.85f)
    }

    @Test
    fun testTypoTolerance() {
        // "adlee" vs "Adele" (transposition typo -> dist 1 with Damerau-Levenshtein)
        val score = FuzzySearchMatcher.scoreMatch("Adele", "adlee")
        assertTrue("Expected typo match for 'adlee' vs 'Adele', got $score", score >= 0.5f)
        assertTrue(FuzzySearchMatcher.isMatch("Adele", "adlee"))

        // "starboyy" vs "Starboy"
        assertTrue(FuzzySearchMatcher.isMatch("Starboy - The Weeknd", "starboyy"))

        // "blinding light" vs "Blinding Lights"
        assertTrue(FuzzySearchMatcher.isMatch("Blinding Lights", "blinding light"))
    }

    @Test
    fun testNonMatch() {
        val score = FuzzySearchMatcher.scoreMatch("Beethoven Symphony No. 5", "Eminem")
        assertEquals(0.0f, score, 0.001f)
        assertFalse(FuzzySearchMatcher.isMatch("Beethoven Symphony No. 5", "Eminem"))
    }

    @Test
    fun testDamerauLevenshteinDistance() {
        assertEquals(0, FuzzySearchMatcher.damerauLevenshteinDistance("kitten", "kitten"))
        assertEquals(1, FuzzySearchMatcher.damerauLevenshteinDistance("kitten", "sitten"))
        assertEquals(1, FuzzySearchMatcher.damerauLevenshteinDistance("adele", "adlee"))
        assertEquals(3, FuzzySearchMatcher.damerauLevenshteinDistance("kitten", "sitting"))
    }
}
