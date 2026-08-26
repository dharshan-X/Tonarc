package com.quietrays.tonarc.presentation.viewmodel

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ListenableFuture
import com.quietrays.tonarc.MainCoroutineExtension
import com.quietrays.tonarc.data.ContextualMix
import com.quietrays.tonarc.data.MixMood
import com.quietrays.tonarc.data.database.AlbumArtThemeDao
import com.quietrays.tonarc.data.database.YouTubeDao
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.model.SortOption
import com.quietrays.tonarc.data.model.StorageFilter
import com.quietrays.tonarc.data.network.youtube.InnertubeApiService
import com.quietrays.tonarc.data.analytics.TasteProfile
import com.quietrays.tonarc.data.analytics.TasteProfileManager
import com.quietrays.tonarc.data.preferences.ThemePreferencesRepository
import com.quietrays.tonarc.data.preferences.UserPreferencesRepository
import com.quietrays.tonarc.data.recommendation.RadioResult
import com.quietrays.tonarc.data.recommendation.SmartRadioEngine
import com.quietrays.tonarc.data.repository.MusicRepository
import com.quietrays.tonarc.data.repository.SmartPlaylistGenerator
import com.quietrays.tonarc.data.service.player.DualPlayerEngine
import com.quietrays.tonarc.data.worker.SyncManager
import com.quietrays.tonarc.data.youtube.YouTubeRepository
import com.quietrays.tonarc.utils.AppShortcutManager
import com.quietrays.tonarc.utils.MediaItemBuilder
import io.mockk.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExperimentalCoroutinesApi
@ExtendWith(MainCoroutineExtension::class)
class PlayerViewModelRadioTest {

    private lateinit var playerViewModel: PlayerViewModel
    private val mockContext: Context = mockk(relaxed = true)
    private val mockMusicRepository: MusicRepository = mockk(relaxed = true)
    private val mockUserPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val mockThemePreferencesRepository: ThemePreferencesRepository = mockk(relaxed = true)
    private val mockAlbumArtThemeDao: AlbumArtThemeDao = mockk(relaxed = true)
    private val mockSyncManager: SyncManager = mockk(relaxed = true)
    private val mockDualPlayerEngine: DualPlayerEngine = mockk(relaxed = true)
    private val mockAppShortcutManager: AppShortcutManager = mockk(relaxed = true)
    private val mockListeningStatsTracker: ListeningStatsTracker = mockk(relaxed = true)
    private val mockDailyMixStateHolder: DailyMixStateHolder = mockk(relaxed = true)
    private val mockLyricsStateHolder: LyricsStateHolder = mockk(relaxed = true)
    private val mockQueueStateHolder: QueueStateHolder = mockk(relaxed = true)
    private val mockQueueUndoStateHolder: QueueUndoStateHolder = mockk(relaxed = true)
    private val mockPlaylistDismissUndoStateHolder: PlaylistDismissUndoStateHolder = mockk(relaxed = true)
    private val mockPlaybackStateHolder: PlaybackStateHolder = mockk(relaxed = true)
    private val mockConnectivityStateHolder: ConnectivityStateHolder = mockk(relaxed = true)
    private val mockSleepTimerStateHolder: SleepTimerStateHolder = mockk(relaxed = true)
    private val mockSearchStateHolder: SearchStateHolder = mockk(relaxed = true)
    private val mockLibraryStateHolder: LibraryStateHolder = mockk(relaxed = true)
    private val mockFolderNavigationStateHolder: FolderNavigationStateHolder = mockk(relaxed = true)
    private val mockLibraryTabsStateHolder: LibraryTabsStateHolder = mockk(relaxed = true)
    private val mockMetadataEditStateHolder: MetadataEditStateHolder = mockk(relaxed = true)
    private val mockSongRemovalStateHolder: SongRemovalStateHolder = mockk(relaxed = true)
    private val mockExternalMediaStateHolder: ExternalMediaStateHolder = mockk(relaxed = true)
    private val mockThemeStateHolder: ThemeStateHolder = mockk(relaxed = true)
    private val mockMultiSelectionStateHolder: MultiSelectionStateHolder = mockk(relaxed = true)
    private val mockPlaylistSelectionStateHolder: PlaylistSelectionStateHolder = mockk(relaxed = true)
    private val mockSmartPlaylistGenerator: SmartPlaylistGenerator = mockk(relaxed = true)
    private val mockYouTubeRepository: YouTubeRepository = mockk(relaxed = true)
    private val mockYouTubeDao: YouTubeDao = mockk(relaxed = true)
    private val mockInnertubeApiService: InnertubeApiService = mockk(relaxed = true)
    private val mockSmartRadioEngine: SmartRadioEngine = mockk(relaxed = true)
    private val mockTasteProfileManager: TasteProfileManager = mockk(relaxed = true)
    private lateinit var mockMediaControllerFactory: com.quietrays.tonarc.data.media.MediaControllerFactory
    private lateinit var mockController: MediaController
    private val controllerMediaItems = mutableListOf<MediaItem>()

    private val testDispatcher = StandardTestDispatcher()

    private val _allSongsFlow = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    private val _favoriteIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private lateinit var stablePlayerStateFlow: MutableStateFlow<StablePlayerState>

    private val seedSong = Song(
        id = "song_1",
        title = "Paranoid Android",
        artist = "Radiohead",
        artistId = 1L,
        album = "OK Computer",
        albumId = 1L,
        path = "/music/song1.mp3",
        contentUriString = "content://media/1",
        albumArtUriString = null,
        duration = 383000L,
        mimeType = "audio/mpeg",
        bitrate = 320000,
        sampleRate = 44100,
        youtubeId = "fHiGbolFFGw"
    )

    private val radioTrack1 = Song(
        id = "youtube_v1",
        title = "Karma Police",
        artist = "Radiohead",
        artistId = 1L,
        album = "OK Computer",
        albumId = 1L,
        path = "youtube://v1",
        contentUriString = "youtube://v1",
        albumArtUriString = null,
        duration = 264000L,
        mimeType = "audio/webm",
        bitrate = 160000,
        sampleRate = 48000,
        youtubeId = "v1"
    )

    private val radioTrack2 = Song(
        id = "youtube_v2",
        title = "No Surprises",
        artist = "Radiohead",
        artistId = 1L,
        album = "OK Computer",
        albumId = 1L,
        path = "youtube://v2",
        contentUriString = "youtube://v2",
        albumArtUriString = null,
        duration = 228000L,
        mimeType = "audio/webm",
        bitrate = 160000,
        sampleRate = 48000,
        youtubeId = "v2"
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        MockKAnnotations.init(this)
        controllerMediaItems.clear()

        mockkStatic(ContextCompat::class)
        val directExecutor = java.util.concurrent.Executor { it.run() }
        every { ContextCompat.getMainExecutor(any()) } returns directExecutor

        mockkObject(MediaItemBuilder)
        every { MediaItemBuilder.build(any()) } answers {
            val song = firstArg<Song>()
            MediaItem.Builder()
                .setMediaId(song.id)
                .setUri("file:///tmp/${song.id}.mp3")
                .build()
        }
        val mockedPlaybackUri = mockk<android.net.Uri>(relaxed = true)
        every { mockedPlaybackUri.scheme } returns "file"
        every { MediaItemBuilder.playbackUri(any<Song>()) } returns mockedPlaybackUri

        mockkStatic(android.net.Uri::class)
        every { android.net.Uri.parse(any()) } returns mockk(relaxed = true)
        every { android.net.Uri.fromFile(any()) } returns mockk(relaxed = true)

        coEvery { mockUserPreferencesRepository.favoriteSongIdsFlow } returns flowOf(emptySet())
        coEvery { mockUserPreferencesRepository.songsSortOptionFlow } returns flowOf("SongTitleAZ")
        coEvery { mockUserPreferencesRepository.albumsSortOptionFlow } returns flowOf("AlbumTitleAZ")
        coEvery { mockUserPreferencesRepository.artistsSortOptionFlow } returns flowOf("ArtistNameAZ")
        coEvery { mockUserPreferencesRepository.likedSongsSortOptionFlow } returns flowOf("LikedSongTitleAZ")
        coEvery { mockUserPreferencesRepository.navBarCornerRadiusFlow } returns flowOf(32)
        coEvery { mockUserPreferencesRepository.navBarStyleFlow } returns flowOf("Default")
        coEvery { mockUserPreferencesRepository.libraryNavigationModeFlow } returns flowOf("TabRow")
        coEvery { mockUserPreferencesRepository.carouselStyleFlow } returns flowOf("NoPeek")
        coEvery { mockUserPreferencesRepository.fullPlayerLoadingTweaksFlow } returns flowOf(com.quietrays.tonarc.data.preferences.FullPlayerLoadingTweaks())
        coEvery { mockUserPreferencesRepository.tapBackgroundClosesPlayerFlow } returns flowOf(true)
        coEvery { mockUserPreferencesRepository.hapticsEnabledFlow } returns flowOf(true)
        coEvery { mockUserPreferencesRepository.foldersSortOptionFlow } returns flowOf("FolderNameAZ")
        coEvery { mockUserPreferencesRepository.persistentShuffleEnabledFlow } returns flowOf(false)
        coEvery { mockUserPreferencesRepository.isShuffleOnFlow } returns flowOf(false)
        every { mockUserPreferencesRepository.repeatModeFlow } returns MutableStateFlow(Player.REPEAT_MODE_OFF)
        coEvery { mockThemePreferencesRepository.playerThemePreferenceFlow } returns flowOf("Global")

        val songsMap = mapOf(
            seedSong.id to seedSong,
            radioTrack1.id to radioTrack1,
            radioTrack2.id to radioTrack2
        )
        every { mockLibraryStateHolder.allSongs } returns _allSongsFlow
        every { mockLibraryStateHolder.allSongsById } returns MutableStateFlow(songsMap)
        every { mockLibraryStateHolder.isLoadingLibrary } returns MutableStateFlow(false)
        every { mockLibraryStateHolder.isLoadingCategories } returns MutableStateFlow(false)
        every { mockLibraryStateHolder.genres } returns MutableStateFlow(persistentListOf())
        every { mockLibraryStateHolder.albums } returns MutableStateFlow(persistentListOf())
        every { mockLibraryStateHolder.artists } returns MutableStateFlow(persistentListOf())
        every { mockLibraryStateHolder.musicFolders } returns MutableStateFlow(persistentListOf())
        every { mockLibraryStateHolder.currentSongSortOption } returns MutableStateFlow(SortOption.SongTitleAZ)
        every { mockLibraryStateHolder.currentAlbumSortOption } returns MutableStateFlow(SortOption.AlbumTitleAZ)
        every { mockLibraryStateHolder.currentArtistSortOption } returns MutableStateFlow(SortOption.ArtistNameAZ)
        every { mockLibraryStateHolder.currentFolderSortOption } returns MutableStateFlow(SortOption.FolderNameAZ)
        every { mockLibraryStateHolder.currentFavoriteSortOption } returns MutableStateFlow(SortOption.LikedSongTitleAZ)
        every { mockLibraryStateHolder.currentStorageFilter } returns MutableStateFlow(StorageFilter.ALL)
        every { mockLibraryStateHolder.initialize(any()) } just runs

        every { mockSearchStateHolder.searchHistory } returns MutableStateFlow(persistentListOf())
        every { mockSearchStateHolder.searchResults } returns MutableStateFlow(persistentListOf())
        every { mockSearchStateHolder.selectedSearchFilter } returns MutableStateFlow(com.quietrays.tonarc.data.model.SearchFilterType.ALL)
        every { mockSearchStateHolder.isLoadingMore } returns MutableStateFlow(false)
        every { mockSearchStateHolder.isSearchingOnline } returns MutableStateFlow(false)
        every { mockSearchStateHolder.initialize(any()) } just runs

        every { mockConnectivityStateHolder.initialize() } just runs
        every { mockSleepTimerStateHolder.initialize(any(), any(), any(), any(), any()) } just runs

        stablePlayerStateFlow = MutableStateFlow(StablePlayerState(currentSong = null))
        every { mockPlaybackStateHolder.stablePlayerState } returns stablePlayerStateFlow
        every { mockPlaybackStateHolder.setMediaController(any()) } just runs

        every { mockMusicRepository.getPaginatedSongs(any(), any()) } returns flowOf(androidx.paging.PagingData.empty())
        every { mockMusicRepository.getPaginatedFavoriteSongs(any(), any()) } returns flowOf(androidx.paging.PagingData.empty())
        every { mockMusicRepository.getAudioFiles() } returns flowOf(emptyList())
        every { mockMusicRepository.getDistinctAlbumArtSongs() } returns flowOf(emptyList())
        every { mockMusicRepository.getHomeMixPreviewSongs(any()) } returns flowOf(emptyList())
        every { mockMusicRepository.getSongCountFlow() } returns flowOf(0)
        every { mockMusicRepository.getCloudSongCountFlow() } returns flowOf(0)
        every { mockMusicRepository.searchSongs(any(), any()) } returns flowOf(emptyList())
        every { mockMusicRepository.getMusicByGenre(any()) } returns flowOf(emptyList())
        coEvery { mockMusicRepository.getFavoriteSongIdsOnce() } returns emptySet()
        every { mockMusicRepository.getFavoriteSongIdsFlow() } returns _favoriteIdsFlow
        every { mockMusicRepository.getSong(seedSong.id) } returns flowOf(seedSong)
        every { mockMusicRepository.getSong(radioTrack1.id) } returns flowOf(radioTrack1)
        every { mockMusicRepository.getSong(radioTrack2.id) } returns flowOf(radioTrack2)
        every { mockMusicRepository.getSong(any()) } returns flowOf(null)
        coEvery { mockMusicRepository.getSongIdByContentUri(any()) } returns null
        coEvery { mockMusicRepository.getSongByPath(any()) } returns null
        coEvery { mockMusicRepository.setFavoriteStatus(any(), any()) } just Runs
        coEvery { mockMusicRepository.getAllSongsOnce() } returns emptyList()
        coEvery { mockMusicRepository.getFavoriteSongsOnce(any()) } returns emptyList()
        coEvery { mockMusicRepository.getFirstPlayableSong() } returns null
        coEvery { mockMusicRepository.getRandomSongs(any()) } returns emptyList()
        coEvery { mockMusicRepository.getSongIdsSorted(any(), any()) } returns emptyList()
        coEvery { mockMusicRepository.getFavoriteSongIdsSorted(any(), any()) } returns emptyList()
        every { mockLyricsStateHolder.songUpdates } returns MutableSharedFlow()

        val sessionToken = mockk<SessionToken>(relaxed = true)
        mockMediaControllerFactory = mockk(relaxed = true)

        mockController = mockk(relaxed = true)
        every { mockController.isConnected } returns true
        every { mockController.mediaItemCount } answers { controllerMediaItems.size }
        every { mockController.getMediaItemAt(any()) } answers { controllerMediaItems[firstArg()] }
        every { mockController.addMediaItems(any()) } answers {
            val items = firstArg<List<MediaItem>>()
            controllerMediaItems.addAll(items)
        }
        every { mockController.setMediaItems(any()) } answers {
            val items = firstArg<List<MediaItem>>()
            controllerMediaItems.clear()
            controllerMediaItems.addAll(items)
        }
        every { mockController.setMediaItems(any(), any<Int>(), any<Long>()) } answers {
            val items = firstArg<List<MediaItem>>()
            controllerMediaItems.clear()
            controllerMediaItems.addAll(items)
        }
        every { mockController.clearMediaItems() } answers {
            controllerMediaItems.clear()
        }

        val mockFuture = mockk<ListenableFuture<MediaController>>(relaxed = true)
        every { mockFuture.get() } returns mockController
        every { mockFuture.addListener(any(), any()) } answers {
            val runnable = firstArg<Runnable>()
            runnable.run()
        }
        every { mockMediaControllerFactory.create(any(), any(), any()) } returns mockFuture

        coEvery { mockTasteProfileManager.computeTasteProfile() } returns TasteProfile(
            archetypeTitle = "Melody Connoisseur",
            archetypeSubtitle = "Guided by timeless songwriting and deep harmonies",
            archetypeEmoji = "🎵",
            totalListeningDurationMs = 0L,
            totalPlays = 0,
            topGenres = emptyList(),
            topArtists = emptyList(),
            topSongs = emptyList()
        )

        playerViewModel = PlayerViewModel(
            mockContext,
            mockMusicRepository,
            mockUserPreferencesRepository,
            mockThemePreferencesRepository,
            mockAlbumArtThemeDao,
            mockSyncManager,
            mockDualPlayerEngine,
            mockAppShortcutManager,
            mockListeningStatsTracker,
            mockDailyMixStateHolder,
            mockLyricsStateHolder,
            mockQueueStateHolder,
            mockQueueUndoStateHolder,
            mockPlaylistDismissUndoStateHolder,
            mockPlaybackStateHolder,
            mockConnectivityStateHolder,
            mockSleepTimerStateHolder,
            mockSearchStateHolder,
            mockLibraryStateHolder,
            mockFolderNavigationStateHolder,
            mockLibraryTabsStateHolder,
            mockMetadataEditStateHolder,
            mockSongRemovalStateHolder,
            mockExternalMediaStateHolder,
            mockThemeStateHolder,
            mockMultiSelectionStateHolder,
            mockPlaylistSelectionStateHolder,
            mockSmartPlaylistGenerator,
            mockYouTubeRepository,
            mockYouTubeDao,
            mockInnertubeApiService,
            sessionToken,
            mockMediaControllerFactory,
            mockSmartRadioEngine,
            mockTasteProfileManager
        )
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(android.net.Uri::class)
        unmockkObject(MediaItemBuilder)
        unmockkAll()
    }

    @Test
    @DisplayName("playInstantRadio immediately starts seed song playback and asynchronously hydrates queue")
    fun test_playInstantRadio_startsSeedAndHydratesQueue() = runTest(testDispatcher) {
        val radioResult = RadioResult(
            seed = seedSong,
            tracks = listOf(radioTrack1, radioTrack2),
            continuationToken = null,
            radioTitle = "Paranoid Android Radio"
        )
        coEvery { mockSmartRadioEngine.generateRadioForSong(seedSong, any()) } returns radioResult
        coEvery { mockSmartRadioEngine.generateRadioForSong(seedSong) } returns radioResult

        playerViewModel.playInstantRadio(seedSong)

        advanceUntilIdle()

        // Verify that radio was generated and media items were added
        coVerify { mockSmartRadioEngine.generateRadioForSong(seedSong, any()) }
        verify { mockController.addMediaItems(any()) }
        verify { mockQueueStateHolder.setOriginalQueueOrder(any()) }

        val queue = playerViewModel.playerUiState.value.currentPlaybackQueue
        assertThat(queue).contains(radioTrack1)
        assertThat(queue).contains(radioTrack2)
    }

    @Test
    @DisplayName("playArtistRadio generates artist radio and plays full queue")
    fun test_playArtistRadio_generatesAndPlaysRadio() = runTest(testDispatcher) {
        val radioResult = RadioResult(
            seed = seedSong,
            tracks = listOf(radioTrack1, radioTrack2),
            continuationToken = null,
            radioTitle = "Radiohead Radio"
        )
        coEvery { mockSmartRadioEngine.generateRadioForArtist("Radiohead", any()) } returns radioResult
        coEvery { mockSmartRadioEngine.generateRadioForArtist("Radiohead") } returns radioResult

        playerViewModel.playArtistRadio("Radiohead")
        advanceUntilIdle()

        coVerify { mockSmartRadioEngine.generateRadioForArtist("Radiohead", any()) }
        verify { mockQueueStateHolder.setOriginalQueueOrder(any()) }
    }

    @Test
    @DisplayName("playContextualMix plays first song and sets context songs in queue")
    fun test_playContextualMix_playsSongsInContext() = runTest(testDispatcher) {
        val mix = ContextualMix(
            mood = MixMood.ENERGY_BOOST,
            title = "⚡ Afternoon Energy",
            subtitle = "High tempo drive",
            songs = listOf(radioTrack1, radioTrack2)
        )

        playerViewModel.playContextualMix(mix)
        advanceUntilIdle()

        verify { mockQueueStateHolder.setOriginalQueueOrder(listOf(radioTrack1, radioTrack2)) }
    }

    @Test
    @DisplayName("playDiscoveryRadar fetches and plays discovery radar mix")
    fun test_playDiscoveryRadar_playsDiscoveryRadarMix() = runTest(testDispatcher) {
        val radarMix = ContextualMix(
            mood = MixMood.DISCOVERY_RADAR,
            title = "📡 Discovery Radar",
            subtitle = "Fresh tracks",
            songs = listOf(radioTrack1, radioTrack2)
        )
        every { mockDailyMixStateHolder.contextualMixes } returns MutableStateFlow(listOf(radarMix))

        playerViewModel.playDiscoveryRadar()
        advanceUntilIdle()

        verify { mockQueueStateHolder.setOriginalQueueOrder(listOf(radioTrack1, radioTrack2)) }
    }

    @Test
    @DisplayName("refreshTasteProfile computes and updates tasteProfile StateFlow")
    fun test_refreshTasteProfile_updatesTasteProfileState() = runTest(testDispatcher) {
        val expectedProfile = TasteProfile(
            archetypeTitle = "Late-Night Audiophile",
            archetypeSubtitle = "Finds magic in midnight frequencies",
            archetypeEmoji = "🌌",
            totalListeningDurationMs = 120_000L,
            totalPlays = 15,
            topGenres = emptyList(),
            topArtists = emptyList(),
            topSongs = listOf(radioTrack1, radioTrack2)
        )
        coEvery { mockTasteProfileManager.computeTasteProfile() } returns expectedProfile

        playerViewModel.refreshTasteProfile().join()
        advanceUntilIdle()

        assertThat(playerViewModel.tasteProfile.value).isEqualTo(expectedProfile)
    }

    @Test
    @DisplayName("playTopTasteMix plays topSongs from cached taste profile")
    fun test_playTopTasteMix_whenProfileCached_playsTopSongs() = runTest(testDispatcher) {
        val expectedProfile = TasteProfile(
            archetypeTitle = "High-Energy Motivator",
            archetypeSubtitle = "Fueled by high-tempo anthems",
            archetypeEmoji = "⚡",
            totalListeningDurationMs = 240_000L,
            totalPlays = 30,
            topGenres = emptyList(),
            topArtists = emptyList(),
            topSongs = listOf(radioTrack1, radioTrack2)
        )
        coEvery { mockTasteProfileManager.computeTasteProfile() } returns expectedProfile

        playerViewModel.refreshTasteProfile().join()
        advanceUntilIdle()

        playerViewModel.playTopTasteMix().join()
        advanceUntilIdle()

        verify { mockQueueStateHolder.setOriginalQueueOrder(listOf(radioTrack1, radioTrack2)) }
    }

    @Test
    @DisplayName("playTopTasteMix computes taste profile and plays topSongs when not already cached")
    fun test_playTopTasteMix_whenProfileNotCached_computesAndPlaysTopSongs() = runTest(testDispatcher) {
        val expectedProfile = TasteProfile(
            archetypeTitle = "Acoustic Explorer",
            archetypeSubtitle = "Energized by morning melodies",
            archetypeEmoji = "🌅",
            totalListeningDurationMs = 60_000L,
            totalPlays = 5,
            topGenres = emptyList(),
            topArtists = emptyList(),
            topSongs = listOf(radioTrack2)
        )
        coEvery { mockTasteProfileManager.computeTasteProfile() } returns expectedProfile

        playerViewModel.playTopTasteMix().join()
        advanceUntilIdle()

        assertThat(playerViewModel.tasteProfile.value).isEqualTo(expectedProfile)
        verify { mockQueueStateHolder.setOriginalQueueOrder(listOf(radioTrack2)) }
    }

    @Test
    @DisplayName("playTopTasteMix emits toast when topSongs is empty")
    fun test_playTopTasteMix_whenNoTopSongs_emitsToast() = runTest(testDispatcher) {
        val emptyProfile = TasteProfile(
            archetypeTitle = "Melody Connoisseur",
            archetypeSubtitle = "Guided by timeless songwriting",
            archetypeEmoji = "🎵",
            totalListeningDurationMs = 0L,
            totalPlays = 0,
            topGenres = emptyList(),
            topArtists = emptyList(),
            topSongs = emptyList()
        )
        coEvery { mockTasteProfileManager.computeTasteProfile() } returns emptyProfile

        playerViewModel.playTopTasteMix().join()
        advanceUntilIdle()

        verify(exactly = 0) { mockQueueStateHolder.setOriginalQueueOrder(any()) }
    }
}
