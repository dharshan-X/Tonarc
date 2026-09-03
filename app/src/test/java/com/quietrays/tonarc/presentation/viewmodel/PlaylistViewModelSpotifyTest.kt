package com.quietrays.tonarc.presentation.viewmodel

import android.content.Context
import com.quietrays.tonarc.data.DailyMixManager
import com.quietrays.tonarc.data.database.YouTubeDao
import com.quietrays.tonarc.data.database.YouTubePlaylistEntity
import com.quietrays.tonarc.data.database.YouTubeSongEntity
import com.quietrays.tonarc.data.model.Playlist
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.network.spotify.SpotifyPlaylist
import com.quietrays.tonarc.data.network.spotify.SpotifyPlaylistFetcher
import com.quietrays.tonarc.data.network.spotify.SpotifyPrivatePlaylistException
import com.quietrays.tonarc.data.network.spotify.SpotifyTrack
import com.quietrays.tonarc.data.network.youtube.SyncState
import com.quietrays.tonarc.data.network.youtube.YouTubeLibrarySyncEngine
import com.quietrays.tonarc.data.offline.CloudOfflineRepository
import com.quietrays.tonarc.data.playlist.M3uManager
import com.quietrays.tonarc.data.playlist.NlpPlaylistGenerator
import com.quietrays.tonarc.data.preferences.PlaylistPreferencesRepository
import com.quietrays.tonarc.data.repository.MusicRepository
import com.quietrays.tonarc.data.spotify.MatchProgress
import com.quietrays.tonarc.data.spotify.MatchResult
import com.quietrays.tonarc.data.spotify.SpotifyMatchingEngine
import com.quietrays.tonarc.data.youtube.YouTubeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModelSpotifyTest {

    private val playlistPreferencesRepository: PlaylistPreferencesRepository = mockk(relaxed = true)
    private val musicRepository: MusicRepository = mockk(relaxed = true)
    private val dailyMixManager: DailyMixManager = mockk(relaxed = true)
    private val m3uManager: M3uManager = mockk(relaxed = true)
    private val nlpPlaylistGenerator: NlpPlaylistGenerator = mockk(relaxed = true)
    private val cloudOfflineRepository: CloudOfflineRepository = mockk(relaxed = true)
    private val youTubeRepository: YouTubeRepository = mockk(relaxed = true)
    private val youTubeLibrarySyncEngine: YouTubeLibrarySyncEngine = mockk(relaxed = true)
    private val youTubeDao: YouTubeDao = mockk(relaxed = true)
    private val spotifyPlaylistFetcher: SpotifyPlaylistFetcher = mockk(relaxed = true)
    private val spotifyMatchingEngine: SpotifyMatchingEngine = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    private val userPlaylistsFlow = MutableStateFlow<List<Playlist>>(emptyList())
    private val ytPlaylistsFlow = MutableStateFlow<List<YouTubePlaylistEntity>>(emptyList())
    private val syncStateFlow = MutableStateFlow<SyncState>(SyncState.Idle)

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: PlaylistViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { playlistPreferencesRepository.playlistsSortOptionFlow } returns flowOf("NAME_A_TO_Z")
        every { playlistPreferencesRepository.userPlaylistsFlow } returns userPlaylistsFlow
        every { playlistPreferencesRepository.playlistSongOrderModesFlow } returns flowOf(emptyMap())
        every { youTubeRepository.playlistsFlow } returns ytPlaylistsFlow
        every { youTubeLibrarySyncEngine.syncState } returns syncStateFlow

        viewModel = PlaylistViewModel(
            playlistPreferencesRepository,
            musicRepository,
            dailyMixManager,
            m3uManager,
            nlpPlaylistGenerator,
            cloudOfflineRepository,
            youTubeRepository,
            youTubeLibrarySyncEngine,
            youTubeDao,
            spotifyPlaylistFetcher,
            spotifyMatchingEngine,
            context
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createTestSpotifyPlaylist(
        id: String = "37i9dQZF1DXcBWIGoYBM5M",
        title: String = "Today's Top Hits",
        trackCount: Int = 2
    ): SpotifyPlaylist {
        val tracks = (1..trackCount).map { i ->
            SpotifyTrack(
                id = "track_$i",
                title = "Song Title $i",
                artist = "Artist $i",
                album = "Album $i",
                durationMs = 180_000L,
                coverUri = "https://cover.jpg/$i"
            )
        }
        return SpotifyPlaylist(
            id = id,
            title = title,
            description = "Top hits",
            author = "Spotify",
            coverUri = "https://cover.jpg/playlist",
            trackCount = trackCount,
            tracks = tracks
        )
    }

    private fun createTestSong(id: String, title: String, videoId: String = id): Song {
        return Song(
            id = id,
            title = title,
            artist = "Artist $id",
            artistId = 1L,
            album = "Album $id",
            albumId = 1L,
            path = "/music/$id.mp3",
            contentUriString = "content://music/$id",
            albumArtUriString = "https://img.jpg/$id",
            duration = 180000L,
            genre = "Pop",
            dateAdded = 1000L,
            mimeType = "audio/mp3",
            bitrate = 320,
            sampleRate = 44100,
            youtubeId = videoId
        )
    }

    @Test
    fun `previewSpotifyPlaylist with invalid URL emits Error`() = runTest {
        every { spotifyPlaylistFetcher.extractPlaylistId("invalid_url") } returns null

        viewModel.previewSpotifyPlaylist("invalid_url")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.spotifyImportState.value
        assertTrue(state is SpotifyImportState.Error)
        assertEquals("Invalid Spotify playlist link or ID", (state as SpotifyImportState.Error).message)
    }

    @Test
    fun `previewSpotifyPlaylist with valid URL emits Preview on success`() = runTest {
        val playlist = createTestSpotifyPlaylist()
        every { spotifyPlaylistFetcher.extractPlaylistId("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M") } returns "37i9dQZF1DXcBWIGoYBM5M"
        coEvery { spotifyPlaylistFetcher.fetchPlaylist("37i9dQZF1DXcBWIGoYBM5M") } returns Result.success(playlist)

        viewModel.previewSpotifyPlaylist("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.spotifyImportState.value
        assertTrue(state is SpotifyImportState.Preview)
        assertEquals("Today's Top Hits", (state as SpotifyImportState.Preview).playlist.title)
        assertEquals(2, state.playlist.tracks.size)
    }

    @Test
    fun `previewSpotifyPlaylist with valid URL emits Error on fetch failure`() = runTest {
        every { spotifyPlaylistFetcher.extractPlaylistId("37i9dQZF1DXcBWIGoYBM5M") } returns "37i9dQZF1DXcBWIGoYBM5M"
        coEvery { spotifyPlaylistFetcher.fetchPlaylist("37i9dQZF1DXcBWIGoYBM5M") } returns Result.failure(IOException("Network error"))

        viewModel.previewSpotifyPlaylist("37i9dQZF1DXcBWIGoYBM5M")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.spotifyImportState.value
        assertTrue(state is SpotifyImportState.Error)
        assertEquals("Network error", (state as SpotifyImportState.Error).message)
    }

    @Test
    fun `previewSpotifyPlaylist with private playlist and logged out user emits Error with isPrivatePlaylist true and isUserLoggedIn false`() = runTest {
        val playlistId = "37i9dQZF1DXcBWIGoYBM5M"
        every { spotifyPlaylistFetcher.extractPlaylistId("https://open.spotify.com/playlist/$playlistId") } returns playlistId
        val exception = SpotifyPrivatePlaylistException(
            playlistId = playlistId,
            isUserLoggedIn = false
        )
        coEvery { spotifyPlaylistFetcher.fetchPlaylist(playlistId) } returns Result.failure(exception)

        viewModel.previewSpotifyPlaylist("https://open.spotify.com/playlist/$playlistId")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.spotifyImportState.value
        assertTrue(state is SpotifyImportState.Error)
        val errorState = state as SpotifyImportState.Error
        assertTrue(errorState.isPrivatePlaylist)
        assertEquals(false, errorState.isUserLoggedIn)
        assertEquals(exception.message, errorState.message)
    }

    @Test
    fun `previewSpotifyPlaylist with private playlist and logged in user emits Error with isPrivatePlaylist true and isUserLoggedIn true`() = runTest {
        val playlistId = "37i9dQZF1DXcBWIGoYBM5M"
        every { spotifyPlaylistFetcher.extractPlaylistId("https://open.spotify.com/playlist/$playlistId") } returns playlistId
        val exception = SpotifyPrivatePlaylistException(
            playlistId = playlistId,
            isUserLoggedIn = true
        )
        coEvery { spotifyPlaylistFetcher.fetchPlaylist(playlistId) } returns Result.failure(exception)

        viewModel.previewSpotifyPlaylist("https://open.spotify.com/playlist/$playlistId")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.spotifyImportState.value
        assertTrue(state is SpotifyImportState.Error)
        val errorState = state as SpotifyImportState.Error
        assertTrue(errorState.isPrivatePlaylist)
        assertEquals(true, errorState.isUserLoggedIn)
        assertEquals(exception.message, errorState.message)
    }

    @Test
    fun `saveSpotifyPlaylist with empty matched songs emits Error`() = runTest {
        val playlist = createTestSpotifyPlaylist()
        val matchResults = playlist.tracks.map { track ->
            MatchResult(originalTrack = track, matchedSong = null)
        }
        coEvery {
            spotifyMatchingEngine.matchTracks(
                tracks = playlist.tracks,
                matchLocal = any(),
                matchCloud = any(),
                onProgress = any()
            )
        } returns matchResults

        viewModel.saveSpotifyPlaylist(playlist, saveAsCloud = true, saveAsLocal = true)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.spotifyImportState.value
        assertTrue(state is SpotifyImportState.Error)
        assertEquals("No matching songs found for this playlist", (state as SpotifyImportState.Error).message)
    }

    @Test
    fun `saveSpotifyPlaylist saves to both cloud and local and emits Success`() = runTest {
        val playlist = createTestSpotifyPlaylist(id = "spot123", title = "Hits", trackCount = 2)
        val song1 = createTestSong("yt_1", "Song Title 1", "vid1")
        val song2 = createTestSong("yt_2", "Song Title 2", "vid2")
        val matchResults = listOf(
            MatchResult(originalTrack = playlist.tracks[0], matchedSong = song1, isLocalMatch = false),
            MatchResult(originalTrack = playlist.tracks[1], matchedSong = song2, isLocalMatch = true)
        )

        coEvery {
            spotifyMatchingEngine.matchTracks(
                tracks = playlist.tracks,
                matchLocal = true,
                matchCloud = true,
                onProgress = any()
            )
        } answers {
            val progressCallback = it.invocation.args[3] as? ((MatchProgress) -> Unit)
            progressCallback?.invoke(MatchProgress(1, 2, "Song Title 1"))
            progressCallback?.invoke(MatchProgress(2, 2, "Song Title 2"))
            matchResults
        }

        viewModel.saveSpotifyPlaylist(
            playlist = playlist,
            customTitle = "My Imported Hits",
            saveAsCloud = true,
            saveAsLocal = true
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val targetPlaylistId = "spotify_spot123"
        coVerify { youTubeDao.deleteSongsByPlaylist(targetPlaylistId) }
        coVerify {
            youTubeDao.insertSongs(match { songs ->
                songs.size == 2 &&
                songs[0].playlistId == targetPlaylistId &&
                songs[0].title == "Song Title 1" &&
                songs[1].playlistId == targetPlaylistId &&
                songs[1].title == "Song Title 2"
            })
        }
        coVerify {
            youTubeDao.insertPlaylist(match { entity ->
                entity.id == targetPlaylistId &&
                entity.name == "My Imported Hits" &&
                entity.songCount == 2
            })
        }
        coVerify {
            playlistPreferencesRepository.createPlaylist(
                name = "My Imported Hits",
                songIds = listOf("yt_1", "yt_2"),
                isQueueGenerated = false,
                coverImageUri = null,
                coverColorArgb = null,
                coverIconName = null,
                coverShapeType = null,
                coverShapeDetail1 = null,
                coverShapeDetail2 = null,
                coverShapeDetail3 = null,
                coverShapeDetail4 = null,
                customId = null,
                source = "LOCAL"
            )
        }

        val state = viewModel.spotifyImportState.value
        assertTrue(state is SpotifyImportState.Success)
        val success = state as SpotifyImportState.Success
        assertEquals("My Imported Hits", success.playlistTitle)
        assertEquals(2, success.matchedCount)
        assertEquals(2, success.totalCount)
        assertEquals(targetPlaylistId, success.playlistId)
    }

    @Test
    fun `saveSpotifyPlaylist saves only to cloud when saveAsLocal is false`() = runTest {
        val playlist = createTestSpotifyPlaylist(id = "spot456", title = "Cloud Only Hits", trackCount = 1)
        val song1 = createTestSong("yt_1", "Song Title 1", "vid1")
        val matchResults = listOf(
            MatchResult(originalTrack = playlist.tracks[0], matchedSong = song1)
        )

        coEvery {
            spotifyMatchingEngine.matchTracks(
                tracks = playlist.tracks,
                matchLocal = false,
                matchCloud = true,
                onProgress = any()
            )
        } returns matchResults

        viewModel.saveSpotifyPlaylist(
            playlist = playlist,
            customTitle = null,
            saveAsCloud = true,
            saveAsLocal = false
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val targetPlaylistId = "spotify_spot456"
        coVerify { youTubeDao.insertPlaylist(match { it.id == targetPlaylistId && it.name == "Cloud Only Hits" }) }
        coVerify(exactly = 0) { playlistPreferencesRepository.createPlaylist(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }

        val state = viewModel.spotifyImportState.value
        assertTrue(state is SpotifyImportState.Success)
        assertEquals("Cloud Only Hits", (state as SpotifyImportState.Success).playlistTitle)
    }

    @Test
    fun `saveSpotifyPlaylist saves only to local when saveAsCloud is false`() = runTest {
        val playlist = createTestSpotifyPlaylist(id = "spot789", title = "Local Only Hits", trackCount = 1)
        val song1 = createTestSong("local_1", "Song Title 1")
        val matchResults = listOf(
            MatchResult(originalTrack = playlist.tracks[0], matchedSong = song1, isLocalMatch = true)
        )

        coEvery {
            spotifyMatchingEngine.matchTracks(
                tracks = playlist.tracks,
                matchLocal = true,
                matchCloud = false,
                onProgress = any()
            )
        } returns matchResults

        viewModel.saveSpotifyPlaylist(
            playlist = playlist,
            customTitle = "Custom Local Hits",
            saveAsCloud = false,
            saveAsLocal = true
        )
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { youTubeDao.insertPlaylist(any()) }
        coVerify {
            playlistPreferencesRepository.createPlaylist(
                name = "Custom Local Hits",
                songIds = listOf("local_1"),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        }

        val state = viewModel.spotifyImportState.value
        assertTrue(state is SpotifyImportState.Success)
        assertEquals("Custom Local Hits", (state as SpotifyImportState.Success).playlistTitle)
    }

    @Test
    fun `saveSpotifyPlaylist emits Error when exception occurs`() = runTest {
        val playlist = createTestSpotifyPlaylist()
        coEvery {
            spotifyMatchingEngine.matchTracks(any(), any(), any(), any())
        } throws RuntimeException("Matching engine failure")

        viewModel.saveSpotifyPlaylist(playlist)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.spotifyImportState.value
        assertTrue(state is SpotifyImportState.Error)
        assertEquals("Matching engine failure", (state as SpotifyImportState.Error).message)
    }

    @Test
    fun `resetSpotifyImportState resets state to Idle`() {
        every { spotifyPlaylistFetcher.extractPlaylistId("invalid") } returns null
        viewModel.previewSpotifyPlaylist("invalid")
        assertTrue(viewModel.spotifyImportState.value is SpotifyImportState.Error)

        viewModel.resetSpotifyImportState()
        assertEquals(SpotifyImportState.Idle, viewModel.spotifyImportState.value)
    }
}
