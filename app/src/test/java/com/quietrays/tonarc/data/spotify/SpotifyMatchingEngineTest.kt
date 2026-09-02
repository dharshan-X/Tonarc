package com.quietrays.tonarc.data.spotify

import com.google.common.truth.Truth.assertThat
import com.quietrays.tonarc.data.model.ArtistRef
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.network.spotify.SpotifyTrack
import com.quietrays.tonarc.data.network.youtube.InnertubeApiService
import com.quietrays.tonarc.data.network.youtube.InnertubeSearchResult
import com.quietrays.tonarc.data.network.youtube.InnertubeTrack
import com.quietrays.tonarc.data.repository.MusicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

class SpotifyMatchingEngineTest {

    private lateinit var innertubeApiService: InnertubeApiService
    private lateinit var musicRepository: MusicRepository
    private lateinit var engine: SpotifyMatchingEngine

    @BeforeEach
    fun setUp() {
        innertubeApiService = mockk(relaxed = true)
        musicRepository = mockk(relaxed = true)
        engine = SpotifyMatchingEngine(
            innertubeApiService = innertubeApiService,
            musicRepository = musicRepository
        )
    }

    private fun createLocalSong(
        id: String = "local_1",
        title: String = "Blinding Lights",
        artist: String = "The Weeknd",
        artists: List<ArtistRef> = emptyList(),
        duration: Long = 200_000L
    ): Song {
        return Song.emptySong().copy(
            id = id,
            title = title,
            artist = artist,
            artists = artists,
            duration = duration,
            path = "/storage/emulated/0/Music/$title.mp3",
            contentUriString = "content://media/external/audio/media/1"
        )
    }

    private fun createSpotifyTrack(
        id: String = "spot_1",
        title: String = "Blinding Lights",
        artist: String = "The Weeknd",
        artists: List<String> = listOf(artist),
        durationMs: Long = 200_000L
    ): SpotifyTrack {
        return SpotifyTrack(
            id = id,
            title = title,
            artist = artist,
            artists = artists,
            durationMs = durationMs
        )
    }

    private fun createInnertubeTrack(
        videoId: String = "yt_123",
        title: String = "Blinding Lights",
        artist: String = "The Weeknd",
        artists: List<String> = listOf(artist),
        durationSeconds: Long = 200L
    ): InnertubeTrack {
        return InnertubeTrack(
            videoId = videoId,
            title = title,
            artist = artist,
            artists = artists,
            durationSeconds = durationSeconds,
            thumbnailUri = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        )
    }

    @Test
    fun `matchTracks returns empty list when input tracks are empty`() = runTest {
        val result = engine.matchTracks(emptyList())
        assertThat(result).isEmpty()
        coVerify(exactly = 0) { musicRepository.getAllSongsOnce() }
    }

    @Test
    fun `matchTracks matches local song when title and artist match within duration tolerance`() = runTest {
        val localSong = createLocalSong(
            id = "song_1",
            title = "Blinding Lights",
            artist = "The Weeknd",
            duration = 200_000L
        )
        coEvery { musicRepository.getAllSongsOnce() } returns listOf(localSong)

        val spotifyTrack = createSpotifyTrack(
            id = "sp_1",
            title = "Blinding Lights",
            artist = "The Weeknd",
            durationMs = 205_000L // 5s difference (within 12s)
        )

        val results = engine.matchTracks(listOf(spotifyTrack))

        assertThat(results).hasSize(1)
        val match = results[0]
        assertThat(match.isLocalMatch).isTrue()
        assertThat(match.originalTrack).isEqualTo(spotifyTrack)
        assertThat(match.matchedSong).isEqualTo(localSong)
        assertThat(match.matchedYouTubeTrack).isNull()

        // Cloud search should not be called for locally matched tracks
        coVerify(exactly = 0) { innertubeApiService.search(any(), any(), any()) }
    }

    @Test
    fun `matchTracks falls back to cloud when local song duration exceeds 12s tolerance`() = runTest {
        val localSong = createLocalSong(
            id = "song_1",
            title = "Blinding Lights",
            artist = "The Weeknd",
            duration = 300_000L // 100s difference (>12s tolerance)
        )
        val cloudTrack = createInnertubeTrack(
            videoId = "cloud_1",
            title = "Blinding Lights",
            artist = "The Weeknd",
            durationSeconds = 200L
        )

        coEvery { musicRepository.getAllSongsOnce() } returns listOf(localSong)
        coEvery { innertubeApiService.search("Blinding Lights The Weeknd", any(), any()) } returns
                InnertubeSearchResult("Blinding Lights The Weeknd", songs = listOf(cloudTrack))

        val spotifyTrack = createSpotifyTrack(
            id = "sp_1",
            title = "Blinding Lights",
            artist = "The Weeknd",
            durationMs = 200_000L
        )

        val results = engine.matchTracks(listOf(spotifyTrack))

        assertThat(results).hasSize(1)
        val match = results[0]
        assertThat(match.isLocalMatch).isFalse()
        assertThat(match.matchedYouTubeTrack).isEqualTo(cloudTrack)
        assertThat(match.matchedSong).isNotNull()
        assertThat(match.matchedSong?.youtubeId).isEqualTo("cloud_1")
        assertThat(match.matchedSong?.id).isEqualTo("youtube_cloud_1")
        assertThat(match.matchedSong?.contentUriString).isEqualTo("youtube://cloud_1")
    }

    @Test
    fun `matchTracks matches local song with feat and bracket normalization`() = runTest {
        val localSong = createLocalSong(
            id = "song_2",
            title = "Levitating",
            artist = "Dua Lipa",
            duration = 203_000L
        )
        coEvery { musicRepository.getAllSongsOnce() } returns listOf(localSong)

        val spotifyTrack = createSpotifyTrack(
            id = "sp_2",
            title = "Levitating (feat. Daft Punk) [Remastered 2021]",
            artist = "Dua Lipa feat. Daft Punk",
            durationMs = 204_000L
        )

        val results = engine.matchTracks(listOf(spotifyTrack))

        assertThat(results).hasSize(1)
        val match = results[0]
        assertThat(match.isLocalMatch).isTrue()
        assertThat(match.matchedSong).isEqualTo(localSong)
    }

    @Test
    fun `matchTracks matches local song with multiple artist refs`() = runTest {
        val localSong = createLocalSong(
            id = "song_multi",
            title = "One Kiss",
            artist = "Calvin Harris",
            artists = listOf(
                ArtistRef(id = 1L, name = "Calvin Harris", isPrimary = true),
                ArtistRef(id = 2L, name = "Dua Lipa", isPrimary = false)
            ),
            duration = 214_000L
        )
        coEvery { musicRepository.getAllSongsOnce() } returns listOf(localSong)

        val spotifyTrack = createSpotifyTrack(
            id = "sp_multi",
            title = "One Kiss",
            artist = "Calvin Harris, Dua Lipa",
            artists = listOf("Calvin Harris", "Dua Lipa"),
            durationMs = 214_000L
        )

        val results = engine.matchTracks(listOf(spotifyTrack))

        assertThat(results).hasSize(1)
        assertThat(results[0].isLocalMatch).isTrue()
        assertThat(results[0].matchedSong).isEqualTo(localSong)
    }

    @Test
    fun `matchTracks falls back to cloud search when not found in local library`() = runTest {
        coEvery { musicRepository.getAllSongsOnce() } returns emptyList()

        val ytTrack = createInnertubeTrack(
            videoId = "dQw4w9WgXcQ",
            title = "Never Gonna Give You Up",
            artist = "Rick Astley",
            durationSeconds = 213L
        )
        coEvery { innertubeApiService.search("Never Gonna Give You Up Rick Astley", any(), any()) } returns
                InnertubeSearchResult("Never Gonna Give You Up Rick Astley", songs = listOf(ytTrack))

        val spotifyTrack = createSpotifyTrack(
            id = "sp_nggyu",
            title = "Never Gonna Give You Up",
            artist = "Rick Astley",
            durationMs = 213_000L
        )

        val results = engine.matchTracks(listOf(spotifyTrack))

        assertThat(results).hasSize(1)
        val match = results[0]
        assertThat(match.isLocalMatch).isFalse()
        assertThat(match.matchedYouTubeTrack).isEqualTo(ytTrack)
        assertThat(match.matchedSong?.youtubeId).isEqualTo("dQw4w9WgXcQ")
        assertThat(match.matchedSong?.title).isEqualTo("Never Gonna Give You Up")
        assertThat(match.matchedSong?.artist).isEqualTo("Rick Astley")
        assertThat(match.matchedSong?.duration).isEqualTo(213_000L)
    }

    @Test
    fun `matchTracks selects best matching cloud candidate over first mismatch`() = runTest {
        coEvery { musicRepository.getAllSongsOnce() } returns emptyList()

        val mismatchTrack = createInnertubeTrack(
            videoId = "mismatch_id",
            title = "Random Cover By Other Artist",
            artist = "Cover Band",
            durationSeconds = 180L
        )
        val exactTrack = createInnertubeTrack(
            videoId = "exact_id",
            title = "Starboy",
            artist = "The Weeknd",
            durationSeconds = 230L
        )

        coEvery { innertubeApiService.search("Starboy The Weeknd", any(), any()) } returns
                InnertubeSearchResult("Starboy The Weeknd", songs = listOf(mismatchTrack, exactTrack))

        val spotifyTrack = createSpotifyTrack(
            id = "sp_starboy",
            title = "Starboy",
            artist = "The Weeknd",
            durationMs = 230_000L
        )

        val results = engine.matchTracks(listOf(spotifyTrack))

        assertThat(results).hasSize(1)
        val match = results[0]
        assertThat(match.isLocalMatch).isFalse()
        assertThat(match.matchedYouTubeTrack).isEqualTo(exactTrack)
        assertThat(match.matchedSong?.youtubeId).isEqualTo("exact_id")
    }

    @Test
    fun `matchTracks returns matchedSong null when neither local nor cloud finds a match`() = runTest {
        coEvery { musicRepository.getAllSongsOnce() } returns emptyList()
        coEvery { innertubeApiService.search(any(), any(), any()) } returns
                InnertubeSearchResult("Nonexistent Song Nonexistent Artist", songs = emptyList())

        val spotifyTrack = createSpotifyTrack(
            id = "sp_none",
            title = "Nonexistent Song",
            artist = "Nonexistent Artist"
        )

        val results = engine.matchTracks(listOf(spotifyTrack))

        assertThat(results).hasSize(1)
        val match = results[0]
        assertThat(match.isLocalMatch).isFalse()
        assertThat(match.matchedSong).isNull()
        assertThat(match.matchedYouTubeTrack).isNull()
    }

    @Test
    fun `matchTracks emits MatchProgress from 1 to total for all tracks`() = runTest {
        val localSong1 = createLocalSong(id = "s1", title = "Track One", artist = "Artist One")
        val localSong2 = createLocalSong(id = "s2", title = "Track Two", artist = "Artist Two")
        val localSong3 = createLocalSong(id = "s3", title = "Track Three", artist = "Artist Three")

        coEvery { musicRepository.getAllSongsOnce() } returns listOf(localSong1, localSong2, localSong3)

        val track1 = createSpotifyTrack(id = "t1", title = "Track One", artist = "Artist One")
        val track2 = createSpotifyTrack(id = "t2", title = "Track Two", artist = "Artist Two")
        val track3 = createSpotifyTrack(id = "t3", title = "Track Three", artist = "Artist Three")

        val progressUpdates = mutableListOf<MatchProgress>()

        val results = engine.matchTracks(
            tracks = listOf(track1, track2, track3),
            onProgress = { progressUpdates.add(it) }
        )

        assertThat(results).hasSize(3)
        assertThat(progressUpdates).hasSize(3)

        val currentValues = progressUpdates.map { it.current }.sorted()
        assertThat(currentValues).containsExactly(1, 2, 3).inOrder()

        progressUpdates.forEach {
            assertThat(it.total).isEqualTo(3)
        }
    }

    @Test
    fun `matchTracks skips local matching when matchLocal is false`() = runTest {
        val localSong = createLocalSong(id = "s1", title = "Track One", artist = "Artist One")
        coEvery { musicRepository.getAllSongsOnce() } returns listOf(localSong)

        val ytTrack = createInnertubeTrack(videoId = "yt_1", title = "Track One", artist = "Artist One")
        coEvery { innertubeApiService.search("Track One Artist One", any(), any()) } returns
                InnertubeSearchResult("Track One Artist One", songs = listOf(ytTrack))

        val track = createSpotifyTrack(id = "t1", title = "Track One", artist = "Artist One")

        val results = engine.matchTracks(
            tracks = listOf(track),
            matchLocal = false,
            matchCloud = true
        )

        assertThat(results).hasSize(1)
        assertThat(results[0].isLocalMatch).isFalse()
        assertThat(results[0].matchedYouTubeTrack).isEqualTo(ytTrack)
        coVerify(exactly = 0) { musicRepository.getAllSongsOnce() }
    }

    @Test
    fun `matchTracks skips cloud matching when matchCloud is false`() = runTest {
        coEvery { musicRepository.getAllSongsOnce() } returns emptyList()

        val track = createSpotifyTrack(id = "t1", title = "Track One", artist = "Artist One")

        val results = engine.matchTracks(
            tracks = listOf(track),
            matchLocal = true,
            matchCloud = false
        )

        assertThat(results).hasSize(1)
        assertThat(results[0].matchedSong).isNull()
        assertThat(results[0].isLocalMatch).isFalse()
        coVerify(exactly = 0) { innertubeApiService.search(any(), any(), any()) }
    }

    @Test
    fun `matchTracks handles exception from musicRepository gracefully`() = runTest {
        coEvery { musicRepository.getAllSongsOnce() } throws IOException("Database error")

        val ytTrack = createInnertubeTrack(videoId = "yt_fallback", title = "Track 1", artist = "Artist 1")
        coEvery { innertubeApiService.search(any(), any(), any()) } returns
                InnertubeSearchResult("query", songs = listOf(ytTrack))

        val track = createSpotifyTrack(id = "t1", title = "Track 1", artist = "Artist 1")

        val results = engine.matchTracks(listOf(track))

        assertThat(results).hasSize(1)
        assertThat(results[0].isLocalMatch).isFalse()
        assertThat(results[0].matchedYouTubeTrack).isEqualTo(ytTrack)
    }

    @Test
    fun `matchTracks handles exception from innertubeApiService gracefully`() = runTest {
        coEvery { musicRepository.getAllSongsOnce() } returns emptyList()
        coEvery { innertubeApiService.search(any(), any(), any()) } throws IOException("Network timeout")

        val track = createSpotifyTrack(id = "t1", title = "Track 1", artist = "Artist 1")

        val results = engine.matchTracks(listOf(track))

        assertThat(results).hasSize(1)
        assertThat(results[0].matchedSong).isNull()
        assertThat(results[0].isLocalMatch).isFalse()
    }
}
