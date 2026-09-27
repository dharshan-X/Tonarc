package com.quietrays.tonarc.presentation.youtube.dashboard

import com.quietrays.tonarc.MainCoroutineExtension
import com.quietrays.tonarc.data.database.EngagementDao
import com.quietrays.tonarc.data.model.Playlist
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.network.youtube.YouTubeGenre
import com.quietrays.tonarc.data.network.youtube.YouTubeGenreCatalog
import com.quietrays.tonarc.data.network.youtube.YouTubeGenreExploreResult
import com.quietrays.tonarc.data.recommendation.AdaptiveWeightTuner
import com.quietrays.tonarc.data.recommendation.CandidateAggregator
import com.quietrays.tonarc.data.recommendation.PersonalizedRanker
import com.quietrays.tonarc.data.repository.MusicRepository
import com.quietrays.tonarc.data.youtube.YouTubeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainCoroutineExtension::class)
class YouTubeDashboardViewModelGenreTest {

    private val youTubeRepository = mockk<YouTubeRepository>(relaxed = true)
    private val candidateAggregator = mockk<CandidateAggregator>(relaxed = true)
    private val personalizedRanker = mockk<PersonalizedRanker>(relaxed = true)
    private val adaptiveWeightTuner = mockk<AdaptiveWeightTuner>(relaxed = true)
    private val engagementDao = mockk<EngagementDao>(relaxed = true)
    private val musicRepository = mockk<MusicRepository>(relaxed = true)

    private lateinit var viewModel: YouTubeDashboardViewModel

    private fun createSong(id: String, title: String, artist: String): Song {
        return Song(
            id = id,
            title = title,
            artist = artist,
            artistId = 1L,
            album = "Pop Album",
            albumId = 1L,
            path = "youtube://$id",
            contentUriString = "youtube://$id",
            albumArtUriString = null,
            duration = 200000L,
            mimeType = "audio/webm",
            bitrate = 160000,
            sampleRate = 48000
        )
    }

    @BeforeEach
    fun setUp() {
        coEvery { youTubeRepository.getExploreSections() } returns flowOf(emptyList())
        coEvery { engagementDao.getAllEngagements() } returns emptyList()
        coEvery { engagementDao.getTopPlayedSongs(any()) } returns emptyList()
        coEvery { engagementDao.getRecentlyPlayedSongs(any()) } returns emptyList()
        coEvery { musicRepository.getAllSongsOnce() } returns emptyList()

        viewModel = YouTubeDashboardViewModel(
            youTubeRepository = youTubeRepository,
            candidateAggregator = candidateAggregator,
            personalizedRanker = personalizedRanker,
            adaptiveWeightTuner = adaptiveWeightTuner,
            engagementDao = engagementDao,
            musicRepository = musicRepository
        )
    }

    @Test
    fun `availableGenres contains all catalog items`() {
        assertEquals(YouTubeGenreCatalog.all.size, viewModel.availableGenres.size)
        assertTrue(viewModel.availableGenres.any { it.title == "Pop" })
        assertTrue(viewModel.availableGenres.any { it.title == "Rock & Alt" })
    }

    @Test
    fun `selecting genre triggers YouTubeRepository explore and updates state`() = runTest {
        val testGenre = YouTubeGenre(id = "pop", title = "Pop", subtitle = "Hits", colorHex = 0xFFE91E63, iconEmoji = "🎤")
        val sampleSongs = listOf(
            createSong("youtube_1", "Levitating", "Dua Lipa")
        )
        val samplePlaylists = listOf(
            Playlist(id = "PL123", name = "Today's Pop Hits", source = "YOUTUBE", songCount = 50)
        )

        coEvery { youTubeRepository.getYouTubeGenreExplore(testGenre) } returns YouTubeGenreExploreResult(
            genre = testGenre,
            topSongs = sampleSongs,
            playlists = samplePlaylists
        )

        viewModel.selectGenre(testGenre)
        advanceUntilIdle()

        assertEquals(testGenre, viewModel.selectedGenre.value)
        val result = viewModel.genreExploreResult.value
        assertNotNull(result)
        assertEquals("Pop", result?.genre?.title)
        assertEquals(1, result?.topSongs?.size)
        assertEquals("Levitating", result?.topSongs?.first()?.title)
        assertEquals(1, result?.playlists?.size)
        assertEquals("Today's Pop Hits", result?.playlists?.first()?.name)

        coVerify(exactly = 1) { youTubeRepository.getYouTubeGenreExplore(testGenre) }
    }

    @Test
    fun `deselecting genre clears genreExploreResult`() = runTest {
        val testGenre = YouTubeGenre(id = "rock", title = "Rock & Alt", subtitle = "Riffs", colorHex = 0xFF9C27B0, iconEmoji = "🎸")
        coEvery { youTubeRepository.getYouTubeGenreExplore(testGenre) } returns YouTubeGenreExploreResult(
            genre = testGenre,
            topSongs = emptyList(),
            playlists = emptyList()
        )

        viewModel.selectGenre(testGenre)
        advanceUntilIdle()
        assertNotNull(viewModel.selectedGenre.value)

        viewModel.selectGenre(null)
        advanceUntilIdle()

        assertNull(viewModel.selectedGenre.value)
        assertNull(viewModel.genreExploreResult.value)
    }
}
