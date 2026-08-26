package com.quietrays.tonarc.presentation.viewmodel

import android.content.Context
import android.os.Environment
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.quietrays.tonarc.data.backup.BackupManager
import com.quietrays.tonarc.data.model.SearchResultItem
import com.quietrays.tonarc.data.model.Artist
import com.quietrays.tonarc.data.preferences.ThemePreferencesRepository
import com.quietrays.tonarc.data.preferences.UserPreferencesRepository
import com.quietrays.tonarc.data.repository.MusicRepository
import com.quietrays.tonarc.data.worker.SyncManager
import com.quietrays.tonarc.data.youtube.YouTubeRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

import com.quietrays.tonarc.data.network.deezer.DeezerApiService
import com.quietrays.tonarc.data.network.deezer.DeezerSearchResponse
import com.quietrays.tonarc.data.network.deezer.DeezerArtist

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var themePreferencesRepository: ThemePreferencesRepository
    private lateinit var syncManager: SyncManager
    private lateinit var backupManager: BackupManager
    private lateinit var musicRepository: MusicRepository
    private lateinit var youTubeRepository: YouTubeRepository
    private lateinit var deezerApiService: DeezerApiService
    private lateinit var context: Context
    private lateinit var tempDir: java.nio.file.Path

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempDir = Files.createTempDirectory("setup-vm-test")
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns tempDir.toFile()
        userPreferencesRepository = UserPreferencesRepository(
            dataStore = PreferenceDataStoreFactory.create(
                scope = kotlinx.coroutines.CoroutineScope(testDispatcher),
                produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
            ),
            json = Json
        )
        themePreferencesRepository = mockk(relaxed = true)
        syncManager = mockk(relaxed = true) {
            coEvery { isSyncing } returns flowOf(false)
        }
        backupManager = mockk(relaxed = true)
        musicRepository = mockk(relaxed = true)
        youTubeRepository = mockk(relaxed = true)
        deezerApiService = mockk(relaxed = true) {
            coEvery { searchArtist(any(), any()) } returns DeezerSearchResponse(emptyList(), 0)
        }
        val storageManager = mockk<android.os.storage.StorageManager>(relaxed = true) {
            every { storageVolumes } returns emptyList()
        }
        context = mockk(relaxed = true) {
            every { getSystemService(Context.STORAGE_SERVICE) } returns storageManager
            every { getSystemService(android.os.storage.StorageManager::class.java) } returns storageManager
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Environment::class)
        tempDir.toFile().deleteRecursively()
    }

    private fun createViewModel(): SetupViewModel {
        return SetupViewModel(
            userPreferencesRepository = userPreferencesRepository,
            themePreferencesRepository = themePreferencesRepository,
            syncManager = syncManager,
            backupManager = backupManager,
            musicRepository = musicRepository,
            youTubeRepository = youTubeRepository,
            deezerApiService = deezerApiService,
            context = context
        )
    }

    @Test
    fun `toggleFavoriteArtist adds and removes artist correctly`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.selectedFavoriteArtists.isEmpty())

        viewModel.toggleFavoriteArtist("Taylor Swift")
        assertTrue(viewModel.uiState.value.selectedFavoriteArtists.contains("Taylor Swift"))
        assertEquals(1, viewModel.uiState.value.selectedFavoriteArtists.size)

        viewModel.toggleFavoriteArtist("The Weeknd")
        assertEquals(2, viewModel.uiState.value.selectedFavoriteArtists.size)

        viewModel.toggleFavoriteArtist("Taylor Swift")
        assertFalse(viewModel.uiState.value.selectedFavoriteArtists.contains("Taylor Swift"))
        assertEquals(1, viewModel.uiState.value.selectedFavoriteArtists.size)
    }

    @Test
    fun `minimum 5 favorite artists validation`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasMinimumFavoriteArtists)

        val artists = listOf("Taylor Swift", "The Weeknd", "Billie Eilish", "Coldplay", "Eminem")
        artists.take(4).forEach { viewModel.toggleFavoriteArtist(it) }
        assertFalse(viewModel.uiState.value.hasMinimumFavoriteArtists)

        viewModel.toggleFavoriteArtist(artists[4])
        assertTrue(viewModel.uiState.value.hasMinimumFavoriteArtists)
    }

    @Test
    fun `saveFavoriteArtists writes selected artists to repository`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val artists = setOf("Taylor Swift", "The Weeknd", "Billie Eilish", "Coldplay", "Eminem")
        artists.forEach { viewModel.toggleFavoriteArtist(it) }
        viewModel.saveFavoriteArtists()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(artists, userPreferencesRepository.favoriteArtistsFlow.first())
    }

    @Test
    fun `searchArtists updates query and fetches results`() = runTest(testDispatcher) {
        coEvery {
            youTubeRepository.searchAllPaginated(query = "Dua", filterType = com.quietrays.tonarc.data.model.SearchFilterType.ARTISTS)
        } returns YouTubeRepository.YouTubeMultiPageResult(
            items = listOf(
                SearchResultItem.ArtistItem(
                    Artist(id = 1L, name = "Dua Lipa", songCount = 50, imageUrl = "https://img/dualipa.jpg")
                )
            ),
            continuationToken = null
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setArtistSearchQuery("Dua")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Dua", viewModel.uiState.value.artistSearchQuery)
        assertEquals(1, viewModel.uiState.value.artistSearchResults.size)
        assertEquals("Dua Lipa", viewModel.uiState.value.artistSearchResults.first().name)
    }

    @Test
    fun `searchArtists deduplicates duplicate artist names and assigns unique ids`() = runTest(testDispatcher) {
        coEvery {
            youTubeRepository.searchAllPaginated(query = "IMA", filterType = com.quietrays.tonarc.data.model.SearchFilterType.ARTISTS)
        } returns YouTubeRepository.YouTubeMultiPageResult(
            items = listOf(
                SearchResultItem.ArtistItem(
                    Artist(id = 1L, name = "IMA", songCount = 10, imageUrl = "https://img/ima1.jpg")
                ),
                SearchResultItem.ArtistItem(
                    Artist(id = 2L, name = "IMA", songCount = 5, imageUrl = "https://img/ima2.jpg")
                ),
                SearchResultItem.ArtistItem(
                    Artist(id = 3L, name = "Imagine Dragons", songCount = 100, imageUrl = "https://img/id.jpg")
                )
            ),
            continuationToken = null
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setArtistSearchQuery("IMA")
        testDispatcher.scheduler.advanceUntilIdle()

        val results = viewModel.uiState.value.artistSearchResults
        assertEquals(2, results.size)
        assertEquals("IMA", results[0].name)
        assertEquals("Imagine Dragons", results[1].name)
    }

    @Test
    fun `loadPopularArtists enriches artists with Deezer images and local artists`() = runTest(testDispatcher) {
        coEvery { deezerApiService.searchArtist("Taylor Swift", 1) } returns DeezerSearchResponse(
            data = listOf(
                DeezerArtist(id = 1L, name = "Taylor Swift", pictureMedium = "https://img.deezer.com/ts_med.jpg")
            ),
            total = 1
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val artists = viewModel.uiState.value.popularArtists
        assertTrue(artists.isNotEmpty())
        val taylor = artists.firstOrNull { it.name == "Taylor Swift" }
        assertTrue(taylor != null)
        assertEquals("https://img.deezer.com/ts_med.jpg", taylor?.imageUrl)
    }
}
