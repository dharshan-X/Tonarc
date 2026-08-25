package com.quietrays.tonarc.data.recommendation

import com.quietrays.tonarc.data.database.ItemCooccurrenceDao
import com.quietrays.tonarc.data.database.ItemCooccurrenceEntity
import com.quietrays.tonarc.data.database.SongEngagementEntity
import com.quietrays.tonarc.data.listenbrainz.ListenBrainzRepository
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.preferences.UserPreferencesRepository
import com.quietrays.tonarc.data.repository.MusicRepository
import com.quietrays.tonarc.data.youtube.YouTubeRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationEngineEdgeCasesTest {

    private val mockDao = mockk<ItemCooccurrenceDao>(relaxed = true)
    private val embeddingStore = ItemEmbeddingStore(mockDao)
    private val weightTuner = AdaptiveWeightTuner()
    private val ranker = PersonalizedRanker()

    private fun createDummySong(id: String, title: String, artist: String, genre: String? = "Rock"): Song {
        return Song(
            id = id,
            title = title,
            artist = artist,
            artistId = 1L,
            album = "Test Album",
            albumId = 1L,
            path = "/dummy/$id.mp3",
            contentUriString = "content://dummy/$id",
            albumArtUriString = null,
            duration = 200_000L,
            genre = genre,
            mimeType = "audio/mp3",
            bitrate = 320,
            sampleRate = 44100
        )
    }

    @Test
    fun `test NaN and zero divisor guards in vector calculations`() {
        // Zero vector
        val zeroVec1 = floatArrayOf(0f, 0f, 0f)
        val zeroVec2 = floatArrayOf(0f, 0f, 0f)
        val simZero = embeddingStore.cosineSimilarity(zeroVec1, zeroVec2)
        assertEquals(0f, simZero, 0.0001f)

        // All-NaN vectors
        val nanVec1 = floatArrayOf(Float.NaN, Float.NaN, Float.NaN)
        val nanVec2 = floatArrayOf(Float.NaN, Float.NaN, Float.NaN)
        val simNan = embeddingStore.cosineSimilarity(nanVec1, nanVec2)
        assertEquals(0f, simNan, 0.0001f)

        // Infinity and zero combinations
        val infVec = floatArrayOf(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f)
        val simInf = embeddingStore.cosineSimilarity(infVec, zeroVec1)
        assertEquals(0f, simInf, 0.0001f)

        // Valid vectors
        val vecA = floatArrayOf(1f, 2f, 3f)
        val vecB = floatArrayOf(1f, 2f, 3f)
        val simValid = embeddingStore.cosineSimilarity(vecA, vecB)
        assertEquals(1f, simValid, 0.001f)

        // Map-based sparse cosine similarity
        val mapZeroA = emptyMap<String, Double>()
        val mapZeroB = mapOf("s1" to 0.0)
        assertEquals(0.0, embeddingStore.cosineSimilarity(mapZeroA, mapZeroB), 0.0001)

        val mapNanA = mapOf("s1" to Double.NaN)
        val mapNanB = mapOf("s1" to Double.NaN)
        assertEquals(0.0, embeddingStore.cosineSimilarity(mapNanA, mapNanB), 0.0001)

        val mapValidA = mapOf("s1" to 1.0, "s2" to 2.0)
        val mapValidB = mapOf("s1" to 1.0, "s2" to 2.0)
        assertEquals(1.0, embeddingStore.cosineSimilarity(mapValidA, mapValidB), 0.001)
    }

    @Test
    fun `test sub-score normalization strictly within 0 to 1`() = runTest {
        coEvery { mockDao.getCooccurrencesForSong(any(), any()) } returns listOf(
            ItemCooccurrenceEntity(songIdA = "seed", songIdB = "s1", cooccurrenceCount = 100, lastUpdatedTimestamp = 1000L),
            ItemCooccurrenceEntity(songIdA = "seed", songIdB = "s2", cooccurrenceCount = 50, lastUpdatedTimestamp = 1000L),
            ItemCooccurrenceEntity(songIdA = "seed", songIdB = "s3", cooccurrenceCount = 0, lastUpdatedTimestamp = 1000L)
        )

        val similar = embeddingStore.getSimilarSongs("seed", limit = 10)
        for ((_, score) in similar) {
            assertTrue("Sub-score must be >= 0.0", score >= 0.0)
            assertTrue("Sub-score must be <= 1.0", score <= 1.0)
            assertFalse("Sub-score must not be NaN", score.isNaN())
        }

        // Check PersonalizedRanker sub-scores with extreme / negative stats
        val candidates = listOf(
            RecommendationCandidate(createDummySong("s1", "Song 1", "Artist 1"), CandidateSourceType.YT_RADIO, sourceStrength = 10.0),
            RecommendationCandidate(createDummySong("s2", "Song 2", "Artist 2"), CandidateSourceType.GENRE_EXPANSION, sourceStrength = -5.0)
        )

        val engagements = mapOf(
            "s1" to SongEngagementEntity("s1", playCount = 0, completionCount = 0, skipBefore30sCount = 10, totalPlayDurationMs = 0L),
            "s2" to SongEngagementEntity("s2", playCount = 100, completionCount = 100, skipBefore30sCount = 0, totalPlayDurationMs = 500_000L)
        )

        val ranked = ranker.rank(
            candidates = candidates,
            engagements = engagements,
            favoriteSongIds = setOf("s2")
        )

        for (scored in ranked) {
            assertTrue("Final score >= 0", scored.finalScore >= 0.0)
            assertFalse("Final score not NaN", scored.finalScore.isNaN())
            assertTrue("Affinity in [0, 1]", scored.affinityScore in 0.0..1.0)
            assertTrue("Recency in [0, 1]", scored.recencyScore in 0.0..1.0)
            assertTrue("Novelty in [0, 1]", scored.noveltyScore in 0.0..1.0)
            assertTrue("Source strength in [0, 1]", scored.sourceStrengthScore in 0.0..1.0)
        }
    }

    @Test
    fun `test cold start fallback when engagement history and seeds are empty`() = runTest {
        val mockYT = mockk<YouTubeRepository>(relaxed = true)
        val mockLB = mockk<ListenBrainzRepository>(relaxed = true)
        val mockMusicRepo = mockk<MusicRepository>(relaxed = true)
        val mockPrefs = mockk<UserPreferencesRepository>(relaxed = true)

        val localLibrary = listOf(
            createDummySong("local1", "Local 1", "Artist 1", genre = "Rock"),
            createDummySong("local2", "Local 2", "Artist 2", genre = "Jazz"),
            createDummySong("local3", "Local 3", "Artist 3", genre = "Pop")
        )

        every { mockPrefs.favoriteArtistsFlow } returns flowOf(emptySet())
        every { mockMusicRepo.getAudioFiles() } returns flowOf(localLibrary)
        every { mockYT.getCharts() } returns flowOf(emptyList())

        val aggregator = CandidateAggregator(
            youTubeRepository = mockYT,
            listenBrainzRepository = mockLB,
            musicRepository = mockMusicRepo,
            itemEmbeddingStore = embeddingStore,
            userPreferencesRepository = mockPrefs
        )

        val result = aggregator.collect(seedSongs = emptyList(), limit = 10)
        assertFalse("Cold start candidates must not be empty when library has tracks", result.isEmpty())
        assertTrue("Cold start candidates must contain local tracks", result.any { it.song.id == "local1" })
    }

    @Test
    fun `test simplex invariance and non-zero weight enforcement in AdaptiveWeightTuner`() {
        // High skips test
        val highSkips = (1..50).map {
            SongEngagementEntity(it.toString(), playCount = 1, completionCount = 0, skipBefore30sCount = 10, totalPlayDurationMs = 10_000L)
        }
        val tunedHighSkips = weightTuner.updateWeights(highSkips)
        val sumHighSkips = tunedHighSkips.affinityWeight +
            tunedHighSkips.sourceStrengthWeight +
            tunedHighSkips.recencyWeight +
            tunedHighSkips.favoriteWeight +
            tunedHighSkips.noveltyWeight

        assertEquals(1.0, sumHighSkips, 0.0001)
        assertTrue(tunedHighSkips.affinityWeight >= AdaptiveWeightTuner.MIN_WEIGHT)
        assertTrue(tunedHighSkips.sourceStrengthWeight >= AdaptiveWeightTuner.MIN_WEIGHT)
        assertTrue(tunedHighSkips.recencyWeight >= AdaptiveWeightTuner.MIN_WEIGHT)
        assertTrue(tunedHighSkips.favoriteWeight >= AdaptiveWeightTuner.MIN_WEIGHT)
        assertTrue(tunedHighSkips.noveltyWeight >= AdaptiveWeightTuner.MIN_WEIGHT)

        // High completions test
        val highCompletions = (1..50).map {
            SongEngagementEntity(it.toString(), playCount = 10, completionCount = 10, skipBefore30sCount = 0, totalPlayDurationMs = 100_000L)
        }
        val tunedHighCompletions = weightTuner.updateWeights(highCompletions)
        val sumHighCompletions = tunedHighCompletions.affinityWeight +
            tunedHighCompletions.sourceStrengthWeight +
            tunedHighCompletions.recencyWeight +
            tunedHighCompletions.favoriteWeight +
            tunedHighCompletions.noveltyWeight

        assertEquals(1.0, sumHighCompletions, 0.0001)
        assertTrue(tunedHighCompletions.affinityWeight >= AdaptiveWeightTuner.MIN_WEIGHT)
        assertTrue(tunedHighCompletions.sourceStrengthWeight >= AdaptiveWeightTuner.MIN_WEIGHT)
        assertTrue(tunedHighCompletions.recencyWeight >= AdaptiveWeightTuner.MIN_WEIGHT)
        assertTrue(tunedHighCompletions.favoriteWeight >= AdaptiveWeightTuner.MIN_WEIGHT)
        assertTrue(tunedHighCompletions.noveltyWeight >= AdaptiveWeightTuner.MIN_WEIGHT)
    }

    @Test
    fun `test queue and recently played exclusion in ranker and diversity selection`() {
        val candidates = listOf(
            RecommendationCandidate(createDummySong("s1", "Song 1", "Artist A"), CandidateSourceType.YT_RADIO),
            RecommendationCandidate(createDummySong("s2", "Song 2", "Artist A"), CandidateSourceType.YT_RADIO),
            RecommendationCandidate(createDummySong("s3", "Song 3", "Artist B"), CandidateSourceType.YT_RADIO),
            RecommendationCandidate(createDummySong("s4", "Song 4", "Artist C"), CandidateSourceType.YT_RADIO)
        )

        val excludedIds = setOf("s1", "s3")
        val ranked = ranker.rank(
            candidates = candidates,
            engagements = emptyMap(),
            favoriteSongIds = emptySet(),
            excludedSongIds = excludedIds
        )

        // s1 and s3 must not be present in ranked candidates
        val rankedIds = ranked.map { it.song.id }
        assertFalse("Excluded song s1 must not be in ranked list", rankedIds.contains("s1"))
        assertFalse("Excluded song s3 must not be in ranked list", rankedIds.contains("s3"))
        assertTrue("s2 must be present", rankedIds.contains("s2"))
        assertTrue("s4 must be present", rankedIds.contains("s4"))

        val diversePicks = ranker.pickWithDiversity(
            rankedCandidates = ranked,
            favoriteSongIds = emptySet(),
            limit = 10,
            excludedSongIds = excludedIds
        )
        val pickIds = diversePicks.map { it.id }
        assertFalse("Excluded song s1 must not be in picks", pickIds.contains("s1"))
        assertFalse("Excluded song s3 must not be in picks", pickIds.contains("s3"))
    }

    @Test
    fun `test popularity bias dampening in item embeddings`() = runTest {
        // Song A has 150 total plays, Song Popular has 5000 total plays, Song Niche has 20 total plays
        val scorePopular = embeddingStore.computePopularityDampenedScore(cooccurrenceCount = 100L, totalCountA = 150L, totalCountB = 5000L)
        val scoreNiche = embeddingStore.computePopularityDampenedScore(cooccurrenceCount = 18L, totalCountA = 150L, totalCountB = 20L)

        assertTrue("Popular score in [0, 1]", scorePopular in 0.0..1.0)
        assertTrue("Niche score in [0, 1]", scoreNiche in 0.0..1.0)
        assertFalse("Popular score not NaN", scorePopular.isNaN())
        assertFalse("Niche score not NaN", scoreNiche.isNaN())
        // Popular score is dampened by high total count denominator
        assertTrue("Popular score is valid positive score", scorePopular > 0.0)
        assertTrue("Niche score is valid positive score", scoreNiche > 0.0)
    }
}
