package com.quietrays.tonarc.presentation.viewmodel

import android.content.Context
import com.quietrays.tonarc.data.DailyMixManager
import com.quietrays.tonarc.data.database.YouTubeDao
import com.quietrays.tonarc.data.database.YouTubePlaylistEntity
import com.quietrays.tonarc.data.database.YouTubeSongEntity
import com.quietrays.tonarc.data.model.Playlist
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.network.youtube.SyncState
import com.quietrays.tonarc.data.network.youtube.YouTubeLibrarySyncEngine
import com.quietrays.tonarc.data.offline.CloudOfflineRepository
import com.quietrays.tonarc.data.playlist.M3uManager
import com.quietrays.tonarc.data.playlist.NlpPlaylistGenerator
import com.quietrays.tonarc.data.preferences.PlaylistPreferencesRepository
import com.quietrays.tonarc.data.repository.MusicRepository
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModelTest {

    private val playlistPreferencesRepository: PlaylistPreferencesRepository = mockk(relaxed = true)
    private val musicRepository: MusicRepository = mockk(relaxed = true)
    private val dailyMixManager: DailyMixManager = mockk(relaxed = true)
    private val m3uManager: M3uManager = mockk(relaxed = true)
    private val nlpPlaylistGenerator: NlpPlaylistGenerator = mockk(relaxed = true)
    private val cloudOfflineRepository: CloudOfflineRepository = mockk(relaxed = true)
    private val youTubeRepository: YouTubeRepository = mockk(relaxed = true)
    private val youTubeLibrarySyncEngine: YouTubeLibrarySyncEngine = mockk(relaxed = true)
    private val youTubeDao: YouTubeDao = mockk(relaxed = true)
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
            context
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `downloadPlaylist enqueues cloud songs`() = runTest {
        val cloudSong1 = Song.emptySong().copy(id = "youtube_1", contentUriString = "youtube://vid1", title = "Cloud 1")
        val cloudSong2 = Song.emptySong().copy(id = "youtube_2", contentUriString = "youtube://vid2", title = "Cloud 2")
        val localSong = Song.emptySong().copy(id = "local_1", contentUriString = "content://media/1", title = "Local")

        viewModel.downloadPlaylist(listOf(cloudSong1, cloudSong2, localSong))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { cloudOfflineRepository.enqueueAll(listOf(cloudSong1, cloudSong2)) }
    }

    @Test
    fun `combines local and YouTube playlists and pins ytm_liked_music`() = runTest {
        val local = Playlist(id = "local_1", name = "Zebra Local", songIds = emptyList())
        val ytCustom = YouTubePlaylistEntity(id = "yt_1", name = "Alpha YouTube", songCount = 5)
        val ytLiked = YouTubePlaylistEntity(id = "ytm_liked_music", name = "Liked Music", songCount = 10)

        userPlaylistsFlow.value = listOf(local)
        ytPlaylistsFlow.value = listOf(ytCustom, ytLiked)
        testDispatcher.scheduler.advanceUntilIdle()

        val playlists = viewModel.uiState.value.playlists
        assertEquals(3, playlists.size)
        assertEquals("ytm_liked_music", playlists[0].id)
        assertEquals("Liked Music", playlists[0].name)
        assertEquals("YOUTUBE", playlists[0].source)
        assertEquals("yt_1", playlists[1].id)
        assertEquals("local_1", playlists[2].id)
    }

    @Test
    fun `loadPlaylistDetails loads liked music from youTubeDao`() = runTest {
        val likedEntity = YouTubePlaylistEntity(id = "ytm_liked_music", name = "Liked Music", songCount = 1)
        val songEntity = YouTubeSongEntity(
            id = "youtube_abc",
            videoId = "abc",
            playlistId = "__liked_music__",
            title = "Liked Song",
            artist = "Artist",
            thumbnailUrl = "https://img.jpg"
        )
        coEvery { youTubeDao.getPlaylistById("ytm_liked_music") } returns likedEntity
        every { youTubeDao.getSongsByPlaylist("__liked_music__") } returns flowOf(listOf(songEntity))

        viewModel.loadPlaylistDetails("ytm_liked_music")
        testDispatcher.scheduler.advanceUntilIdle()

        val details = viewModel.uiState.value.currentPlaylistDetails
        val songs = viewModel.uiState.value.currentPlaylistSongs

        assertEquals("ytm_liked_music", details?.id)
        assertEquals("Liked Music", details?.name)
        assertEquals("YOUTUBE", details?.source)
        assertEquals(1, songs.size)
        assertEquals("Liked Song", songs[0].title)
    }

    @Test
    fun `loadPlaylistDetails loads cached YouTube playlist`() = runTest {
        val ytPlaylist = YouTubePlaylistEntity(id = "PL123", name = "My YT Playlist", songCount = 1)
        val songEntity = YouTubeSongEntity(
            id = "youtube_xyz",
            videoId = "xyz",
            playlistId = "PL123",
            title = "Track 1",
            artist = "Artist 1"
        )
        coEvery { youTubeDao.getPlaylistById("PL123") } returns ytPlaylist
        every { youTubeDao.getSongsByPlaylist("PL123") } returns flowOf(listOf(songEntity))

        viewModel.loadPlaylistDetails("PL123")
        testDispatcher.scheduler.advanceUntilIdle()

        val details = viewModel.uiState.value.currentPlaylistDetails
        val songs = viewModel.uiState.value.currentPlaylistSongs

        assertEquals("PL123", details?.id)
        assertEquals("My YT Playlist", details?.name)
        assertEquals("YOUTUBE", details?.source)
        assertEquals(1, songs.size)
        assertEquals("Track 1", songs[0].title)
    }

    @Test
    fun `syncYouTubeLibrary delegates to sync engine`() = runTest {
        viewModel.syncYouTubeLibrary(force = true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { youTubeLibrarySyncEngine.syncLibrary(true) }
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
            albumArtUriString = "https://img.jpg",
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
    fun `extractYouTubePlaylistId handles urls and raw ids`() {
        assertEquals("PL12345", viewModel.extractYouTubePlaylistId("PL12345"))
        assertEquals("PL12345", viewModel.extractYouTubePlaylistId("https://music.youtube.com/playlist?list=PL12345"))
        assertEquals("PL12345", viewModel.extractYouTubePlaylistId("https://www.youtube.com/playlist?list=PL12345&si=abc"))
        assertEquals("VLPL12345", viewModel.extractYouTubePlaylistId("VLPL12345"))
    }

    @Test
    fun `previewYouTubePlaylist populates preview state on success`() = runTest {
        val sampleSong = createTestSong("youtube_test1", "Imported Song", "test1")
        val samplePlaylist = Playlist(
            id = "PL999",
            name = "Awesome Mix",
            songIds = listOf("youtube_test1"),
            coverImageUri = "https://img.jpg",
            source = "YOUTUBE"
        )

        coEvery { youTubeRepository.getPlaylist("PL999") } returns Pair(samplePlaylist, listOf(sampleSong))

        viewModel.previewYouTubePlaylist("https://music.youtube.com/playlist?list=PL999")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.youtubeImportState.value
        assert(state is YouTubeImportState.Preview)
        val preview = state as YouTubeImportState.Preview
        assertEquals("PL999", preview.playlistId)
        assertEquals("Awesome Mix", preview.title)
        assertEquals(1, preview.trackCount)
    }

    @Test
    fun `saveYouTubePlaylist saves entities into youTubeDao`() = runTest {
        val sampleSong = createTestSong("youtube_test1", "Imported Song", "test1")
        val samplePlaylist = Playlist(
            id = "PL999",
            name = "Awesome Mix",
            songIds = listOf("youtube_test1"),
            coverImageUri = "https://img.jpg",
            source = "YOUTUBE"
        )

        coEvery { youTubeRepository.getPlaylist("PL999") } returns Pair(samplePlaylist, listOf(sampleSong))

        viewModel.saveYouTubePlaylist("PL999", customTitle = "My Custom Mix", saveAsLocal = false)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { youTubeDao.insertPlaylist(match { it.id == "PL999" && it.name == "My Custom Mix" }) }
        coVerify { youTubeDao.insertSongs(match { it.size == 1 && it[0].videoId == "test1" }) }

        val state = viewModel.youtubeImportState.value
        assert(state is YouTubeImportState.Success)
    }
}
