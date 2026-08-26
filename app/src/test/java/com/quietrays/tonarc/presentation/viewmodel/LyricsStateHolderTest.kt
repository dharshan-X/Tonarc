package com.quietrays.tonarc.presentation.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quietrays.tonarc.data.media.SongMetadataEditor
import com.quietrays.tonarc.data.model.Lyrics
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.preferences.UserPreferencesRepository
import com.quietrays.tonarc.data.repository.MusicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsStateHolderTest {

    @Test
    fun withPersistedLyrics_replacesAlbumArtUriWhenMetadataWriteRefreshesArtworkPath() {
        val originalSong = testSong(albumArtUriString = "file:///cache/song_art_1_old.jpg")

        val updatedSong = originalSong.withPersistedLyrics(
            rawLyrics = "New lyrics",
            refreshedAlbumArtUri = "file:///cache/song_art_1_new.jpg"
        )

        assertThat(updatedSong.lyrics).isEqualTo("New lyrics")
        assertThat(updatedSong.albumArtUriString).isEqualTo("file:///cache/song_art_1_new.jpg")
    }

    @Test
    fun withPersistedLyrics_keepsExistingAlbumArtUriWhenMetadataWriteDoesNotReturnOne() {
        val originalSong = testSong(albumArtUriString = "content://art/song_art_1.jpg")

        val updatedSong = originalSong.withPersistedLyrics(
            rawLyrics = "Imported lyrics",
            refreshedAlbumArtUri = null
        )

        assertThat(updatedSong.lyrics).isEqualTo("Imported lyrics")
        assertThat(updatedSong.albumArtUriString).isEqualTo("content://art/song_art_1.jpg")
    }

    @Test
    fun fetchLyricsForSong_usesStoredLyricsWithoutRemoteFetch() = runTest {
        val musicRepository = mockk<MusicRepository>(relaxed = true)
        val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        val songMetadataEditor = mockk<SongMetadataEditor>(relaxed = true)
        val holder = LyricsStateHolder(
            musicRepository = musicRepository,
            userPreferencesRepository = userPreferencesRepository,
            songMetadataEditor = songMetadataEditor
        )
        val callback = RecordingLyricsLoadCallback()
        val state = MutableStateFlow(StablePlayerState())
        val song = testSong(albumArtUriString = "content://art/song_art_1.jpg").copy(
            lyrics = "Stored lyrics"
        )
        val storedLyrics = Lyrics(plain = listOf("Stored lyrics"), areFromRemote = false)

        holder.initialize(backgroundScope, callback, state)
        coEvery { musicRepository.getStoredLyrics(song) } returns (storedLyrics to "Stored lyrics")

        holder.searchUiState.test {
            assertThat(awaitItem()).isEqualTo(LyricsSearchUiState.Idle)

            holder.fetchLyricsForSong(
                song = song,
                forcePickResults = false,
                sourcePreference = com.quietrays.tonarc.data.model.LyricsSourcePreference.API_FIRST
            ) { "Lyrics already available" }

            assertThat(awaitItem()).isEqualTo(LyricsSearchUiState.Loading)
            assertThat(awaitItem()).isEqualTo(LyricsSearchUiState.Success(storedLyrics))
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(holder.searchUiState.value).isEqualTo(LyricsSearchUiState.Success(storedLyrics))
        coVerify(exactly = 1) { musicRepository.getStoredLyrics(song) }
        coVerify(exactly = 0) { musicRepository.getLyricsFromRemote(any()) }
        coVerify(exactly = 0) { musicRepository.searchRemoteLyrics(any()) }
    }

    @Test
    fun songChange_automaticallyTriggersLyricsResolutionInBackground() = runTest {
        val musicRepository = mockk<MusicRepository>(relaxed = true)
        val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        val songMetadataEditor = mockk<SongMetadataEditor>(relaxed = true)
        val holder = LyricsStateHolder(
            musicRepository = musicRepository,
            userPreferencesRepository = userPreferencesRepository,
            songMetadataEditor = songMetadataEditor,
            ioDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        )
        val callback = RecordingLyricsLoadCallback()
        val state = MutableStateFlow(StablePlayerState())
        val song = testSong(albumArtUriString = "content://art/song_art_1.jpg")
        val lyrics = Lyrics(plain = listOf("Background preloaded lyrics"), areFromRemote = true)

        io.mockk.every { userPreferencesRepository.lyricsSourcePreferenceFlow } returns kotlinx.coroutines.flow.flowOf(com.quietrays.tonarc.data.model.LyricsSourcePreference.EMBEDDED_FIRST)
        coEvery { musicRepository.getLyrics(song, any(), any()) } returns lyrics

        holder.initialize(backgroundScope, callback, state)

        // Set new playing song
        state.value = StablePlayerState(currentSong = song)

        // Advance virtual time past 300ms debounce and execute pending tasks
        testScheduler.advanceTimeBy(350L)
        testScheduler.advanceUntilIdle()

        assertThat(holder.lyricsState.value).isEqualTo(lyrics)
        assertThat(callback.startedSongs).contains("1")
        assertThat(callback.loadedLyrics["1"]).isEqualTo(lyrics)
        coVerify(atLeast = 1) { musicRepository.getLyrics(song, any(), any()) }
    }

    @Test
    fun rapidSongSkips_debouncesAndOnlyFetchesFinalSong() = runTest {
        val musicRepository = mockk<MusicRepository>(relaxed = true)
        val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        val songMetadataEditor = mockk<SongMetadataEditor>(relaxed = true)
        val holder = LyricsStateHolder(
            musicRepository = musicRepository,
            userPreferencesRepository = userPreferencesRepository,
            songMetadataEditor = songMetadataEditor,
            ioDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        )
        val callback = RecordingLyricsLoadCallback()
        val state = MutableStateFlow(StablePlayerState())

        val song1 = testSong(albumArtUriString = null).copy(id = "1", title = "Song 1")
        val song2 = testSong(albumArtUriString = null).copy(id = "2", title = "Song 2")
        val song3 = testSong(albumArtUriString = null).copy(id = "3", title = "Song 3")
        val lyrics3 = Lyrics(plain = listOf("Song 3 Lyrics"), areFromRemote = true)

        io.mockk.every { userPreferencesRepository.lyricsSourcePreferenceFlow } returns kotlinx.coroutines.flow.flowOf(com.quietrays.tonarc.data.model.LyricsSourcePreference.EMBEDDED_FIRST)
        coEvery { musicRepository.getLyrics(song3, any(), any()) } returns lyrics3

        holder.initialize(backgroundScope, callback, state)

        // Rapid skipping
        state.value = StablePlayerState(currentSong = song1)
        testScheduler.advanceTimeBy(100L)

        state.value = StablePlayerState(currentSong = song2)
        testScheduler.advanceTimeBy(100L)

        state.value = StablePlayerState(currentSong = song3)
        testScheduler.advanceTimeBy(350L)
        testScheduler.advanceUntilIdle()

        // Only song 3 should have triggered fetch
        coVerify(exactly = 0) { musicRepository.getLyrics(song1, any(), any()) }
        coVerify(exactly = 0) { musicRepository.getLyrics(song2, any(), any()) }
        coVerify(exactly = 1) { musicRepository.getLyrics(song3, any(), any()) }

        assertThat(holder.lyricsState.value).isEqualTo(lyrics3)
        assertThat(callback.loadedLyrics["3"]).isEqualTo(lyrics3)
    }

    @Test
    fun nullOrEmptySong_clearsLyricsState() = runTest {
        val musicRepository = mockk<MusicRepository>(relaxed = true)
        val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        val songMetadataEditor = mockk<SongMetadataEditor>(relaxed = true)
        val holder = LyricsStateHolder(
            musicRepository = musicRepository,
            userPreferencesRepository = userPreferencesRepository,
            songMetadataEditor = songMetadataEditor,
            ioDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        )
        val callback = RecordingLyricsLoadCallback()
        val state = MutableStateFlow(StablePlayerState())
        val song = testSong(albumArtUriString = null)
        val lyrics = Lyrics(plain = listOf("Some lyrics"), areFromRemote = true)

        io.mockk.every { userPreferencesRepository.lyricsSourcePreferenceFlow } returns kotlinx.coroutines.flow.flowOf(com.quietrays.tonarc.data.model.LyricsSourcePreference.EMBEDDED_FIRST)
        coEvery { musicRepository.getLyrics(song, any(), any()) } returns lyrics

        holder.initialize(backgroundScope, callback, state)

        state.value = StablePlayerState(currentSong = song)
        testScheduler.advanceTimeBy(350L)
        testScheduler.advanceUntilIdle()
        assertThat(holder.lyricsState.value).isEqualTo(lyrics)

        state.value = StablePlayerState(currentSong = null)
        testScheduler.advanceTimeBy(350L)
        testScheduler.advanceUntilIdle()
        assertThat(holder.lyricsState.value).isNull()
    }

    @Test
    fun sameSongPlaybackStateUpdate_doesNotReFetchLyrics() = runTest {
        val musicRepository = mockk<MusicRepository>(relaxed = true)
        val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        val songMetadataEditor = mockk<SongMetadataEditor>(relaxed = true)
        val holder = LyricsStateHolder(
            musicRepository = musicRepository,
            userPreferencesRepository = userPreferencesRepository,
            songMetadataEditor = songMetadataEditor,
            ioDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        )
        val callback = RecordingLyricsLoadCallback()
        val song = testSong(albumArtUriString = null)
        val lyrics = Lyrics(plain = listOf("Cached lyrics"), areFromRemote = true)
        val state = MutableStateFlow(StablePlayerState(currentSong = song, isPlaying = false))

        io.mockk.every { userPreferencesRepository.lyricsSourcePreferenceFlow } returns kotlinx.coroutines.flow.flowOf(com.quietrays.tonarc.data.model.LyricsSourcePreference.EMBEDDED_FIRST)
        coEvery { musicRepository.getLyrics(song, any(), any()) } returns lyrics

        holder.initialize(backgroundScope, callback, state)
        testScheduler.advanceTimeBy(350L)
        testScheduler.advanceUntilIdle()
        assertThat(holder.lyricsState.value).isEqualTo(lyrics)

        // Player state changes playing / buffering but song stays same
        state.value = state.value.copy(isPlaying = true, isBuffering = true)
        testScheduler.advanceTimeBy(350L)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { musicRepository.getLyrics(song, any(), any()) }
    }

    private fun testSong(albumArtUriString: String?): Song {
        return Song(
            id = "1",
            title = "Indian Summer",
            artist = "Blood Cultures",
            album = "Happy Birthday",
            path = "/music/indian-summer.mp3",
            contentUriString = "content://media/external/audio/media/1",
            albumArtUriString = albumArtUriString,
            duration = 295_000L,
            mimeType = "audio/mpeg",
            bitrate = 320_000,
            sampleRate = 44_100,
            artistId = 1L,
            albumId = 1L
        )
    }

    private class RecordingLyricsLoadCallback : LyricsLoadCallback {
        val startedSongs = mutableListOf<String>()
        val loadedLyrics = mutableMapOf<String, Lyrics?>()

        override fun onLoadingStarted(songId: String) {
            startedSongs.add(songId)
        }

        override fun onLyricsLoaded(songId: String, lyrics: Lyrics?) {
            loadedLyrics[songId] = lyrics
        }
    }
}
