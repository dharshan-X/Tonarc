package com.quietrays.tonarc.data.recommendation

import com.quietrays.tonarc.data.model.Song

/**
 * The source pool from which a recommendation candidate was generated.
 */
enum class CandidateSourceType {
    YT_RADIO,
    LB_SIMILAR_ARTIST,
    LIBRARY_COOCCURRENCE,
    GENRE_EXPANSION
}

/**
 * Represents a candidate track collected during the generation stage of the recommendation pipeline.
 */
data class RecommendationCandidate(
    val song: Song,
    val sourceType: CandidateSourceType,
    val sourceStrength: Double = 1.0,
    val seedSongId: String? = null
)
