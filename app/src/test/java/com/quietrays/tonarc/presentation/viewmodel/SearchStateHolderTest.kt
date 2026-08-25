package com.quietrays.tonarc.presentation.viewmodel

import com.quietrays.tonarc.data.model.Album
import com.quietrays.tonarc.data.model.Artist
import com.quietrays.tonarc.data.model.SearchFilterType
import com.quietrays.tonarc.data.model.SearchHistoryItem
import com.quietrays.tonarc.data.model.SearchResultItem
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.repository.MusicRepository
import com.quietrays.tonarc.data.youtube.YouTubeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchStateHolderTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: CoroutineScope
    private lateinit var musicRepository: MusicRepository
    private lateinit var youTubeRepository: YouTubeRepository
    private lateinit var searchStateHolder: SearchStateHolder

    private val testSong1 = Song(
        id = "local_1",
        title = "Midnight City",
        artist = "M83",
        artistId = 101L,
        album = "Hurry Up, We're Dreaming",
        albumId = 201L,
        path = "/storage/emulated/0/Music/m83.mp3",
        contentUriString = "content://media/1",
        albumArtUriString = null,
        duration = 240000L,
        mimeType = "audio/mp3",
        bitrate = 320,
        sampleRate = 44100
    )

    private val testSong2Online = Song(
        id = "yt_video_123",
        title = "Midnight City (Live)",
        artist = "M83",
        artistId = 101L,
        album = "Live at Red Rocks",
        albumId = 202L,
        path = "",
        contentUriString = "",
        albumArtUriString = "https://img.youtube.com/vi/yt_video_123/0.jpg",
        duration = 260000L,
        mimeType = "audio/webm",
        bitrate = null,
        sampleRate = null,
        youtubeId = "yt_video_123"
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        testScope = CoroutineScope(SupervisorJob() + testDispatcher)
        musicRepository = mockk(relaxed = true)
        youTubeRepository = mockk(relaxed = true)

        searchStateHolder = SearchStateHolder(
            musicRepository = musicRepository,
            youTubeRepository = youTubeRepository
        )
        searchStateHolder.initialize(testScope)
    }

    @AfterEach
    fun tearDown() {
        searchStateHolder.onCleared()
        testScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `performSearch combines local and online results without overwriting`() = runTest(testDispatcher) {
        val localFlow = MutableStateFlow<List<SearchResultItem>>(listOf(SearchResultItem.SongItem(testSong1)))
        every { musicRepository.searchAll("Midnight", any()) } returns localFlow
        coEvery {
            youTubeRepository.searchAllPaginated("Midnight", any(), any())
        } returns YouTubeRepository.YouTubeMultiPageResult(
            items = listOf(SearchResultItem.SongItem(testSong2Online)),
            continuationToken = "continuation_abc"
        )

        searchStateHolder.performSearch("Midnight")
        testDispatcher.scheduler.advanceTimeBy(200L) // Pass debounce
        testDispatcher.scheduler.advanceUntilIdle()

        // Should have both local and online items combined
        val results = searchStateHolder.searchResults.value
        assertEquals(2, results.size)
        assertTrue(results.any { it is SearchResultItem.SongItem && it.song.id == "local_1" })
        assertTrue(results.any { it is SearchResultItem.SongItem && it.song.id == "yt_video_123" })

        // 2. Local emits again (e.g. database change) -> MUST NOT overwrite online results!
        localFlow.value = listOf(SearchResultItem.SongItem(testSong1))
        testDispatcher.scheduler.advanceUntilIdle()

        val resultsAfterReEmit = searchStateHolder.searchResults.value
        assertEquals(2, resultsAfterReEmit.size)
        assertTrue(resultsAfterReEmit.any { it is SearchResultItem.SongItem && it.song.id == "yt_video_123" })
    }

    @Test
    fun `loadMoreSearchResults appends unique items to search results`() = runTest(testDispatcher) {
        val localFlow = MutableStateFlow<List<SearchResultItem>>(listOf(SearchResultItem.SongItem(testSong1)))
        every { musicRepository.searchAll(any(), any()) } returns localFlow
        coEvery {
            youTubeRepository.searchAllPaginated("Midnight", any(), isNull())
        } returns YouTubeRepository.YouTubeMultiPageResult(
            items = listOf(SearchResultItem.SongItem(testSong2Online)),
            continuationToken = "token_page_1"
        )

        val testSong3Online = testSong2Online.copy(id = "yt_video_456", title = "Midnight City (Remix)")
        coEvery {
            youTubeRepository.searchAllPaginated("Midnight", any(), "token_page_1")
        } returns YouTubeRepository.YouTubeMultiPageResult(
            items = listOf(SearchResultItem.SongItem(testSong3Online)),
            continuationToken = null
        )

        searchStateHolder.performSearch("Midnight")
        testDispatcher.scheduler.advanceTimeBy(200L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, searchStateHolder.searchResults.value.size)

        // Load page 2
        searchStateHolder.loadMoreSearchResults()
        testDispatcher.scheduler.advanceUntilIdle()

        val resultsAfterPagination = searchStateHolder.searchResults.value
        assertEquals(3, resultsAfterPagination.size)
        assertTrue(resultsAfterPagination.any { it is SearchResultItem.SongItem && it.song.id == "yt_video_456" })
    }

    @Test
    fun `search history operations work correctly`() = runTest(testDispatcher) {
        val historyList = listOf(
            SearchHistoryItem(id = 1L, query = "Daft Punk", timestamp = 1000L),
            SearchHistoryItem(id = 2L, query = "The Weeknd", timestamp = 2000L)
        )
        coEvery { musicRepository.getRecentSearchHistory(any()) } returns historyList

        searchStateHolder.loadSearchHistory(15)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, searchStateHolder.searchHistory.value.size)
        assertEquals("Daft Punk", searchStateHolder.searchHistory.value[0].query)

        // Submit query
        searchStateHolder.onSearchQuerySubmitted("Coldplay")
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { musicRepository.addSearchHistoryItem("Coldplay") }

        // Delete query
        searchStateHolder.deleteSearchHistoryItem("Daft Punk")
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { musicRepository.deleteSearchHistoryItemByQuery("Daft Punk") }

        // Clear all
        searchStateHolder.clearSearchHistory()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { musicRepository.clearSearchHistory() }
        assertEquals(0, searchStateHolder.searchHistory.value.size)
    }
}
