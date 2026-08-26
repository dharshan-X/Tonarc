package com.quietrays.tonarc.data.recommendation

import com.google.common.truth.Truth.assertThat
import com.quietrays.tonarc.data.database.SongEngagementEntity
import org.junit.jupiter.api.Test

class AdaptiveWeightTunerTest {

    private val tuner = AdaptiveWeightTuner()

    @Test
    fun `computeTunedWeights increases skip penalty when skip rate is high`() {
        val highSkipEngagements = listOf(
            SongEngagementEntity(songId = "1", playCount = 2, skipBefore30sCount = 8),
            SongEngagementEntity(songId = "2", playCount = 1, skipBefore30sCount = 5)
        )

        val baseWeights = PersonalizedRanker.RankingWeights()
        val tuned = tuner.computeTunedWeights(highSkipEngagements, baseWeights)

        assertThat(tuned.skipPenaltyMultiplier).isGreaterThan(baseWeights.skipPenaltyMultiplier)
        assertThat(tuned.recencyWeight).isGreaterThan(baseWeights.recencyWeight)
    }

    @Test
    fun `computeTunedWeights increases completion boost when completion rate is high`() {
        val highCompletionEngagements = listOf(
            SongEngagementEntity(songId = "1", playCount = 10, completionCount = 9, skipBefore30sCount = 0),
            SongEngagementEntity(songId = "2", playCount = 8, completionCount = 7, skipBefore30sCount = 0)
        )

        val baseWeights = PersonalizedRanker.RankingWeights()
        val tuned = tuner.computeTunedWeights(highCompletionEngagements, baseWeights)

        assertThat(tuned.completionBoostMultiplier).isGreaterThan(baseWeights.completionBoostMultiplier)
        assertThat(tuned.affinityWeight).isGreaterThan(baseWeights.affinityWeight)
    }
}
