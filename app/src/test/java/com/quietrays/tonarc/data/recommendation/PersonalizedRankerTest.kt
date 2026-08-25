package com.quietrays.tonarc.data.recommendation

import com.google.common.truth.Truth.assertThat
import com.quietrays.tonarc.data.database.SongEngagementEntity
import com.quietrays.tonarc.data.model.Song
import org.junit.jupiter.api.Test
import java.util.Random

class PersonalizedRankerTest {

    private val ranker = PersonalizedRanker()

    private fun testSong(
        id: String,
        title: String,
        artist: String,
        artistId: Long,
        genre: String? = null
    ): Song = Song(
        id = id,
        title = title,
        artist = artist,
        artistId = artistId,
        album = "Album",
        albumId = 10L,
        path = "path/$id",
        contentUriString = "content://music/$id",
        albumArtUriString = null,
        duration = 180000L,
        mimeType = "audio/mpeg",
        bitrate = 320000,
        sampleRate = 44100,
        genre = genre
    )

    @Test
    fun `rank gives higher score to completed tracks vs frequently skipped tracks`() {
        val songCompleted = testSong(
            id = "song_completed",
            title = "Completed Track",
            artist = "Artist A",
            artistId = 1L
        )

        val songSkipped = testSong(
            id = "song_skipped",
            title = "Skipped Track",
            artist = "Artist B",
            artistId = 2L
        )

        val candidateCompleted = RecommendationCandidate(
            song = songCompleted,
            sourceType = CandidateSourceType.YT_RADIO,
            sourceStrength = 0.8
        )

        val candidateSkipped = RecommendationCandidate(
            song = songSkipped,
            sourceType = CandidateSourceType.YT_RADIO,
            sourceStrength = 0.8
        )

        val engagements = mapOf(
            "song_completed" to SongEngagementEntity(
                songId = "song_completed",
                playCount = 10,
                totalPlayDurationMs = 1800000L,
                completionCount = 9,
                skipBefore30sCount = 0
            ),
            "song_skipped" to SongEngagementEntity(
                songId = "song_skipped",
                playCount = 10,
                totalPlayDurationMs = 1800000L,
                completionCount = 0,
                skipBefore30sCount = 8
            )
        )

        val ranked = ranker.rank(
            candidates = listOf(candidateCompleted, candidateSkipped),
            engagements = engagements,
            favoriteSongIds = emptySet(),
            random = Random(42)
        )

        assertThat(ranked).hasSize(2)
        assertThat(ranked[0].song.id).isEqualTo("song_completed")
        assertThat(ranked[0].finalScore).isGreaterThan(ranked[1].finalScore)
    }

    @Test
    fun `rank filters candidates based on selected mood energy bands`() {
        val chillSong = testSong(
            id = "chill_1",
            title = "Chill Lofi",
            artist = "Lofi Artist",
            artistId = 1L,
            genre = "lo-fi"
        )
        val metalSong = testSong(
            id = "metal_1",
            title = "Heavy Metal",
            artist = "Metal Artist",
            artistId = 2L,
            genre = "metal"
        )

        val candidates = listOf(
            RecommendationCandidate(chillSong, CandidateSourceType.GENRE_EXPANSION),
            RecommendationCandidate(metalSong, CandidateSourceType.GENRE_EXPANSION)
        )

        val chillRanked = ranker.rank(
            candidates = candidates,
            engagements = emptyMap(),
            favoriteSongIds = emptySet(),
            mood = PersonalizedRanker.RecommendationMood.CHILL
        )
        assertThat(chillRanked.map { it.song.id }).containsExactly("chill_1")

        val workoutRanked = ranker.rank(
            candidates = candidates,
            engagements = emptyMap(),
            favoriteSongIds = emptySet(),
            mood = PersonalizedRanker.RecommendationMood.WORKOUT
        )
        assertThat(workoutRanked.map { it.song.id }).containsExactly("metal_1")
    }

    @Test
    fun `pickWithDiversity respects max artist limits`() {
        val songs = (1..6).map { i ->
            testSong(
                id = "song_$i",
                title = "Title $i",
                artist = "Same Artist",
                artistId = 99L
            )
        }

        val candidates = songs.map { song ->
            PersonalizedRanker.ScoredCandidate(
                candidate = RecommendationCandidate(song, CandidateSourceType.YT_RADIO),
                finalScore = 1.0,
                affinityScore = 1.0,
                recencyScore = 1.0,
                noveltyScore = 1.0,
                favoriteScore = 0.0,
                sourceStrengthScore = 1.0
            )
        }

        val diversePicks = ranker.pickWithDiversity(candidates, emptySet(), limit = 2)
        assertThat(diversePicks).hasSize(2)
    }

    @Test
    fun `pickWithDiversity distinguishes different artists even when artistId is 0L`() {
        val artists = listOf("Queen", "Daft Punk", "The Beatles", "Taylor Swift", "Radiohead")
        val candidates = artists.mapIndexed { index, artistName ->
            val song = testSong(
                id = "youtube_$index",
                title = "Hit Track $index",
                artist = artistName,
                artistId = 0L
            )
            PersonalizedRanker.ScoredCandidate(
                candidate = RecommendationCandidate(song, CandidateSourceType.YT_RADIO),
                finalScore = 0.9 - (index * 0.05),
                affinityScore = 0.8,
                recencyScore = 0.8,
                noveltyScore = 0.8,
                favoriteScore = 0.0,
                sourceStrengthScore = 0.9
            )
        }

        val diversePicks = ranker.pickWithDiversity(candidates, emptySet(), limit = 5)
        assertThat(diversePicks).hasSize(5)
        assertThat(diversePicks.map { it.artist }).containsExactly(
            "Queen", "Daft Punk", "The Beatles", "Taylor Swift", "Radiohead"
        ).inOrder()
    }
}
