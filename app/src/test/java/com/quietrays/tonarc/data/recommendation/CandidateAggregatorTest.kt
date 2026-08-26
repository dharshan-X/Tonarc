package com.quietrays.tonarc.data.recommendation

import com.google.common.truth.Truth.assertThat
import com.quietrays.tonarc.data.listenbrainz.ListenBrainzRepository
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.repository.MusicRepository
import com.quietrays.tonarc.data.youtube.YouTubeRepository
import io.mockk.mockk
import org.junit.jupiter.api.Test

class CandidateAggregatorTest {

    private fun testSong(id: String, title: String, artist: String): Song = Song(
        id = id,
        title = title,
        artist = artist,
        artistId = 10L,
        album = "Album",
        albumId = 10L,
        path = "path/$id",
        contentUriString = "content://music/$id",
        albumArtUriString = null,
        duration = 180000L,
        mimeType = "audio/mpeg",
        bitrate = 320000,
        sampleRate = 44100
    )

    @Test
    fun `deduplicateCandidates retains higher source strength candidate on duplicate`() {
        val song1 = testSong("1", "Track A", "Artist A")

        val candidateLow = RecommendationCandidate(
            song = song1,
            sourceType = CandidateSourceType.GENRE_EXPANSION,
            sourceStrength = 0.5
        )

        val candidateHigh = RecommendationCandidate(
            song = song1,
            sourceType = CandidateSourceType.YT_RADIO,
            sourceStrength = 0.9
        )

        val aggregator = CandidateAggregator(
            youTubeRepository = mockk<YouTubeRepository>(relaxed = true),
            listenBrainzRepository = mockk<ListenBrainzRepository>(relaxed = true),
            musicRepository = mockk<MusicRepository>(relaxed = true),
            itemEmbeddingStore = mockk<ItemEmbeddingStore>(relaxed = true),
            userPreferencesRepository = mockk(relaxed = true)
        )

        val deduplicated = aggregator.deduplicateCandidates(listOf(candidateLow, candidateHigh))
        assertThat(deduplicated).hasSize(1)
        assertThat(deduplicated.first().sourceType).isEqualTo(CandidateSourceType.YT_RADIO)
        assertThat(deduplicated.first().sourceStrength).isEqualTo(0.9)
    }

    @Test
    fun `deduplicateCandidates normalizes youtube IDs across uri prefixes`() {
        val song1 = testSong("youtube_abc123", "Track X", "Artist X").copy(youtubeId = "abc123")
        val song2 = testSong("custom_id", "Track X", "Artist X").copy(contentUriString = "youtube://abc123")

        val candidate1 = RecommendationCandidate(
            song = song1,
            sourceType = CandidateSourceType.YT_RADIO,
            sourceStrength = 0.7
        )

        val candidate2 = RecommendationCandidate(
            song = song2,
            sourceType = CandidateSourceType.YT_RADIO,
            sourceStrength = 0.95
        )

        val aggregator = CandidateAggregator(
            youTubeRepository = mockk<YouTubeRepository>(relaxed = true),
            listenBrainzRepository = mockk<ListenBrainzRepository>(relaxed = true),
            musicRepository = mockk<MusicRepository>(relaxed = true),
            itemEmbeddingStore = mockk<ItemEmbeddingStore>(relaxed = true),
            userPreferencesRepository = mockk(relaxed = true)
        )

        val deduplicated = aggregator.deduplicateCandidates(listOf(candidate1, candidate2))
        assertThat(deduplicated).hasSize(1)
        assertThat(deduplicated.first().sourceStrength).isEqualTo(0.95)
    }
}
