package com.quietrays.tonarc.presentation.viewmodel

import android.content.Context
import com.quietrays.tonarc.data.DailyMixManager
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.offline.CloudOfflineRepository
import com.quietrays.tonarc.data.playlist.M3uManager
import com.quietrays.tonarc.data.playlist.NlpPlaylistGenerator
import com.quietrays.tonarc.data.preferences.PlaylistPreferencesRepository
import com.quietrays.tonarc.data.repository.MusicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
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
    private val youTubeRepository: com.quietrays.tonarc.data.youtube.YouTubeRepository = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: PlaylistViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        io.mockk.every { playlistPreferencesRepository.playlistsSortOptionFlow } returns kotlinx.coroutines.flow.flowOf("NAME_A_TO_Z")
        io.mockk.every { playlistPreferencesRepository.userPlaylistsFlow } returns kotlinx.coroutines.flow.flowOf(emptyList())
        io.mockk.every { playlistPreferencesRepository.playlistSongOrderModesFlow } returns kotlinx.coroutines.flow.flowOf(emptyMap())

        viewModel = PlaylistViewModel(
            playlistPreferencesRepository,
            musicRepository,
            dailyMixManager,
            m3uManager,
            nlpPlaylistGenerator,
            cloudOfflineRepository,
            youTubeRepository,
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
}
