package com.quietrays.tonarc.presentation.viewmodel

import com.quietrays.tonarc.data.ContextualMix
import com.quietrays.tonarc.data.DailyMixManager
import com.quietrays.tonarc.data.MixMood
import com.quietrays.tonarc.data.database.YouTubeDao
import com.quietrays.tonarc.data.database.YouTubeSongEntity
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.preferences.UserPreferencesRepository
import com.quietrays.tonarc.data.repository.MusicRepository
import com.quietrays.tonarc.data.youtube.YouTubeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DailyMixStateHolderTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val mockDailyMixManager: DailyMixManager = mockk(relaxed = true)
    private val mockUserPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val mockMusicRepository: MusicRepository = mockk(relaxed = true)
    private val mockYouTubeRepository: YouTubeRepository = mockk(relaxed = true)
    private val mockYouTubeDao: YouTubeDao = mockk(relaxed = true)

    private lateinit var dailyMixStateHolder: DailyMixStateHolder

    private fun createTestSong(
        id: String,
        title: String,
        artist: String,
        youtubeId: String? = null
    ): Song {
        return Song(
            id = id,
            title = title,
            artist = artist,
            artistId = 1L,
            album = "Test Album",
            albumId = 1L,
            path = "/test/path/$id",
            contentUriString = "content://media/$id",
            albumArtUriString = null,
            duration = 180L,
            mimeType = "audio/mp3",
            bitrate = 320,
            sampleRate = 44100,
            youtubeId = youtubeId
        )
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockDailyMixManager.getCurrentTimeMood(any()) } returns MixMood.MORNING_FOCUS
        dailyMixStateHolder = DailyMixStateHolder(
            dailyMixManager = mockDailyMixManager,
            userPreferencesRepository = mockUserPreferencesRepository,
            musicRepository = mockMusicRepository,
            youTubeRepository = mockYouTubeRepository,
            youTubeDao = mockYouTubeDao,
            ioDispatcher = testDispatcher
        )
        dailyMixStateHolder.initialize(testScope)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updateDailyMix combines local and YouTube candidate songs and generates mixes`() = testScope.runTest {
        val localSong = createTestSong("101", "Local Track", "Local Artist")
        val ytSongEntity = YouTubeSongEntity(
            id = "youtube_vid1",
            videoId = "vid1",
            playlistId = "__history__",
            title = "Cached YouTube Track",
            artist = "YT Artist",
            album = "YT Album",
            duration = 210,
            thumbnailUrl = "https://thumb.url/1",
            year = 2024,
            dateAdded = 1000L
        )
        val ytQuickPick = createTestSong("youtube_vid2", "Quick Pick Track", "Popular Artist", youtubeId = "vid2")

        coEvery { mockMusicRepository.getAllSongsOnce() } returns listOf(localSong)
        coEvery { mockYouTubeDao.getAllYouTubeSongsList() } returns listOf(ytSongEntity)
        coEvery { mockYouTubeRepository.getHomeRecommendations() } returns YouTubeRepository.HomeRecommendations(
            quickPicks = listOf(ytQuickPick)
        )
        every { mockUserPreferencesRepository.favoriteArtistsFlow } returns flowOf(emptySet())

        val expectedDailyMix = listOf(localSong, ytQuickPick)
        val expectedYourMix = listOf(ytSongEntity.toSong(), localSong, ytQuickPick)
        val expectedContextualMixes = listOf(
            ContextualMix(MixMood.MORNING_FOCUS, MixMood.MORNING_FOCUS.displayName, MixMood.MORNING_FOCUS.subtitle, listOf(localSong)),
            ContextualMix(MixMood.DISCOVERY_RADAR, MixMood.DISCOVERY_RADAR.displayName, MixMood.DISCOVERY_RADAR.subtitle, listOf(ytQuickPick))
        )

        coEvery { mockDailyMixManager.generateDailyMix(any(), any()) } returns expectedDailyMix
        coEvery { mockDailyMixManager.generateYourMix(any(), any()) } returns expectedYourMix
        coEvery { mockDailyMixManager.generateAllContextualMixes(any(), any()) } returns expectedContextualMixes

        dailyMixStateHolder.updateDailyMix(flowOf(emptySet()))
        advanceUntilIdle()

        assertEquals(expectedDailyMix, dailyMixStateHolder.dailyMixSongs.value)
        assertEquals(expectedYourMix, dailyMixStateHolder.yourMixSongs.value)
        assertEquals(expectedContextualMixes, dailyMixStateHolder.contextualMixes.value)

        coVerify { mockUserPreferencesRepository.saveDailyMixSongIds(listOf("101", "youtube_vid2")) }
        coVerify { mockUserPreferencesRepository.saveYourMixSongIds(listOf("youtube_vid1", "101", "youtube_vid2")) }
    }

    @Test
    fun `selectMood updates selectedMood state flow`() {
        assertEquals(MixMood.MORNING_FOCUS, dailyMixStateHolder.selectedMood.value)

        dailyMixStateHolder.selectMood(MixMood.ENERGY_BOOST)
        assertEquals(MixMood.ENERGY_BOOST, dailyMixStateHolder.selectedMood.value)

        dailyMixStateHolder.selectMood(MixMood.DISCOVERY_RADAR)
        assertEquals(MixMood.DISCOVERY_RADAR, dailyMixStateHolder.selectedMood.value)
    }

    @Test
    fun `updateDailyMix populates contextualMixes for all 5 moods`() = testScope.runTest {
        val song1 = createTestSong("1", "Song 1", "Artist")
        val song2 = createTestSong("2", "Song 2", "Artist")

        coEvery { mockMusicRepository.getAllSongsOnce() } returns listOf(song1, song2)
        coEvery { mockYouTubeDao.getAllYouTubeSongsList() } returns emptyList()
        coEvery { mockYouTubeRepository.getHomeRecommendations() } returns YouTubeRepository.HomeRecommendations()
        every { mockUserPreferencesRepository.favoriteArtistsFlow } returns flowOf(emptySet())

        val mockContextualMixes = MixMood.entries.map { mood ->
            ContextualMix(
                mood = mood,
                title = mood.displayName,
                subtitle = mood.subtitle,
                songs = listOf(song1, song2)
            )
        }

        coEvery { mockDailyMixManager.generateDailyMix(any(), any()) } returns listOf(song1)
        coEvery { mockDailyMixManager.generateYourMix(any(), any()) } returns listOf(song2)
        coEvery { mockDailyMixManager.generateAllContextualMixes(any(), any()) } returns mockContextualMixes

        dailyMixStateHolder.updateDailyMix(flowOf(emptySet()))
        advanceUntilIdle()

        assertEquals(5, dailyMixStateHolder.contextualMixes.value.size)
        assertEquals(mockContextualMixes, dailyMixStateHolder.contextualMixes.value)
        assertEquals(MixMood.MORNING_FOCUS, dailyMixStateHolder.contextualMixes.value[0].mood)
        assertEquals(MixMood.DISCOVERY_RADAR, dailyMixStateHolder.contextualMixes.value[4].mood)
    }

    @Test
    fun `updateDailyMix with empty candidate songs clears contextualMixes`() = testScope.runTest {
        coEvery { mockMusicRepository.getAllSongsOnce() } returns emptyList()
        coEvery { mockYouTubeDao.getAllYouTubeSongsList() } returns emptyList()
        coEvery { mockYouTubeRepository.getHomeRecommendations() } returns YouTubeRepository.HomeRecommendations()
        every { mockUserPreferencesRepository.favoriteArtistsFlow } returns flowOf(emptySet())

        dailyMixStateHolder.updateDailyMix(flowOf(emptySet()))
        advanceUntilIdle()

        assertEquals(0, dailyMixStateHolder.dailyMixSongs.value.size)
        assertEquals(0, dailyMixStateHolder.yourMixSongs.value.size)
        assertEquals(0, dailyMixStateHolder.contextualMixes.value.size)
    }

    @Test
    fun `loadPersistedDailyMix restores mixes containing YouTube song IDs`() = testScope.runTest {
        val dailyMixIds = listOf("101", "youtube_vid1")
        val yourMixIds = listOf("youtube_vid1")

        every { mockUserPreferencesRepository.dailyMixSongIdsFlow } returns flowOf(dailyMixIds)
        every { mockUserPreferencesRepository.yourMixSongIdsFlow } returns flowOf(yourMixIds)

        val localSong = createTestSong("101", "Local Track", "Local Artist")
        val ytSong = createTestSong("youtube_vid1", "YouTube Track", "YT Artist", youtubeId = "vid1")

        every { mockMusicRepository.getSongsByIds(dailyMixIds) } returns flowOf(listOf(localSong, ytSong))
        every { mockMusicRepository.getSongsByIds(yourMixIds) } returns flowOf(listOf(ytSong))

        dailyMixStateHolder.loadPersistedDailyMix()
        advanceUntilIdle()

        assertEquals(2, dailyMixStateHolder.dailyMixSongs.value.size)
        assertEquals("101", dailyMixStateHolder.dailyMixSongs.value[0].id)
        assertEquals("youtube_vid1", dailyMixStateHolder.dailyMixSongs.value[1].id)

        assertEquals(1, dailyMixStateHolder.yourMixSongs.value.size)
        assertEquals("youtube_vid1", dailyMixStateHolder.yourMixSongs.value[0].id)
    }

    @Test
    fun `removeFromDailyMix removes song from active daily mix state`() {
        val song1 = createTestSong("1", "Song 1", "Artist")
        val song2 = createTestSong("youtube_2", "Song 2", "Artist", youtubeId = "2")

        dailyMixStateHolder.initialize(testScope)
        coEvery { mockMusicRepository.getAllSongsOnce() } returns listOf(song1, song2)
        coEvery { mockDailyMixManager.generateDailyMix(any(), any()) } returns listOf(song1, song2)
        coEvery { mockDailyMixManager.generateYourMix(any(), any()) } returns listOf(song1, song2)
        every { mockUserPreferencesRepository.favoriteArtistsFlow } returns flowOf(emptySet())

        dailyMixStateHolder.updateDailyMix(flowOf(emptySet()))
        testScope.advanceUntilIdle()

        assertEquals(2, dailyMixStateHolder.dailyMixSongs.value.size)

        dailyMixStateHolder.removeFromDailyMix("youtube_2")

        assertEquals(1, dailyMixStateHolder.dailyMixSongs.value.size)
        assertEquals("1", dailyMixStateHolder.dailyMixSongs.value[0].id)
    }
}
