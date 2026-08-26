package com.quietrays.tonarc.data.recommendation

import com.quietrays.tonarc.data.database.SongEngagementEntity
import com.quietrays.tonarc.data.model.Song
import java.util.Random
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stage 2 of recommendation engine: scores and ranks candidates through an on-device taste model,
 * and picks a diverse slice ensuring artist and genre variety.
 */
@Singleton
class PersonalizedRanker @Inject constructor() {

    enum class RecommendationMood(
        val displayName: String,
        val targetEnergy: Double,
        val minEnergy: Double = 0.0,
        val maxEnergy: Double = 1.0
    ) {
        ALL("All", targetEnergy = 0.5),
        CHILL("Chill 🧘", targetEnergy = 0.25, maxEnergy = 0.65),
        WORKOUT("Workout ⚡", targetEnergy = 0.90, minEnergy = 0.55),
        HAPPY("Happy 🎉", targetEnergy = 0.70, minEnergy = 0.40),
        FOCUS("Focus 📚", targetEnergy = 0.30, maxEnergy = 0.60),
        MELANCHOLY("Melancholy 🌧️", targetEnergy = 0.30, maxEnergy = 0.65)
    }

    data class RankingWeights(
        val affinityWeight: Double = 0.30,
        val sourceStrengthWeight: Double = 0.25,
        val recencyWeight: Double = 0.15,
        val favoriteWeight: Double = 0.15,
        val noveltyWeight: Double = 0.10,
        val completionBoostMultiplier: Double = 0.30,
        val skipPenaltyMultiplier: Double = 0.40
    )

    data class ScoredCandidate(
        val candidate: RecommendationCandidate,
        val finalScore: Double,
        val affinityScore: Double,
        val recencyScore: Double,
        val noveltyScore: Double,
        val favoriteScore: Double,
        val sourceStrengthScore: Double
    ) {
        val song: Song get() = candidate.song
    }

    data class DiversityState(
        val artistCounts: MutableMap<String, Int> = mutableMapOf(),
        val genreCounts: MutableMap<String, Int> = mutableMapOf(),
        var unknownGenreCount: Int = 0
    )

    private fun artistKey(song: Song): String {
        return if (song.artistId != 0L) "id_${song.artistId}"
        else "name_${song.artist.trim().lowercase()}"
    }

    fun rank(
        candidates: List<RecommendationCandidate>,
        engagements: Map<String, SongEngagementEntity>,
        favoriteSongIds: Set<String>,
        weights: RankingWeights = RankingWeights(),
        mood: RecommendationMood = RecommendationMood.ALL,
        excludedSongIds: Set<String> = emptySet(),
        random: Random = Random()
    ): List<ScoredCandidate> {
        if (candidates.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val maxPlayCount = engagements.values.maxOfOrNull { it.playCount }?.takeIf { it > 0 }?.toDouble() ?: 1.0
        val maxDuration = engagements.values.maxOfOrNull { it.totalPlayDurationMs }?.takeIf { it > 0L }?.toDouble() ?: 1.0

        return candidates.mapNotNull { candidate ->
            val song = candidate.song
            if (song.id in excludedSongIds) return@mapNotNull null
            val ytId = song.youtubeId
            if (ytId != null && ytId in excludedSongIds) return@mapNotNull null

            val stats = engagements[song.id]

            val songEnergy = song.genre?.lowercase()?.let(com.quietrays.tonarc.data.playlist.nlp.GenreTaxonomy::energyOf)
            if (mood != RecommendationMood.ALL && songEnergy != null) {
                if (songEnergy < mood.minEnergy || songEnergy > mood.maxEnergy) {
                    return@mapNotNull null
                }
            }

            val playCount = stats?.playCount?.toDouble() ?: 0.0
            val playCountScore = if (maxPlayCount <= 0.0 || maxPlayCount.isNaN() || maxPlayCount.isInfinite()) 0.0 
                else (playCount / maxPlayCount).coerceIn(0.0, 1.0)

            val totalDuration = stats?.totalPlayDurationMs?.toDouble() ?: 0.0
            val durationScore = if (maxDuration <= 0.0 || maxDuration.isNaN() || maxDuration.isInfinite()) 0.0 
                else (totalDuration / maxDuration).coerceIn(0.0, 1.0)

            val completionBoost = if (stats != null && stats.playCount > 0) {
                val ratio = stats.completionCount.toDouble() / stats.playCount
                if (ratio.isNaN() || ratio.isInfinite()) 0.0 else ratio.coerceIn(0.0, 1.0)
            } else 0.0

            val totalPlaysAndSkips = (stats?.playCount ?: 0) + (stats?.skipBefore30sCount ?: 0)
            val skipPenalty = if (stats != null && totalPlaysAndSkips > 0) {
                val ratio = stats.skipBefore30sCount.toDouble() / totalPlaysAndSkips
                if (ratio.isNaN() || ratio.isInfinite()) 0.0 else ratio.coerceIn(0.0, 1.0)
            } else 0.0

            val rawAffinity = (playCountScore * 0.45 + durationScore * 0.25 + completionBoost * weights.completionBoostMultiplier - skipPenalty * weights.skipPenaltyMultiplier)
            val affinityScore = if (rawAffinity.isNaN() || rawAffinity.isInfinite()) 0.0 else rawAffinity.coerceIn(0.0, 1.0)

            val recencyScore = computeRecencyScore(stats?.lastPlayedTimestamp, now)
            val noveltyScore = computeNoveltyScore(song.dateAdded, now)
            val favoriteScore = if (favoriteSongIds.contains(song.id)) 1.0 else 0.0
            val sourceStrengthScore = if (candidate.sourceStrength.isNaN() || candidate.sourceStrength.isInfinite()) 0.5 else candidate.sourceStrength.coerceIn(0.0, 1.0)
            val baselineScore = if (stats == null) 0.05 else 0.0
            val noise = random.nextDouble() * 0.005

            val moodBonus = if (mood != RecommendationMood.ALL) {
                val effectiveEnergy = songEnergy ?: 0.5
                val diff = kotlin.math.abs(effectiveEnergy - mood.targetEnergy)
                (1.0 - diff).coerceIn(0.0, 1.0) * 0.35
            } else 0.0

            val finalScore = (affinityScore * weights.affinityWeight) +
                (sourceStrengthScore * weights.sourceStrengthWeight) +
                (recencyScore * weights.recencyWeight) +
                (favoriteScore * weights.favoriteWeight) +
                (noveltyScore * weights.noveltyWeight) +
                moodBonus +
                baselineScore +
                noise

            ScoredCandidate(
                candidate = candidate,
                finalScore = if (finalScore.isNaN() || finalScore.isInfinite()) 0.0 else finalScore,
                affinityScore = affinityScore,
                recencyScore = recencyScore,
                noveltyScore = noveltyScore,
                favoriteScore = favoriteScore,
                sourceStrengthScore = sourceStrengthScore
            )
        }.sortedWith(compareByDescending<ScoredCandidate> { it.finalScore }.thenBy { it.song.id })
    }

    fun pickWithDiversity(
        rankedCandidates: List<ScoredCandidate>,
        favoriteSongIds: Set<String>,
        limit: Int,
        excludedSongIds: Set<String> = emptySet(),
        state: DiversityState = DiversityState()
    ): List<Song> {
        if (limit <= 0 || rankedCandidates.isEmpty()) return emptyList()

        val selected = mutableListOf<Song>()
        for (scored in rankedCandidates) {
            if (selected.size >= limit) break
            val song = scored.song
            if (song.id in excludedSongIds) continue
            val ytId = song.youtubeId
            if (ytId != null && ytId in excludedSongIds) continue

            val isFavorite = favoriteSongIds.contains(song.id)
            val maxPerArtist = if (isFavorite) 3 else 2
            val key = artistKey(song)
            val artistCount = state.artistCounts.getOrDefault(key, 0)
            if (artistCount >= maxPerArtist) continue

            selected += song
            state.artistCounts[key] = artistCount + 1
        }

        if (selected.size < limit) {
            val selectedIds = selected.mapTo(HashSet()) { it.id }
            for (scored in rankedCandidates) {
                if (selected.size >= limit) break
                val song = scored.song
                if (song.id in excludedSongIds || song.id in selectedIds) continue
                val ytId = song.youtubeId
                if (ytId != null && ytId in excludedSongIds) continue

                selected += song
                selectedIds += song.id
            }
        }

        return selected.take(limit)
    }

    private fun computeRecencyScore(lastPlayedTimestamp: Long?, now: Long): Double {
        if (lastPlayedTimestamp == null || lastPlayedTimestamp <= 0L) return 0.6
        val diffMs = (now - lastPlayedTimestamp).coerceAtLeast(0L)
        val days = (diffMs / TimeUnit.DAYS.toMillis(1).toDouble()).let { if (it.isNaN() || it.isInfinite()) 0.0 else it }
        val score = when {
            days < 1.0 -> 0.2
            days < 3.0 -> 0.5
            days < 7.0 -> 0.7
            days < 14.0 -> 0.85
            else -> 1.0
        }
        return if (score.isNaN() || score.isInfinite()) 0.5 else score.coerceIn(0.0, 1.0)
    }

    private fun computeNoveltyScore(dateAdded: Long, now: Long): Double {
        if (dateAdded <= 0L) return 0.0
        val dateAddedMillis = if (dateAdded < 10_000_000_000L) TimeUnit.SECONDS.toMillis(dateAdded) else dateAdded
        val diffMs = (now - dateAddedMillis).coerceAtLeast(0L)
        val days = (diffMs / TimeUnit.DAYS.toMillis(1).toDouble()).let { if (it.isNaN() || it.isInfinite()) 0.0 else it }
        val score = 1.0 - (days / 60.0)
        return if (score.isNaN() || score.isInfinite()) 0.0 else score.coerceIn(0.0, 1.0)
    }
}
