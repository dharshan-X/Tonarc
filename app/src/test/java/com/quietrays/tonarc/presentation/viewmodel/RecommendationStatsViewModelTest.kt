package com.quietrays.tonarc.presentation.viewmodel

import android.content.Context
import com.quietrays.tonarc.MainCoroutineExtension
import com.quietrays.tonarc.data.database.EngagementDao
import com.quietrays.tonarc.data.database.ItemCooccurrenceDao
import com.quietrays.tonarc.data.database.ItemCooccurrenceEntity
import com.quietrays.tonarc.data.database.MusicDao
import com.quietrays.tonarc.data.database.OfflineTrackDao
import com.quietrays.tonarc.data.database.SongEngagementEntity
import com.quietrays.tonarc.data.database.YouTubeDao
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.recommendation.AdaptiveWeightTuner
import com.quietrays.tonarc.data.repository.MusicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainCoroutineExtension::class)
class RecommendationStatsViewModelTest {

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockEngagementDao = mockk<EngagementDao>(relaxed = true)
    private val mockCooccurrenceDao = mockk<ItemCooccurrenceDao>(relaxed = true)
    private val mockMusicRepository = mockk<MusicRepository>(relaxed = true)
    private val mockMusicDao = mockk<MusicDao>(relaxed = true)
    private val mockYouTubeDao = mockk<YouTubeDao>(relaxed = true)
    private val mockOfflineTrackDao = mockk<OfflineTrackDao>(relaxed = true)
    private val weightTuner = AdaptiveWeightTuner()

    private fun createDummySong(id: String, title: String, artist: String): Song {
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
            mimeType = "audio/mp3",
            bitrate = 320000,
            sampleRate = 44100
        )
    }

    @BeforeEach
    fun setUp() {
        every { mockEngagementDao.getAllEngagementsFlow() } returns flowOf(emptyList())
        coEvery { mockEngagementDao.getAllEngagements() } returns emptyList()
        coEvery { mockCooccurrenceDao.getEdgeCount() } returns 0
        coEvery { mockCooccurrenceDao.getTopCooccurrences(any()) } returns emptyList()
        every { mockMusicRepository.getAudioFiles() } returns flowOf(emptyList())
        coEvery { mockMusicDao.getSongsByIdsListSimple(any()) } returns emptyList()
        coEvery { mockYouTubeDao.getAllYouTubeSongsList() } returns emptyList()
        coEvery { mockOfflineTrackDao.getCompleted() } returns emptyList()
    }

    private fun createViewModel(dispatcher: kotlinx.coroutines.CoroutineDispatcher): RecommendationStatsViewModel {
        return RecommendationStatsViewModel(
            context = mockContext,
            engagementDao = mockEngagementDao,
            itemCooccurrenceDao = mockCooccurrenceDao,
            adaptiveWeightTuner = weightTuner,
            musicRepository = mockMusicRepository,
            musicDao = mockMusicDao,
            youTubeDao = mockYouTubeDao,
            offlineTrackDao = mockOfflineTrackDao,
            ioDispatcher = dispatcher
        )
    }

    @Test
    fun `test initial stats load on empty database`() = runTest {
        val viewModel = createViewModel(UnconfinedTestDispatcher(testScheduler))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(0, state.totalSongsTracked)
        assertEquals(0, state.totalPlays)
        assertEquals(0, state.totalCompletions)
        assertEquals(0, state.totalSkips)
        assertEquals(0.0, state.completionRatePct, 0.001)
        assertEquals(0.0, state.skipRatePct, 0.001)
        assertEquals(0, state.totalCooccurrenceEdges)
        assertTrue(state.topEngagedSongs.isEmpty())
        assertTrue(state.topCooccurrences.isEmpty())
    }

    @Test
    fun `test accurate telemetry and rates computation`() = runTest {
        val dummySong1 = createDummySong("s1", "Song 1", "Artist 1")
        val dummySong2 = createDummySong("s2", "Song 2", "Artist 2")

        val engagements = listOf(
            SongEngagementEntity("s1", playCount = 10, completionCount = 8, skipBefore30sCount = 1, sessionRepeatCount = 2, totalPlayDurationMs = 1800_000L),
            SongEngagementEntity("s2", playCount = 5, completionCount = 2, skipBefore30sCount = 3, sessionRepeatCount = 0, totalPlayDurationMs = 500_000L)
        )
        val cooccurrences = listOf(
            ItemCooccurrenceEntity("s1", "s2", cooccurrenceCount = 7, lastUpdatedTimestamp = 1000L)
        )

        coEvery { mockEngagementDao.getAllEngagements() } returns engagements
        every { mockEngagementDao.getAllEngagementsFlow() } returns flowOf(engagements)
        coEvery { mockCooccurrenceDao.getEdgeCount() } returns 1
        coEvery { mockCooccurrenceDao.getTopCooccurrences(any()) } returns cooccurrences
        every { mockMusicRepository.getAudioFiles() } returns flowOf(listOf(dummySong1, dummySong2))

        val viewModel = createViewModel(UnconfinedTestDispatcher(testScheduler))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.totalSongsTracked)
        assertEquals(15, state.totalPlays)
        assertEquals(10, state.totalCompletions)
        assertEquals(4, state.totalSkips)
        assertEquals(2, state.totalRepeats)
        assertEquals(15, state.totalSessions)
        // Completion rate: 10 / 15 = 66.67%
        assertEquals((10.0 / 15.0) * 100.0, state.completionRatePct, 0.01)
        // Skip rate: 4 / 15 = 26.67%
        assertEquals((4.0 / 15.0) * 100.0, state.skipRatePct, 0.01)
        assertEquals(1, state.totalCooccurrenceEdges)

        assertEquals(2, state.topEngagedSongs.size)
        assertEquals("Song 1", state.topEngagedSongs[0].song?.title)
        assertEquals("Artist 1", state.topEngagedSongs[0].song?.artist)

        assertEquals(1, state.topCooccurrences.size)
        assertEquals("Song 1", state.topCooccurrences[0].songA?.title)
        assertEquals("Song 2", state.topCooccurrences[0].songB?.title)
        assertEquals(7, state.topCooccurrences[0].entity.cooccurrenceCount)
    }

    @Test
    fun `test simulate actions update DAO and reload without setting full loading state`() = runTest {
        val viewModel = createViewModel(UnconfinedTestDispatcher(testScheduler))
        advanceUntilIdle()

        viewModel.simulatePlay("song_123")
        advanceUntilIdle()
        coVerify(atLeast = 1) { mockEngagementDao.recordPlay("song_123", any(), any()) }

        viewModel.simulateCompletion("song_123")
        advanceUntilIdle()
        coVerify(atLeast = 1) { mockEngagementDao.recordCompletion("song_123", any()) }

        viewModel.simulateSkip("song_123")
        advanceUntilIdle()
        coVerify(atLeast = 1) { mockEngagementDao.recordSkip("song_123", any()) }

        viewModel.simulateRepeat("song_123")
        advanceUntilIdle()
        coVerify(atLeast = 1) { mockEngagementDao.recordSessionRepeat("song_123", any(), any()) }

        viewModel.simulatePairwisePlay("sA", "sB")
        advanceUntilIdle()
        coVerify(atLeast = 1) { mockCooccurrenceDao.incrementCooccurrence("sA", "sB", any()) }
    }

    @Test
    fun `test clear all telemetry resets data and posts message`() = runTest {
        val viewModel = createViewModel(UnconfinedTestDispatcher(testScheduler))
        advanceUntilIdle()

        viewModel.clearAllTelemetry()
        advanceUntilIdle()

        coVerify { mockEngagementDao.clearAllEngagements() }
        coVerify { mockCooccurrenceDao.clearAll() }
        assertNotNull(viewModel.uiState.value.message)
    }
}
