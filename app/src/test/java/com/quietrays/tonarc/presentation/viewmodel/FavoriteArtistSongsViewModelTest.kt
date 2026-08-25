package com.quietrays.tonarc.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.preferences.UserPreferencesRepository
import com.quietrays.tonarc.data.repository.ArtistImageRepository
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteArtistSongsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockMusicRepository = mockk<MusicRepository>(relaxed = true)
    private val mockYouTubeRepository = mockk<YouTubeRepository>(relaxed = true)
    private val mockArtistImageRepository = mockk<ArtistImageRepository>(relaxed = true)
    private val mockUserPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)

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
            albumArtUriString = "https://example.com/art/$id.jpg",
            duration = 200_000L,
            mimeType = "audio/mp3",
            bitrate = 320,
            sampleRate = 44100
        )
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads local and online songs for favorite artist and resolves image`() = runTest {
        val artistName = "Coldplay"
        val savedStateHandle = SavedStateHandle(mapOf("artistName" to artistName))

        val localSong = createDummySong("local_1", "Yellow", "Coldplay")
        val onlineSong = createDummySong("yt_1", "Fix You", "Coldplay")

        every { mockUserPreferencesRepository.favoriteArtistsFlow } returns flowOf(setOf(artistName))
        every { mockMusicRepository.getAudioFiles() } returns flowOf(listOf(localSong))
        coEvery { mockYouTubeRepository.searchSongsPaginated(artistName) } returns YouTubeRepository.YouTubePageResult(
            songs = listOf(onlineSong),
            continuationToken = null
        )
        coEvery { mockArtistImageRepository.getArtistImageUrl(artistName, 0L) } returns "https://example.com/coldplay.jpg"

        val viewModel = FavoriteArtistSongsViewModel(
            musicRepository = mockMusicRepository,
            youTubeRepository = mockYouTubeRepository,
            artistImageRepository = mockArtistImageRepository,
            userPreferencesRepository = mockUserPreferencesRepository,
            savedStateHandle = savedStateHandle
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Coldplay", state.artistName)
        assertTrue(state.isFavorite)
        assertFalse(state.isLoading)
        assertEquals(2, state.songs.size)
        assertEquals("https://example.com/coldplay.jpg", state.artistImageUrl)
    }

    @Test
    fun `toggleFavorite calls userPreferencesRepository`() = runTest {
        val artistName = "Coldplay"
        val savedStateHandle = SavedStateHandle(mapOf("artistName" to artistName))

        every { mockUserPreferencesRepository.favoriteArtistsFlow } returns flowOf(emptySet())
        every { mockMusicRepository.getAudioFiles() } returns flowOf(emptyList())
        coEvery { mockYouTubeRepository.searchSongsPaginated(any()) } returns YouTubeRepository.YouTubePageResult(
            songs = emptyList(),
            continuationToken = null
        )

        val viewModel = FavoriteArtistSongsViewModel(
            musicRepository = mockMusicRepository,
            youTubeRepository = mockYouTubeRepository,
            artistImageRepository = mockArtistImageRepository,
            userPreferencesRepository = mockUserPreferencesRepository,
            savedStateHandle = savedStateHandle
        )

        advanceUntilIdle()

        viewModel.toggleFavorite()
        advanceUntilIdle()

        coVerify { mockUserPreferencesRepository.toggleFavoriteArtist(artistName) }
    }

    @Test
    fun `loads multiple pages and loadMore appends additional songs`() = runTest {
        val artistName = "Coldplay"
        val savedStateHandle = SavedStateHandle(mapOf("artistName" to artistName))

        val song1 = createDummySong("yt_1", "Song 1", "Coldplay")
        val song2 = createDummySong("yt_2", "Song 2", "Coldplay")
        val song3 = createDummySong("yt_3", "Song 3", "Coldplay")

        every { mockUserPreferencesRepository.favoriteArtistsFlow } returns flowOf(emptySet())
        every { mockMusicRepository.getAudioFiles() } returns flowOf(emptyList())

        // Initial 2 pages loaded automatically
        coEvery { mockYouTubeRepository.searchSongsPaginated(artistName, continuation = null) } returns YouTubeRepository.YouTubePageResult(
            songs = listOf(song1),
            continuationToken = "token_page_2"
        )
        coEvery { mockYouTubeRepository.searchSongsPaginated(artistName, continuation = "token_page_2") } returns YouTubeRepository.YouTubePageResult(
            songs = listOf(song2),
            continuationToken = "token_page_3"
        )
        coEvery { mockYouTubeRepository.searchSongsPaginated(artistName, continuation = "token_page_3") } returns YouTubeRepository.YouTubePageResult(
            songs = emptyList(),
            continuationToken = "token_page_3"
        )
        coEvery { mockYouTubeRepository.searchSongsPaginated("$artistName songs") } returns YouTubeRepository.YouTubePageResult(
            songs = emptyList(),
            continuationToken = null
        )

        val viewModel = FavoriteArtistSongsViewModel(
            musicRepository = mockMusicRepository,
            youTubeRepository = mockYouTubeRepository,
            artistImageRepository = mockArtistImageRepository,
            userPreferencesRepository = mockUserPreferencesRepository,
            savedStateHandle = savedStateHandle
        )

        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.songs.size)
        assertTrue(viewModel.uiState.value.hasMore)

        // Now test loadMore
        coEvery { mockYouTubeRepository.searchSongsPaginated(artistName, continuation = "token_page_3") } returns YouTubeRepository.YouTubePageResult(
            songs = listOf(song3),
            continuationToken = null
        )

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.songs.size)
        assertFalse(viewModel.uiState.value.hasMore)
    }
}
