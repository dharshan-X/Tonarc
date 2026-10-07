package com.quietrays.tonarc.utils

import kotlin.math.max
import kotlin.math.min

/**
 * Utility for typo-tolerant fuzzy matching and relevance scoring using Damerau-Levenshtein distance.
 */
object FuzzySearchMatcher {

    private const val DEFAULT_MATCH_THRESHOLD = 0.50f
    private val WORD_SPLIT_REGEX = Regex("[\\s\\-_/.,;:'\"()\\[\\]]+")

    /**
     * Calculates the Damerau-Levenshtein distance (supports insertion, deletion, substitution, and transposition).
     * Optimized to use 3 one-dimensional IntArrays instead of a full 2D matrix allocation,
     * reducing memory allocations from O(lenA) objects to O(1).
     */
    fun damerauLevenshteinDistance(s1: String, s2: String): Int {
        if (s1 == s2 || s1.equals(s2, ignoreCase = true)) return 0

        val a = s1.lowercase()
        val b = s2.lowercase()
        val lenA = a.length
        val lenB = b.length

        if (lenA == 0) return lenB
        if (lenB == 0) return lenA

        var prevPrevRow = IntArray(lenB + 1)
        var prevRow = IntArray(lenB + 1) { it }
        var currRow = IntArray(lenB + 1)

        for (i in 1..lenA) {
            currRow[0] = i
            val charA = a[i - 1]
            val prevCharA = if (i > 1) a[i - 2] else '\u0000'

            for (j in 1..lenB) {
                val charB = b[j - 1]
                val cost = if (charA == charB) 0 else 1

                var minCost = min(
                    min(prevRow[j] + 1, currRow[j - 1] + 1),
                    prevRow[j - 1] + cost
                )

                // Transposition check
                if (i > 1 && j > 1 && charA == b[j - 2] && prevCharA == charB) {
                    minCost = min(minCost, prevPrevRow[j - 2] + 1)
                }

                currRow[j] = minCost
            }

            val temp = prevPrevRow
            prevPrevRow = prevRow
            prevRow = currRow
            currRow = temp
        }

        return prevRow[lenB]
    }

    /**
     * Calculates a normalized similarity score between 0.0 (no match) and 1.0 (exact match).
     */
    fun similarity(s1: String, s2: String): Float {
        val a = s1.trim().lowercase()
        val b = s2.trim().lowercase()
        if (a == b) return 1.0f
        if (a.isEmpty() || b.isEmpty()) return 0.0f

        val maxLen = max(a.length, b.length)
        val distance = damerauLevenshteinDistance(a, b)
        return (1.0f - (distance.toFloat() / maxLen)).coerceIn(0.0f, 1.0f)
    }

    /**
     * Scores how well a [candidate] matches a user's [query].
     *
     * @return Score between 0.0 and 1.0, or 0.0 if not considered a match.
     */
    fun scoreMatch(candidate: String, query: String, threshold: Float = DEFAULT_MATCH_THRESHOLD): Float {
        val normalizedCandidate = candidate.trim().lowercase()
        val normalizedQuery = query.trim().lowercase()

        if (normalizedCandidate.isEmpty() || normalizedQuery.isEmpty()) return 0.0f
        if (normalizedCandidate == normalizedQuery) return 1.0f

        // Exact substring check
        if (normalizedCandidate.contains(normalizedQuery)) {
            return if (normalizedCandidate.startsWith(normalizedQuery)) 0.95f else 0.85f
        }

        val candidateWords = WORD_SPLIT_REGEX.split(normalizedCandidate).filter { it.isNotBlank() }
        val queryWords = WORD_SPLIT_REGEX.split(normalizedQuery).filter { it.isNotBlank() }

        if (queryWords.isEmpty() || candidateWords.isEmpty()) return 0.0f

        var totalScore = 0.0f

        for (qWord in queryWords) {
            var bestWordScore = 0.0f

            for (cWord in candidateWords) {
                when {
                    cWord == qWord -> {
                        bestWordScore = max(bestWordScore, 1.0f)
                    }
                    cWord.startsWith(qWord) -> {
                        bestWordScore = max(bestWordScore, 0.90f)
                    }
                    cWord.contains(qWord) -> {
                        bestWordScore = max(bestWordScore, 0.75f)
                    }
                    qWord.length >= 3 -> {
                        val dist = damerauLevenshteinDistance(cWord, qWord)
                        val maxAllowedDist = if (qWord.length <= 4) 1 else 2
                        if (dist <= maxAllowedDist) {
                            val maxLen = max(cWord.length, qWord.length)
                            val wordSim = (1.0f - (dist.toFloat() / maxLen)) * 0.85f
                            bestWordScore = max(bestWordScore, wordSim)
                        }
                    }
                }
            }
            totalScore += bestWordScore
        }

        val avgScore = totalScore / queryWords.size
        return if (avgScore >= threshold) avgScore else 0.0f
    }

    /**
     * Checks if a [candidate] is considered a match for [query].
     */
    fun isMatch(candidate: String, query: String, threshold: Float = DEFAULT_MATCH_THRESHOLD): Boolean {
        return scoreMatch(candidate, query, threshold) > 0.0f
    }
}
