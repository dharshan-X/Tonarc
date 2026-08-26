package com.quietrays.tonarc.data.network.youtube

import com.google.common.truth.Truth.assertThat
import com.quietrays.tonarc.data.database.YouTubeDao
import com.quietrays.tonarc.data.database.YouTubePlaylistEntity
import com.quietrays.tonarc.data.database.YouTubeSongEntity
import com.quietrays.tonarc.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.sql.SQLException

@OptIn(ExperimentalCoroutinesApi::class)
class YouTubeLibrarySyncEngineTest {

    private lateinit var innertubeApiService: InnertubeApiService
    private lateinit var youTubeDao: YouTubeDao
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var syncEngine: YouTubeLibrarySyncEngine

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        innertubeApiService = mockk(relaxed = true)
        youTubeDao = mockk(relaxed = true)
        userPreferencesRepository = mockk(relaxed = true)

        syncEngine = YouTubeLibrarySyncEngine(
            innertubeApiService = innertubeApiService,
            youTubeDao = youTubeDao,
            userPreferencesRepository = userPreferencesRepository,
            scope = CoroutineScope(testDispatcher)
        )
    }

    @Test
    fun `initial sync state is Idle`() {
        assertThat(syncEngine.syncState.value).isEqualTo(SyncState.Idle)
    }

    @Test
    fun `syncLibrary returns failure and emits Error state when authCookies is null`() = runTest {
        every { innertubeApiService.authCookies } returns null

        val result = syncEngine.syncLibrary()

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("YouTube Music account not connected")
        assertThat(syncEngine.syncState.value).isEqualTo(SyncState.Error("YouTube Music account not connected"))
        coVerify(exactly = 0) { youTubeDao.deleteSongsByPlaylist(any()) }
        coVerify(exactly = 0) { youTubeDao.insertSongs(any()) }
        coVerify(exactly = 0) { youTubeDao.insertPlaylist(any()) }
        coVerify(exactly = 0) { youTubeDao.insertPlaylists(any()) }
    }

    @Test
    fun `syncLibrary returns failure and emits Error state when authCookies is blank`() = runTest {
        every { innertubeApiService.authCookies } returns "   "

        val result = syncEngine.syncLibrary()

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("YouTube Music account not connected")
        assertThat(syncEngine.syncState.value).isEqualTo(SyncState.Error("YouTube Music account not connected"))
    }

    @Test
    fun `syncLibrary successfully syncs liked songs and playlists`() = runTest {
        every { innertubeApiService.authCookies } returns "SID=abc; HSID=def"

        val track1 = InnertubeTrack(
            videoId = "vid1",
            title = "Song One",
            artist = "Artist A",
            artists = listOf("Artist A", "Artist B"),
            album = "Album 1",
            durationSeconds = 180L,
            thumbnailUri = "https://example.com/art1.jpg"
        )
        val track2 = InnertubeTrack(
            videoId = "vid2",
            title = "Song Two",
            artist = "Solo Artist",
            artists = emptyList(),
            album = null,
            durationSeconds = 240L,
            thumbnailUri = "https://example.com/art2.jpg"
        )

        val playlist1 = InnertubePlaylist(
            playlistId = "PL123",
            title = "Favorites Mix",
            author = "User",
            trackCount = 25,
            thumbnailUri = "https://example.com/pl1.jpg"
        )

        coEvery { innertubeApiService.getLikedSongs(null) } returns (listOf(track1, track2) to null)
        coEvery { innertubeApiService.getUserPlaylists(null) } returns (listOf(playlist1) to null)

        val result = syncEngine.syncLibrary()

        assertThat(result.isSuccess).isTrue()
        val summary = result.getOrNull()
        assertThat(summary).isNotNull()
        assertThat(summary!!.likedCount).isEqualTo(2)
        assertThat(summary.playlistCount).isEqualTo(1)

        val state = syncEngine.syncState.value
        assertThat(state).isInstanceOf(SyncState.Success::class.java)
        val successState = state as SyncState.Success
        assertThat(successState.likedCount).isEqualTo(2)
        assertThat(successState.playlistCount).isEqualTo(1)

        val songsSlot = slot<List<YouTubeSongEntity>>()
        val likedPlaylistSlot = slot<YouTubePlaylistEntity>()
        val playlistsSlot = slot<List<YouTubePlaylistEntity>>()

        coVerifyOrder {
            youTubeDao.deleteSongsByPlaylist("__liked_music__")
            youTubeDao.insertSongs(capture(songsSlot))
            youTubeDao.insertPlaylist(capture(likedPlaylistSlot))
            youTubeDao.insertPlaylists(capture(playlistsSlot))
        }

        val insertedSongs = songsSlot.captured
        assertThat(insertedSongs).hasSize(2)
        assertThat(insertedSongs[0].id).isEqualTo("youtube_vid1")
        assertThat(insertedSongs[0].videoId).isEqualTo("vid1")
        assertThat(insertedSongs[0].playlistId).isEqualTo("__liked_music__")
        assertThat(insertedSongs[0].title).isEqualTo("Song One")
        assertThat(insertedSongs[0].artist).isEqualTo("Artist A, Artist B")
        assertThat(insertedSongs[0].album).isEqualTo("Album 1")
        assertThat(insertedSongs[0].duration).isEqualTo(180_000L)
        assertThat(insertedSongs[0].thumbnailUrl).isEqualTo("https://example.com/art1.jpg")

        assertThat(insertedSongs[1].id).isEqualTo("youtube_vid2")
        assertThat(insertedSongs[1].videoId).isEqualTo("vid2")
        assertThat(insertedSongs[1].artist).isEqualTo("Solo Artist")
        assertThat(insertedSongs[1].album).isEqualTo("Liked Music")
        assertThat(insertedSongs[1].duration).isEqualTo(240_000L)

        val insertedLikedPlaylist = likedPlaylistSlot.captured
        assertThat(insertedLikedPlaylist.id).isEqualTo("ytm_liked_music")
        assertThat(insertedLikedPlaylist.name).isEqualTo("Liked Music")
        assertThat(insertedLikedPlaylist.author).isEqualTo("YouTube Music")
        assertThat(insertedLikedPlaylist.songCount).isEqualTo(2)
        assertThat(insertedLikedPlaylist.thumbnailUrl).isEqualTo("https://example.com/art1.jpg")

        val insertedPlaylists = playlistsSlot.captured
        assertThat(insertedPlaylists).hasSize(1)
        assertThat(insertedPlaylists[0].id).isEqualTo("PL123")
        assertThat(insertedPlaylists[0].name).isEqualTo("Favorites Mix")
        assertThat(insertedPlaylists[0].author).isEqualTo("User")
        assertThat(insertedPlaylists[0].songCount).isEqualTo(25)
        assertThat(insertedPlaylists[0].thumbnailUrl).isEqualTo("https://example.com/pl1.jpg")
    }

    @Test
    fun `syncLibrary paginates liked songs and playlists through multiple pages`() = runTest {
        every { innertubeApiService.authCookies } returns "valid_cookies"

        val page1Track = InnertubeTrack(videoId = "v1", title = "T1", artist = "A1")
        val page2Track = InnertubeTrack(videoId = "v2", title = "T2", artist = "A2")
        val page3Track = InnertubeTrack(videoId = "v3", title = "T3", artist = "A3")

        coEvery { innertubeApiService.getLikedSongs(null) } returns (listOf(page1Track) to "liked_page_2")
        coEvery { innertubeApiService.getLikedSongs("liked_page_2") } returns (listOf(page2Track) to "liked_page_3")
        coEvery { innertubeApiService.getLikedSongs("liked_page_3") } returns (listOf(page3Track) to null)

        val page1Playlist = InnertubePlaylist(playlistId = "p1", title = "P1")
        val page2Playlist = InnertubePlaylist(playlistId = "p2", title = "P2")

        coEvery { innertubeApiService.getUserPlaylists(null) } returns (listOf(page1Playlist) to "pl_page_2")
        coEvery { innertubeApiService.getUserPlaylists("pl_page_2") } returns (listOf(page2Playlist) to null)

        val result = syncEngine.syncLibrary()

        assertThat(result.isSuccess).isTrue()
        val summary = result.getOrNull()!!
        assertThat(summary.likedCount).isEqualTo(3)
        assertThat(summary.playlistCount).isEqualTo(2)

        val songsSlot = slot<List<YouTubeSongEntity>>()
        val playlistsSlot = slot<List<YouTubePlaylistEntity>>()

        coVerify {
            innertubeApiService.getLikedSongs(null)
            innertubeApiService.getLikedSongs("liked_page_2")
            innertubeApiService.getLikedSongs("liked_page_3")
            innertubeApiService.getUserPlaylists(null)
            innertubeApiService.getUserPlaylists("pl_page_2")
            youTubeDao.insertSongs(capture(songsSlot))
            youTubeDao.insertPlaylists(capture(playlistsSlot))
        }

        assertThat(songsSlot.captured).hasSize(3)
        assertThat(playlistsSlot.captured).hasSize(2)
    }

    @Test
    fun `syncLibrary stops pagination if continuation token repeats`() = runTest {
        every { innertubeApiService.authCookies } returns "valid_cookies"

        val track = InnertubeTrack(videoId = "v1", title = "T1", artist = "A1")
        coEvery { innertubeApiService.getLikedSongs(null) } returns (listOf(track) to "loop_token")
        coEvery { innertubeApiService.getLikedSongs("loop_token") } returns (listOf(track) to "loop_token")
        coEvery { innertubeApiService.getUserPlaylists(null) } returns (emptyList<InnertubePlaylist>() to null)

        val result = syncEngine.syncLibrary()

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.likedCount).isEqualTo(2)
        coVerify(exactly = 1) { innertubeApiService.getLikedSongs("loop_token") }
    }

    @Test
    fun `syncLibrary handles network exception in getLikedSongs and emits Error state`() = runTest {
        every { innertubeApiService.authCookies } returns "valid_cookies"
        coEvery { innertubeApiService.getLikedSongs(null) } throws IOException("Connection timed out")

        val result = syncEngine.syncLibrary()

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IOException::class.java)
        assertThat(result.exceptionOrNull()?.message).isEqualTo("Connection timed out")
        assertThat(syncEngine.syncState.value).isEqualTo(SyncState.Error("Connection timed out"))
    }

    @Test
    fun `syncLibrary handles network exception in getUserPlaylists and emits Error state`() = runTest {
        every { innertubeApiService.authCookies } returns "valid_cookies"
        coEvery { innertubeApiService.getLikedSongs(null) } returns (listOf(InnertubeTrack("v1", "T", "A")) to null)
        coEvery { innertubeApiService.getUserPlaylists(null) } throws IOException("502 Bad Gateway")

        val result = syncEngine.syncLibrary()

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("502 Bad Gateway")
        assertThat(syncEngine.syncState.value).isEqualTo(SyncState.Error("502 Bad Gateway"))
    }

    @Test
    fun `syncLibrary handles database error and emits Error state`() = runTest {
        every { innertubeApiService.authCookies } returns "valid_cookies"
        coEvery { innertubeApiService.getLikedSongs(null) } returns (listOf(InnertubeTrack("v1", "T", "A")) to null)
        coEvery { youTubeDao.deleteSongsByPlaylist(any()) } throws SQLException("Disk full")

        val result = syncEngine.syncLibrary()

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("Disk full")
        assertThat(syncEngine.syncState.value).isEqualTo(SyncState.Error("Disk full"))
    }

    @Test
    fun `syncLibrary handles empty library cleanly`() = runTest {
        every { innertubeApiService.authCookies } returns "valid_cookies"
        coEvery { innertubeApiService.getLikedSongs(null) } returns (emptyList<InnertubeTrack>() to null)
        coEvery { innertubeApiService.getUserPlaylists(null) } returns (emptyList<InnertubePlaylist>() to null)

        val result = syncEngine.syncLibrary()

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.likedCount).isEqualTo(0)
        assertThat(result.getOrNull()?.playlistCount).isEqualTo(0)
        assertThat(syncEngine.syncState.value).isInstanceOf(SyncState.Success::class.java)

        coVerify {
            youTubeDao.deleteSongsByPlaylist("__liked_music__")
            youTubeDao.insertPlaylist(match { it.id == "ytm_liked_music" && it.songCount == 0 && it.thumbnailUrl == null })
        }
        coVerify(exactly = 0) { youTubeDao.insertSongs(any()) }
        coVerify(exactly = 0) { youTubeDao.insertPlaylists(any()) }
    }
}
