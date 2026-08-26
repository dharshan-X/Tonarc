package com.quietrays.tonarc.data.recommendation

import com.quietrays.tonarc.data.database.SongEngagementEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stage 3 of recommendation engine: on-device self-tuning module.
 * Evaluates user engagement telemetry (skip vs completion ratios) to dynamically adjust
 * ranking weights without transmitting any user data to external servers.
 */
@Singleton
class AdaptiveWeightTuner @Inject constructor() {

    companion object {
        const val MIN_WEIGHT = 0.05
    }

    /**
     * Updates and normalizes weights according to Simplex Invariance (\sum w_i = 1.0)
     * and strictly enforces that no weight drops to 0.0 (minimum floor MIN_WEIGHT = 0.05).
     */
    fun updateWeights(
        engagements: Collection<SongEngagementEntity>,
        baseWeights: PersonalizedRanker.RankingWeights = PersonalizedRanker.RankingWeights()
    ): PersonalizedRanker.RankingWeights {
        return computeTunedWeights(engagements, baseWeights)
    }

    /**
     * Analyzes engagement history and computes adaptive ranking weights projected onto the simplex.
     */
    fun computeTunedWeights(
        engagements: Collection<SongEngagementEntity>,
        baseWeights: PersonalizedRanker.RankingWeights = PersonalizedRanker.RankingWeights()
    ): PersonalizedRanker.RankingWeights {
        val rawWeights = if (engagements.isEmpty()) {
            baseWeights
        } else {
            val totalPlays = engagements.sumOf { it.playCount }
            val totalSkips = engagements.sumOf { it.skipBefore30sCount }
            val totalCompletions = engagements.sumOf { it.completionCount }

            val totalInteractions = engagements.sumOf { maxOf(it.playCount, it.completionCount + it.skipBefore30sCount) }.coerceAtLeast(1)
            val skipRate = (totalSkips.toDouble() / totalInteractions).coerceIn(0.0, 1.0)
            val completionRate = (totalCompletions.toDouble() / totalInteractions).coerceIn(0.0, 1.0)

            var affinityWeight = baseWeights.affinityWeight
            var sourceStrengthWeight = baseWeights.sourceStrengthWeight
            var recencyWeight = baseWeights.recencyWeight
            var favoriteWeight = baseWeights.favoriteWeight
            var noveltyWeight = baseWeights.noveltyWeight
            var skipPenaltyMultiplier = baseWeights.skipPenaltyMultiplier
            var completionBoostMultiplier = baseWeights.completionBoostMultiplier

            // High skip rate (> 30%): increase skip penalty and diversify away from repeated plays
            if (skipRate > 0.30) {
                val penaltyFactor = (skipRate - 0.30) * 1.5
                skipPenaltyMultiplier = (baseWeights.skipPenaltyMultiplier + penaltyFactor).coerceAtMost(0.80)
                recencyWeight = (baseWeights.recencyWeight + 0.05).coerceAtMost(0.30)
                sourceStrengthWeight = (baseWeights.sourceStrengthWeight + 0.05).coerceAtMost(0.35)
                noveltyWeight = (baseWeights.noveltyWeight + 0.05).coerceAtMost(0.25)
                affinityWeight = (baseWeights.affinityWeight - 0.10).coerceAtLeast(MIN_WEIGHT)
            }

            // High completion rate (> 60%): user is satisfied with current taste graph, exploit affinity
            if (completionRate > 0.60) {
                val boostFactor = (completionRate - 0.60) * 1.0
                completionBoostMultiplier = (baseWeights.completionBoostMultiplier + boostFactor).coerceAtMost(0.60)
                affinityWeight = (affinityWeight + 0.05).coerceAtMost(0.45)
                favoriteWeight = (favoriteWeight + 0.05).coerceAtMost(0.30)
            }

            baseWeights.copy(
                affinityWeight = affinityWeight,
                sourceStrengthWeight = sourceStrengthWeight,
                recencyWeight = recencyWeight,
                favoriteWeight = favoriteWeight,
                noveltyWeight = noveltyWeight,
                skipPenaltyMultiplier = skipPenaltyMultiplier.coerceIn(0.0, 1.0),
                completionBoostMultiplier = completionBoostMultiplier.coerceIn(0.0, 1.0)
            )
        }

        return projectOntoSimplex(rawWeights)
    }

    /**
     * Projects candidate feature weights onto the probability simplex with a minimum floor
     * so that each feature weight w_i >= MIN_WEIGHT > 0.0 and \sum_{i=1}^5 w_i == 1.0.
     */
    fun projectOntoSimplex(weights: PersonalizedRanker.RankingWeights): PersonalizedRanker.RankingWeights {
        val clampedAffinity = if (weights.affinityWeight.isNaN() || weights.affinityWeight.isInfinite()) MIN_WEIGHT else weights.affinityWeight.coerceAtLeast(MIN_WEIGHT)
        val clampedSource = if (weights.sourceStrengthWeight.isNaN() || weights.sourceStrengthWeight.isInfinite()) MIN_WEIGHT else weights.sourceStrengthWeight.coerceAtLeast(MIN_WEIGHT)
        val clampedRecency = if (weights.recencyWeight.isNaN() || weights.recencyWeight.isInfinite()) MIN_WEIGHT else weights.recencyWeight.coerceAtLeast(MIN_WEIGHT)
        val clampedFavorite = if (weights.favoriteWeight.isNaN() || weights.favoriteWeight.isInfinite()) MIN_WEIGHT else weights.favoriteWeight.coerceAtLeast(MIN_WEIGHT)
        val clampedNovelty = if (weights.noveltyWeight.isNaN() || weights.noveltyWeight.isInfinite()) MIN_WEIGHT else weights.noveltyWeight.coerceAtLeast(MIN_WEIGHT)

        val sum = clampedAffinity + clampedSource + clampedRecency + clampedFavorite + clampedNovelty
        val safeSum = if (sum <= 0.0 || sum.isNaN() || sum.isInfinite()) 1.0 else sum

        return weights.copy(
            affinityWeight = clampedAffinity / safeSum,
            sourceStrengthWeight = clampedSource / safeSum,
            recencyWeight = clampedRecency / safeSum,
            favoriteWeight = clampedFavorite / safeSum,
            noveltyWeight = clampedNovelty / safeSum
        )
    }
}
