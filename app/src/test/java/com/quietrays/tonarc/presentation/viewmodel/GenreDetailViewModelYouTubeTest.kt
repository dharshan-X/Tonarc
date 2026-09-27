package com.quietrays.tonarc.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.quietrays.tonarc.MainCoroutineExtension
import com.quietrays.tonarc.data.model.Genre
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.network.youtube.YouTubeGenre
import com.quietrays.tonarc.data.network.youtube.YouTubeGenreExploreResult
import com.quietrays.tonarc.data.repository.MusicRepository
import com.quietrays.tonarc.data.youtube.YouTubeRepository
import com.quietrays.tonarc.di.DispatcherProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainCoroutineExtension::class)
class GenreDetailViewModelYouTubeTest {

    private val musicRepository = mockk<MusicRepository>(relaxed = true)
    private val youTubeRepository = mockk<YouTubeRepository>(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

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
        coEvery { musicRepository.getGenres() } returns flowOf(listOf(Genre(id = "Pop", name = "Pop")))
        coEvery { musicRepository.getMusicByGenre("Pop") } returns flowOf(emptyList())
        coEvery { musicRepository.getArtists() } returns flowOf(emptyList())
    }

    @Test
    fun `initializing GenreDetailViewModel loads YouTube Music genre explore in parallel`() = runTest {
        val testGenre = YouTubeGenre(id = "pop", title = "Pop", colorHex = 0xFFE91E63)
        val sampleSongs = listOf(
            createSong("youtube_pop_1", "Blinding Lights", "The Weeknd")
        )

        coEvery { youTubeRepository.getYouTubeGenreExplore(match { it.id == "pop" || it.title == "Pop" }) } returns YouTubeGenreExploreResult(
            genre = testGenre,
            topSongs = sampleSongs,
            playlists = emptyList()
        )

        val savedStateHandle = SavedStateHandle(mapOf("genreId" to "Pop"))
        val viewModel = GenreDetailViewModel(
            musicRepository = musicRepository,
            youTubeRepository = youTubeRepository,
            savedStateHandle = savedStateHandle,
            dispatchers = testDispatchers
        )

        advanceUntilIdle()

        val ytResult = viewModel.youtubeContent.value
        assertNotNull(ytResult)
        assertEquals(1, ytResult?.topSongs?.size)
        assertEquals("Blinding Lights", ytResult?.topSongs?.first()?.title)

        coVerify { youTubeRepository.getYouTubeGenreExplore(any()) }
    }
}
