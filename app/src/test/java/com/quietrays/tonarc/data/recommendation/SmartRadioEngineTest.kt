package com.quietrays.tonarc.data.recommendation

import com.google.common.truth.Truth.assertThat
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.network.youtube.InnertubeApiService
import com.quietrays.tonarc.data.network.youtube.InnertubeSearchResult
import com.quietrays.tonarc.data.network.youtube.InnertubeTrack
import com.quietrays.tonarc.data.repository.MusicRepository
import com.quietrays.tonarc.data.repository.SmartPlaylistGenerator
import com.quietrays.tonarc.data.youtube.YouTubeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

class SmartRadioEngineTest {

    private lateinit var innertubeApiService: InnertubeApiService
    private lateinit var youTubeRepository: YouTubeRepository
    private lateinit var smartPlaylistGenerator: SmartPlaylistGenerator
    private lateinit var candidateAggregator: CandidateAggregator
    private lateinit var musicRepository: MusicRepository
    private lateinit var radioEngine: SmartRadioEngine

    @BeforeEach
    fun setUp() {
        innertubeApiService = mockk(relaxed = true)
        youTubeRepository = mockk(relaxed = true)
        smartPlaylistGenerator = mockk(relaxed = true)
        candidateAggregator = mockk(relaxed = true)
        musicRepository = mockk(relaxed = true)

        radioEngine = SmartRadioEngine(
            innertubeApiService = innertubeApiService,
            youTubeRepository = youTubeRepository,
            smartPlaylistGenerator = smartPlaylistGenerator,
            candidateAggregator = candidateAggregator,
            musicRepository = musicRepository
        )
    }

    private fun createSong(
        id: String,
        title: String,
        artist: String,
        youtubeId: String? = null,
        uri: String = "content://media/$id"
    ): Song = Song(
        id = id,
        title = title,
        artist = artist,
        artistId = 1L,
        album = "Test Album",
        albumId = 1L,
        path = "/storage/emulated/0/Music/$id.mp3",
        contentUriString = uri,
        albumArtUriString = null,
        duration = 200_000L,
        mimeType = "audio/mp3",
        bitrate = 320000,
        sampleRate = 44100,
        youtubeId = youtubeId
    )

    private fun createInnertubeTrack(
        videoId: String,
        title: String,
        artist: String
    ): InnertubeTrack = InnertubeTrack(
        videoId = videoId,
        title = title,
        artist = artist,
        durationSeconds = 210,
        thumbnailUri = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
    )

    @Test
    fun `generateRadioForSong with explicit youtubeId fetches discovery and interleaves with local`() = runTest {
        val seed = createSong("local_1", "Blinding Lights", "The Weeknd", youtubeId = "bl_yt_100")

        val discoveryTracks = (1..10).map { i ->
            createInnertubeTrack("yt_track_$i", "Cloud Track $i", "Artist $i")
        }
        val localTracks = (1..5).map { i ->
            createSong("local_fav_$i", "Local Track $i", "Local Artist $i")
        }

        coEvery { innertubeApiService.getRadioTracks("bl_yt_100") } returns discoveryTracks
        coEvery { smartPlaylistGenerator.getSmartQueueForSong(seed, limit = 10) } returns localTracks

        val result = radioEngine.generateRadioForSong(seed, initialLimit = 10)

        assertThat(result.seed).isEqualTo(seed)
        assertThat(result.radioTitle).isEqualTo("Blinding Lights Radio")
        assertThat(result.tracks).hasSize(10)

        // Verifies interleaving: contains both discovery (starts with youtube_) and local tracks
        val discoveryCount = result.tracks.count { it.id.startsWith("youtube_") }
        val localCount = result.tracks.count { it.id.startsWith("local_") }
        assertThat(discoveryCount).isEqualTo(7)
        assertThat(localCount).isEqualTo(3)

        // Seed should not be present in tracks
        assertThat(result.tracks.none { it.id == seed.id || it.youtubeId == seed.youtubeId }).isTrue()
    }

    @Test
    fun `generateRadioForSong with youtube URI resolves videoId directly`() = runTest {
        val seed = createSong("yt_uri_song", "Starboy", "The Weeknd", uri = "youtube://starboy_vid_123")

        val discoveryTracks = listOf(
            createInnertubeTrack("yt_rel_1", "Related Track 1", "Artist 1"),
            createInnertubeTrack("yt_rel_2", "Related Track 2", "Artist 2")
        )

        coEvery { innertubeApiService.getRadioTracks("starboy_vid_123") } returns discoveryTracks
        coEvery { smartPlaylistGenerator.getSmartQueueForSong(seed, limit = 10) } returns emptyList()

        val result = radioEngine.generateRadioForSong(seed, initialLimit = 5)

        assertThat(result.tracks).hasSize(2)
        assertThat(result.tracks.first().id).isEqualTo("youtube_yt_rel_1")
        coVerify(exactly = 0) { innertubeApiService.search(any(), any(), any()) }
    }

    @Test
    fun `generateRadioForSong with youtube_ ID prefix resolves videoId directly`() = runTest {
        val seed = createSong("youtube_direct_vid_999", "In The End", "Linkin Park")

        val discoveryTracks = listOf(
            createInnertubeTrack("lp_radio_1", "Numb", "Linkin Park"),
            createInnertubeTrack("lp_radio_2", "Crawling", "Linkin Park")
        )

        coEvery { innertubeApiService.getRadioTracks("direct_vid_999") } returns discoveryTracks
        coEvery { smartPlaylistGenerator.getSmartQueueForSong(seed, limit = 10) } returns emptyList()

        val result = radioEngine.generateRadioForSong(seed, initialLimit = 10)

        assertThat(result.tracks).hasSize(2)
        assertThat(result.tracks.map { it.id }).containsExactly("youtube_lp_radio_1", "youtube_lp_radio_2").inOrder()
        coVerify(exactly = 0) { innertubeApiService.search(any(), any(), any()) }
    }

    @Test
    fun `generateRadioForSong searches Innertube to resolve videoId for local tracks`() = runTest {
        val seed = createSong("local_song_42", "Bohemian Rhapsody", "Queen")

        val searchSong = createInnertubeTrack("queen_vid_123", "Bohemian Rhapsody", "Queen")
        val searchResult = InnertubeSearchResult(
            query = "Bohemian Rhapsody Queen",
            songs = listOf(searchSong)
        )

        val radioTracks = listOf(
            createInnertubeTrack("queen_rad_1", "Don't Stop Me Now", "Queen"),
            createInnertubeTrack("queen_rad_2", "Radio Ga Ga", "Queen")
        )

        coEvery { innertubeApiService.search("Bohemian Rhapsody Queen", InnertubeApiService.YTM_FILTER_SONGS) } returns searchResult
        coEvery { innertubeApiService.getRadioTracks("queen_vid_123") } returns radioTracks
        coEvery { smartPlaylistGenerator.getSmartQueueForSong(seed, limit = 10) } returns emptyList()

        val result = radioEngine.generateRadioForSong(seed, initialLimit = 10)

        assertThat(result.radioTitle).isEqualTo("Bohemian Rhapsody Radio")
        assertThat(result.tracks).hasSize(2)
        assertThat(result.tracks[0].title).isEqualTo("Don't Stop Me Now")
        assertThat(result.tracks[1].title).isEqualTo("Radio Ga Ga")
    }

    @Test
    fun `generateRadioForSong falls back gracefully to smart playlist generator when network fails`() = runTest {
        val seed = createSong("song_err", "Save Your Tears", "The Weeknd", youtubeId = "err_vid_404")

        val fallbackSongs = listOf(
            createSong("loc_fb_1", "Heartless", "The Weeknd"),
            createSong("loc_fb_2", "After Hours", "The Weeknd")
        )

        coEvery { innertubeApiService.getRadioTracks("err_vid_404") } throws IOException("Network unreachable")
        coEvery { smartPlaylistGenerator.getSmartQueueForSong(seed, 25) } returns fallbackSongs

        val result = radioEngine.generateRadioForSong(seed, initialLimit = 25)

        assertThat(result.seed).isEqualTo(seed)
        assertThat(result.radioTitle).isEqualTo("Save Your Tears Radio")
        assertThat(result.tracks).containsExactlyElementsIn(fallbackSongs).inOrder()
    }

    @Test
    fun `generateRadioForSong falls back to smart playlist generator when search returns no videoId`() = runTest {
        val seed = createSong("local_rare", "Rare Local Track", "Unknown Artist")

        coEvery { innertubeApiService.search(any(), any(), any()) } returns InnertubeSearchResult(query = "query", songs = emptyList())

        val fallbackSongs = listOf(
            createSong("loc_fb_3", "Fallback 1", "Local Artist")
        )
        coEvery { smartPlaylistGenerator.getSmartQueueForSong(seed, 25) } returns fallbackSongs

        val result = radioEngine.generateRadioForSong(seed, initialLimit = 25)

        assertThat(result.tracks).isEqualTo(fallbackSongs)
    }

    @Test
    fun `generateRadioForSong deduplicates tracks matching seed song and duplicate entries`() = runTest {
        val seed = createSong("seed_id", "Midnight City", "M83", youtubeId = "m83_mid_1")

        val discoveryTracks = listOf(
            createInnertubeTrack("m83_mid_1", "Midnight City", "M83"), // Duplicate of seed by videoId
            createInnertubeTrack("m83_other_1", "Midnight City", "M83"), // Duplicate of seed by title/artist
            createInnertubeTrack("m83_valid_1", "Wait", "M83"),
            createInnertubeTrack("m83_valid_1", "Wait", "M83"), // Duplicate in list
            createInnertubeTrack("m83_valid_2", "Outro", "M83")
        )

        val localTracks = listOf(
            createSong("seed_id", "Midnight City", "M83"), // Duplicate of seed by id
            createSong("loc_m83", "Reunion", "M83")
        )

        coEvery { innertubeApiService.getRadioTracks("m83_mid_1") } returns discoveryTracks
        coEvery { smartPlaylistGenerator.getSmartQueueForSong(seed, limit = 10) } returns localTracks

        val result = radioEngine.generateRadioForSong(seed, initialLimit = 10)

        // Should only contain Wait, Outro, Reunion
        val titles = result.tracks.map { it.title }
        assertThat(titles).doesNotContain("Midnight City")
        assertThat(titles).containsExactly("Wait", "Reunion", "Outro")
    }

    @Test
    fun `generateRadioForSong clamps output to initialLimit`() = runTest {
        val seed = createSong("seed_clamp", "Song", "Artist", youtubeId = "vid_clamp")

        val discoveryTracks = (1..30).map { createInnertubeTrack("vid_$it", "Title $it", "Artist $it") }
        val localTracks = (1..20).map { createSong("loc_$it", "Local $it", "Artist $it") }

        coEvery { innertubeApiService.getRadioTracks("vid_clamp") } returns discoveryTracks
        coEvery { smartPlaylistGenerator.getSmartQueueForSong(seed, limit = 10) } returns localTracks

        val result = radioEngine.generateRadioForSong(seed, initialLimit = 7)

        assertThat(result.tracks).hasSize(7)
    }

    @Test
    fun `generateRadioForSong handles non-positive initialLimit`() = runTest {
        val seed = createSong("seed_zero", "Song", "Artist", youtubeId = "vid_zero")

        val result = radioEngine.generateRadioForSong(seed, initialLimit = 0)

        assertThat(result.tracks).isEmpty()
        assertThat(result.radioTitle).isEqualTo("Song Radio")
    }

    @Test
    fun `generateRadioForArtist finds top song online and generates artist radio`() = runTest {
        val topTrack = createInnertubeTrack("daft_vid_1", "Get Lucky", "Daft Punk")
        val searchResult = InnertubeSearchResult(
            query = "Daft Punk",
            songs = listOf(topTrack)
        )

        val radioTracks = listOf(
            createInnertubeTrack("daft_rad_1", "One More Time", "Daft Punk"),
            createInnertubeTrack("daft_rad_2", "Around The World", "Daft Punk")
        )

        coEvery { innertubeApiService.search("Daft Punk", InnertubeApiService.YTM_FILTER_SONGS) } returns searchResult
        coEvery { innertubeApiService.getRadioTracks("daft_vid_1") } returns radioTracks
        coEvery { smartPlaylistGenerator.getSmartQueueForSong(any(), limit = 10) } returns emptyList()

        val result = radioEngine.generateRadioForArtist("Daft Punk", initialLimit = 10)

        assertThat(result.radioTitle).isEqualTo("Daft Punk Radio")
        assertThat(result.tracks.map { it.title }).containsExactly("One More Time", "Around The World").inOrder()
    }

    @Test
    fun `generateRadioForArtist falls back to local artist songs when online search fails`() = runTest {
        coEvery { innertubeApiService.search("Coldplay", InnertubeApiService.YTM_FILTER_SONGS) } throws IOException("Offline")

        val localColdplaySong = createSong("loc_cp_1", "Yellow", "Coldplay")
        val localColdplaySong2 = createSong("loc_cp_2", "Fix You", "Coldplay")

        coEvery { musicRepository.getAudioFiles() } returns flowOf(listOf(localColdplaySong, localColdplaySong2))
        coEvery { smartPlaylistGenerator.getSmartQueueForSong(localColdplaySong, 25) } returns listOf(localColdplaySong2)

        val result = radioEngine.generateRadioForArtist("Coldplay", initialLimit = 25)

        assertThat(result.radioTitle).isEqualTo("Coldplay Radio")
        assertThat(result.seed).isEqualTo(localColdplaySong)
        assertThat(result.tracks.map { it.title }).contains("Fix You")
    }

    @Test
    fun `generateRadioForArtist handles empty library when offline`() = runTest {
        coEvery { innertubeApiService.search(any(), any(), any()) } throws IOException("Offline")
        coEvery { musicRepository.getAudioFiles() } returns flowOf(emptyList())
        coEvery { smartPlaylistGenerator.getSmartQueueForSong(any(), any()) } returns emptyList()

        val result = radioEngine.generateRadioForArtist("Unknown Indie Band", initialLimit = 25)

        assertThat(result.radioTitle).isEqualTo("Unknown Indie Band Radio")
        assertThat(result.tracks).isEmpty()
    }

    @Test
    fun `generateRadioForArtist handles non-positive initialLimit`() = runTest {
        val result = radioEngine.generateRadioForArtist("Coldplay", initialLimit = 0)

        assertThat(result.tracks).isEmpty()
        assertThat(result.radioTitle).isEqualTo("Coldplay Radio")
    }

    @Test
    fun `fetchNextBatch returns mapped domain songs from continuation token`() = runTest {
        val continuationTracks = listOf(
            createInnertubeTrack("next_1", "Next Song 1", "Artist 1"),
            createInnertubeTrack("next_2", "Next Song 2", "Artist 2"),
            createInnertubeTrack("next_3", "Next Song 3", "Artist 3")
        )

        coEvery { innertubeApiService.getRadioTracks("seed_vid", "continuation_token_xyz") } returns continuationTracks

        val songs = radioEngine.fetchNextBatch("seed_vid", "continuation_token_xyz", limit = 2)

        assertThat(songs).hasSize(2)
        assertThat(songs[0].id).isEqualTo("youtube_next_1")
        assertThat(songs[0].title).isEqualTo("Next Song 1")
        assertThat(songs[1].id).isEqualTo("youtube_next_2")
        assertThat(songs[1].title).isEqualTo("Next Song 2")
    }

    @Test
    fun `fetchNextBatch returns empty list when arguments are blank or limit non-positive`() = runTest {
        val empty1 = radioEngine.fetchNextBatch("", "", limit = 10)
        val empty2 = radioEngine.fetchNextBatch("seed_vid", "token", limit = 0)

        assertThat(empty1).isEmpty()
        assertThat(empty2).isEmpty()
    }

    @Test
    fun `interleaveTracks works when local list is empty`() {
        val seed = createSong("seed", "Seed", "Artist")
        val discovery = (1..5).map { createSong("yt_$it", "Disc $it", "Artist $it", youtubeId = "yt_$it") }

        val interleaved = radioEngine.interleaveTracks(discovery, emptyList(), seed, limit = 5)

        assertThat(interleaved).hasSize(5)
        assertThat(interleaved.map { it.id }).containsExactly("yt_1", "yt_2", "yt_3", "yt_4", "yt_5").inOrder()
    }

    @Test
    fun `interleaveTracks works when discovery list is empty`() {
        val seed = createSong("seed", "Seed", "Artist")
        val local = (1..5).map { createSong("loc_$it", "Local $it", "Artist $it") }

        val interleaved = radioEngine.interleaveTracks(emptyList(), local, seed, limit = 5)

        assertThat(interleaved).hasSize(5)
        assertThat(interleaved.map { it.id }).containsExactly("loc_1", "loc_2", "loc_3", "loc_4", "loc_5").inOrder()
    }
}
