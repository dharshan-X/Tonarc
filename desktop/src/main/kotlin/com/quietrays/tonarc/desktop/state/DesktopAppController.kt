package com.quietrays.tonarc.desktop.state

import com.quietrays.tonarc.data.model.Lyrics
import com.quietrays.tonarc.desktop.audio.MpvPlaybackState
import com.quietrays.tonarc.desktop.audio.MpvPlayerEngine
import com.quietrays.tonarc.desktop.data.DesktopLyricsRepository
import com.quietrays.tonarc.desktop.data.DesktopStateStore
import com.quietrays.tonarc.desktop.data.DesktopYouTubeClient
import com.quietrays.tonarc.desktop.data.LocalLibraryScanner
import com.quietrays.tonarc.desktop.data.toDesktopAlbum
import com.quietrays.tonarc.desktop.data.toDesktopArtist
import com.quietrays.tonarc.desktop.data.toDesktopPlaylist
import com.quietrays.tonarc.desktop.data.toDesktopTrack
import com.quietrays.tonarc.desktop.model.DesktopAlbum
import com.quietrays.tonarc.desktop.model.DesktopArtist
import com.quietrays.tonarc.desktop.model.DesktopNavDestination
import com.quietrays.tonarc.desktop.model.DesktopPlaylist
import com.quietrays.tonarc.desktop.model.DesktopTrack
import com.quietrays.tonarc.desktop.model.EqualizerPreset
import com.quietrays.tonarc.desktop.model.HomeShelf
import com.quietrays.tonarc.desktop.model.InspectorTab
import com.quietrays.tonarc.desktop.model.LibrarySortOption
import com.quietrays.tonarc.desktop.model.LibraryTab
import com.quietrays.tonarc.desktop.model.PersistedDesktopSettings
import com.quietrays.tonarc.desktop.model.RepeatMode
import com.quietrays.tonarc.desktop.model.ReplayGainMode
import com.quietrays.tonarc.desktop.model.SearchFilter
import com.quietrays.tonarc.desktop.model.ThemeMode
import com.quietrays.tonarc.desktop.model.TrackSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed interface DetailPage {
    data class Album(val album: DesktopAlbum, val isLoading: Boolean = false) : DetailPage
    data class Artist(val artist: DesktopArtist, val isLoading: Boolean = false) : DetailPage
    data class Playlist(
        val playlist: DesktopPlaylist,
        val tracks: List<DesktopTrack>,
        val isLoading: Boolean = false
    ) : DetailPage
}

data class DesktopUiState(
    val currentDestination: DesktopNavDestination = DesktopNavDestination.HOME,
    val detailPage: DetailPage? = null,
    val sidebarExpanded: Boolean = true,
    val showInspectorPane: Boolean = true,
    val inspectorTab: InspectorTab = InspectorTab.NOW_PLAYING,
    val showCompactPlayerSheet: Boolean = false,
    val searchFocusTrigger: Int = 0,

    // Library
    val musicFolders: List<String> = emptyList(),
    val localTracks: List<DesktopTrack> = emptyList(),
    val isScanningLibrary: Boolean = false,
    val libraryTab: LibraryTab = LibraryTab.SONGS,
    val librarySort: LibrarySortOption = LibrarySortOption.TITLE,
    val favoriteIds: Set<String> = emptySet(),
    val playlists: List<DesktopPlaylist> = emptyList(),
    val recentTrackIds: List<String> = emptyList(),

    // Home / YouTube Music
    val homeShelves: List<HomeShelf> = emptyList(),
    val isLoadingHome: Boolean = false,
    val homeError: String? = null,

    // Search
    val searchQuery: String = "",
    val searchFilter: SearchFilter = SearchFilter.ALL,
    val isSearching: Boolean = false,
    val ytSearchSongs: List<DesktopTrack> = emptyList(),
    val ytSearchAlbums: List<DesktopAlbum> = emptyList(),
    val ytSearchArtists: List<DesktopArtist> = emptyList(),
    val ytSearchPlaylists: List<DesktopPlaylist> = emptyList(),

    // Playback
    val currentTrack: DesktopTrack? = null,
    val queue: List<DesktopTrack> = emptyList(),
    val queueIndex: Int = -1,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isResolvingStream: Boolean = false,
    val mpvState: MpvPlaybackState = MpvPlaybackState(),

    // Lyrics
    val currentLyrics: Lyrics = Lyrics(),
    val isLoadingLyrics: Boolean = false,

    // Preferences & Audio
    val themeMode: ThemeMode = ThemeMode.DARK,
    val eqPresetName: String = "Flat",
    val eqBands: List<Float> = List(10) { 0f },
    val replayGain: ReplayGainMode = ReplayGainMode.TRACK,
    val playbackSpeed: Float = 1.0f,
    val youtubeCookies: String? = null,
    val statusMessage: String? = null
)

class DesktopAppController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val stateStore = DesktopStateStore()
    private val libraryScanner = LocalLibraryScanner()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private var persisted: PersistedDesktopSettings = stateStore.load()

    private val youTubeClient = DesktopYouTubeClient(
        baseOkHttpClient = okHttpClient,
        initialVisitorData = persisted.youtubeVisitorData,
        initialCookies = persisted.youtubeCookies,
        onVisitorDataUpdated = { vData ->
            persisted = persisted.copy(youtubeVisitorData = vData)
            stateStore.save(persisted)
        }
    )

    private val lyricsRepository = DesktopLyricsRepository(okHttpClient, youTubeClient)

    private val mpvEngine = MpvPlayerEngine(
        onTrackFinished = { scope.launch { handleTrackFinished() } },
        onPlaybackError = { err ->
            scope.launch {
                _uiState.update { it.copy(statusMessage = err, isResolvingStream = false) }
            }
        }
    )

    private val _uiState = MutableStateFlow(buildInitialState())
    val uiState: StateFlow<DesktopUiState> = _uiState.asStateFlow()

    private val trackRegistry = LinkedHashMap<String, DesktopTrack>()
    private var playJob: Job? = null
    private var lyricsJob: Job? = null
    private var searchJob: Job? = null

    init {
        scope.launch {
            mpvEngine.state.collect { mpv ->
                _uiState.update { it.copy(mpvState = mpv) }
            }
        }
        mpvEngine.setVolume(persisted.volume)
        mpvEngine.setReplayGain(runCatching { ReplayGainMode.valueOf(persisted.replayGain) }.getOrDefault(ReplayGainMode.TRACK))
        mpvEngine.setSpeed(persisted.playbackSpeed)
        mpvEngine.setEqualizerBands(persisted.eqBands)

        rescanLibrary()
        loadHomeShelves()
    }

    private fun buildInitialState(): DesktopUiState {
        val theme = runCatching { ThemeMode.valueOf(persisted.themeMode) }.getOrDefault(ThemeMode.DARK)
        val rg = runCatching { ReplayGainMode.valueOf(persisted.replayGain) }.getOrDefault(ReplayGainMode.TRACK)
        return DesktopUiState(
            musicFolders = persisted.musicFolders,
            favoriteIds = persisted.favoriteTrackIds,
            playlists = persisted.playlists,
            recentTrackIds = persisted.recentTrackIds,
            themeMode = theme,
            eqPresetName = persisted.eqPresetName,
            eqBands = if (persisted.eqBands.size == 10) persisted.eqBands else List(10) { 0f },
            replayGain = rg,
            playbackSpeed = persisted.playbackSpeed,
            youtubeCookies = persisted.youtubeCookies,
            mpvState = mpvEngine.state.value
        )
    }

    fun navigate(destination: DesktopNavDestination) {
        _uiState.update { it.copy(currentDestination = destination, detailPage = null) }
    }

    fun focusSearch() {
        _uiState.update {
            it.copy(
                currentDestination = DesktopNavDestination.SEARCH,
                detailPage = null,
                searchFocusTrigger = it.searchFocusTrigger + 1
            )
        }
    }

    fun toggleSidebarExpanded() {
        _uiState.update { it.copy(sidebarExpanded = !it.sidebarExpanded) }
    }

    fun toggleInspectorPane(tab: InspectorTab? = null) {
        _uiState.update { state ->
            if (tab != null) {
                if (state.showInspectorPane && state.inspectorTab == tab) {
                    state.copy(showInspectorPane = false)
                } else {
                    state.copy(showInspectorPane = true, inspectorTab = tab)
                }
            } else {
                state.copy(showInspectorPane = !state.showInspectorPane)
            }
        }
    }

    fun selectInspectorTab(tab: InspectorTab) {
        _uiState.update { it.copy(showInspectorPane = true, inspectorTab = tab) }
    }

    fun setCompactPlayerSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showCompactPlayerSheet = visible) }
    }

    fun selectLibraryTab(tab: LibraryTab) {
        _uiState.update { it.copy(libraryTab = tab) }
    }

    fun selectLibrarySort(sort: LibrarySortOption) {
        _uiState.update { it.copy(librarySort = sort) }
    }

    fun dismissStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun rescanLibrary() {
        scope.launch {
            _uiState.update { it.copy(isScanningLibrary = true) }
            val tracks = libraryScanner.scanFolders(_uiState.value.musicFolders)
            tracks.forEach { trackRegistry[it.id] = it }
            _uiState.update { it.copy(localTracks = tracks, isScanningLibrary = false) }
        }
    }

    fun addMusicFolder(folderPath: String) {
        val dir = File(folderPath)
        if (!dir.isDirectory) return
        val current = _uiState.value.musicFolders
        if (dir.absolutePath in current) {
            rescanLibrary()
            return
        }
        val updated = current + dir.absolutePath
        persisted = persisted.copy(musicFolders = updated)
        stateStore.save(persisted)
        _uiState.update { it.copy(musicFolders = updated) }
        rescanLibrary()
    }

    fun removeMusicFolder(folderPath: String) {
        val updated = _uiState.value.musicFolders.filterNot { it == folderPath }
        persisted = persisted.copy(musicFolders = updated)
        stateStore.save(persisted)
        _uiState.update { it.copy(musicFolders = updated) }
        rescanLibrary()
    }

    fun loadHomeShelves() {
        scope.launch {
            _uiState.update { it.copy(isLoadingHome = true, homeError = null) }
            val shelves = runCatching { youTubeClient.getHomeShelves() }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoadingHome = false, homeError = err.message) }
                }
                .getOrNull() ?: return@launch

            shelves.flatMap { it.tracks }.forEach { trackRegistry[it.id] = it }
            _uiState.update { it.copy(homeShelves = shelves, isLoadingHome = false, homeError = null) }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    isSearching = false,
                    ytSearchSongs = emptyList(),
                    ytSearchAlbums = emptyList(),
                    ytSearchArtists = emptyList(),
                    ytSearchPlaylists = emptyList()
                )
            }
            return
        }

        searchJob = scope.launch {
            kotlinx.coroutines.delay(320)
            executeSearch(query)
        }
    }

    fun setSearchFilter(filter: SearchFilter) {
        _uiState.update { it.copy(searchFilter = filter) }
        val q = _uiState.value.searchQuery
        if (q.isNotBlank()) {
            searchJob?.cancel()
            searchJob = scope.launch { executeSearch(q) }
        }
    }

    private suspend fun executeSearch(query: String) {
        val filter = _uiState.value.searchFilter
        if (filter == SearchFilter.LOCAL) {
            _uiState.update { it.copy(isSearching = false) }
            return
        }
        _uiState.update { it.copy(isSearching = true) }

        val songsRes = if (filter == SearchFilter.ALL || filter == SearchFilter.YOUTUBE_SONGS) {
            youTubeClient.search(query, DesktopYouTubeClient.YTM_FILTER_SONGS)
        } else null
        val albumsRes = if (filter == SearchFilter.ALL || filter == SearchFilter.YOUTUBE_ALBUMS) {
            youTubeClient.search(query, DesktopYouTubeClient.YTM_FILTER_ALBUMS)
        } else null
        val artistsRes = if (filter == SearchFilter.ALL || filter == SearchFilter.YOUTUBE_ARTISTS) {
            youTubeClient.search(query, DesktopYouTubeClient.YTM_FILTER_ARTISTS)
        } else null
        val playlistsRes = if (filter == SearchFilter.ALL || filter == SearchFilter.YOUTUBE_PLAYLISTS) {
            youTubeClient.search(query, DesktopYouTubeClient.YTM_FILTER_PLAYLISTS)
        } else null

        val songs = songsRes?.songs?.map { it.toDesktopTrack() }.orEmpty()
        songs.forEach { trackRegistry[it.id] = it }

        _uiState.update {
            it.copy(
                isSearching = false,
                ytSearchSongs = songs,
                ytSearchAlbums = albumsRes?.albums?.map { a -> a.toDesktopAlbum() }.orEmpty(),
                ytSearchArtists = artistsRes?.artists?.map { ar -> ar.toDesktopArtist() }.orEmpty(),
                ytSearchPlaylists = playlistsRes?.playlists?.map { p -> p.toDesktopPlaylist() }.orEmpty()
            )
        }
    }

    fun openAlbum(album: DesktopAlbum) {
        if (album.source == TrackSource.LOCAL || album.browseId.isNullOrBlank()) {
            val tracks = if (album.tracks.isNotEmpty()) {
                album.tracks
            } else {
                _uiState.value.localTracks.filter { it.album == album.title }
            }
            _uiState.update { it.copy(detailPage = DetailPage.Album(album.copy(tracks = tracks))) }
            return
        }
        _uiState.update { it.copy(detailPage = DetailPage.Album(album, isLoading = true)) }
        scope.launch {
            val loaded = youTubeClient.getAlbum(album.browseId)
            if (loaded != null) {
                loaded.tracks.forEach { trackRegistry[it.id] = it }
                _uiState.update { it.copy(detailPage = DetailPage.Album(loaded, isLoading = false)) }
            } else {
                _uiState.update { it.copy(detailPage = DetailPage.Album(album, isLoading = false)) }
            }
        }
    }

    fun openArtist(artist: DesktopArtist) {
        if (artist.source == TrackSource.LOCAL || artist.browseId.isNullOrBlank()) {
            val tracks = if (artist.tracks.isNotEmpty()) {
                artist.tracks
            } else {
                _uiState.value.localTracks.filter { it.artist == artist.name }
            }
            _uiState.update { it.copy(detailPage = DetailPage.Artist(artist.copy(tracks = tracks))) }
            return
        }
        _uiState.update { it.copy(detailPage = DetailPage.Artist(artist, isLoading = true)) }
        scope.launch {
            val loaded = youTubeClient.getArtist(artist.browseId)
            if (loaded != null) {
                loaded.tracks.forEach { trackRegistry[it.id] = it }
                _uiState.update { it.copy(detailPage = DetailPage.Artist(loaded, isLoading = false)) }
            } else {
                _uiState.update { it.copy(detailPage = DetailPage.Artist(artist, isLoading = false)) }
            }
        }
    }

    fun openPlaylist(playlist: DesktopPlaylist) {
        if (!playlist.isYouTube || playlist.browseId.isNullOrBlank()) {
            val tracks = playlist.trackIds.mapNotNull { id ->
                trackRegistry[id] ?: _uiState.value.localTracks.firstOrNull { it.id == id }
            }
            _uiState.update { it.copy(detailPage = DetailPage.Playlist(playlist, tracks)) }
            return
        }
        _uiState.update { it.copy(detailPage = DetailPage.Playlist(playlist, emptyList(), isLoading = true)) }
        scope.launch {
            val loaded = youTubeClient.getPlaylist(playlist.browseId)
            if (loaded != null) {
                loaded.second.forEach { trackRegistry[it.id] = it }
                _uiState.update {
                    it.copy(detailPage = DetailPage.Playlist(loaded.first, loaded.second, isLoading = false))
                }
            } else {
                _uiState.update {
                    it.copy(detailPage = DetailPage.Playlist(playlist, emptyList(), isLoading = false))
                }
            }
        }
    }

    fun closeDetail() {
        _uiState.update { it.copy(detailPage = null) }
    }

    fun playTrack(track: DesktopTrack, contextQueue: List<DesktopTrack> = listOf(track)) {
        trackRegistry[track.id] = track
        contextQueue.forEach { trackRegistry[it.id] = it }

        val effectiveQueue = if (contextQueue.any { it.id == track.id }) contextQueue else listOf(track) + contextQueue
        val idx = effectiveQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        val updatedRecent = (listOf(track.id) + _uiState.value.recentTrackIds.filterNot { it == track.id }).take(40)
        persisted = persisted.copy(recentTrackIds = updatedRecent)
        stateStore.save(persisted)

        _uiState.update {
            it.copy(
                currentTrack = track,
                queue = effectiveQueue,
                queueIndex = idx,
                recentTrackIds = updatedRecent,
                statusMessage = null
            )
        }

        startPlaybackOf(track)

        // Populate radio queue when playing a single YouTube track
        if (track.source == TrackSource.YOUTUBE && effectiveQueue.size == 1 && !track.videoId.isNullOrBlank()) {
            scope.launch {
                val radio = youTubeClient.getRadioTracks(track.videoId)
                    .filterNot { it.id == track.id }
                if (radio.isNotEmpty()) {
                    radio.forEach { trackRegistry[it.id] = it }
                    _uiState.update { state ->
                        if (state.currentTrack?.id == track.id && state.queue.size == 1) {
                            state.copy(queue = listOf(track) + radio)
                        } else {
                            state
                        }
                    }
                }
            }
        }
    }

    private fun startPlaybackOf(track: DesktopTrack) {
        playJob?.cancel()
        loadLyricsFor(track)

        if (track.source == TrackSource.LOCAL) {
            val path = track.filePath
            if (path.isNullOrBlank() || !File(path).isFile) {
                _uiState.update { it.copy(statusMessage = "Local file not found: ${track.title}") }
                return
            }
            _uiState.update { it.copy(isResolvingStream = false) }
            mpvEngine.playUri(path)
            return
        }

        val videoId = track.videoId
        if (videoId.isNullOrBlank()) {
            _uiState.update { it.copy(statusMessage = "Missing YouTube video ID for ${track.title}") }
            return
        }

        playJob = scope.launch {
            _uiState.update { it.copy(isResolvingStream = true) }
            val streamUrl = youTubeClient.resolveAudioStreamUrl(videoId)
            if (streamUrl.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isResolvingStream = false,
                        statusMessage = "Could not resolve audio stream for \"${track.title}\""
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(isResolvingStream = false) }
            mpvEngine.playUri(streamUrl)
        }
    }

    private fun loadLyricsFor(track: DesktopTrack) {
        lyricsJob?.cancel()
        _uiState.update { it.copy(isLoadingLyrics = true, currentLyrics = Lyrics()) }
        lyricsJob = scope.launch {
            val lyrics = lyricsRepository.getLyrics(track)
            if (_uiState.value.currentTrack?.id == track.id) {
                _uiState.update { it.copy(currentLyrics = lyrics, isLoadingLyrics = false) }
            }
        }
    }

    fun togglePlayPause() {
        val state = _uiState.value
        if (state.currentTrack == null) {
            val fallback = state.localTracks.firstOrNull()
                ?: state.homeShelves.firstOrNull()?.tracks?.firstOrNull()
            if (fallback != null) {
                playTrack(fallback, state.localTracks.ifEmpty { listOf(fallback) })
            }
            return
        }
        mpvEngine.togglePlayPause()
    }

    fun playNext(fromUserAction: Boolean = true) {
        val state = _uiState.value
        if (state.queue.isEmpty()) return

        if (!fromUserAction && state.repeatMode == RepeatMode.ONE) {
            state.currentTrack?.let { startPlaybackOf(it) }
            return
        }

        val nextIdx = if (state.isShuffleEnabled && state.queue.size > 1) {
            val candidates = state.queue.indices.filter { it != state.queueIndex }
            candidates.random()
        } else {
            val candidate = state.queueIndex + 1
            if (candidate < state.queue.size) {
                candidate
            } else if (state.repeatMode == RepeatMode.ALL || fromUserAction) {
                0
            } else {
                return
            }
        }

        val nextTrack = state.queue.getOrNull(nextIdx) ?: return
        _uiState.update { it.copy(currentTrack = nextTrack, queueIndex = nextIdx) }
        startPlaybackOf(nextTrack)
    }

    fun playPrevious() {
        val state = _uiState.value
        if (state.queue.isEmpty()) return

        if (state.mpvState.positionMs > 4_000L) {
            mpvEngine.seekTo(0L)
            return
        }

        val prevIdx = if (state.queueIndex > 0) state.queueIndex - 1 else state.queue.lastIndex
        val prevTrack = state.queue.getOrNull(prevIdx) ?: return
        _uiState.update { it.copy(currentTrack = prevTrack, queueIndex = prevIdx) }
        startPlaybackOf(prevTrack)
    }

    private fun handleTrackFinished() {
        val state = _uiState.value
        if (state.queue.isEmpty()) return
        if (state.repeatMode == RepeatMode.ONE) {
            state.currentTrack?.let { startPlaybackOf(it) }
            return
        }
        val hasNext = state.isShuffleEnabled ||
            state.queueIndex + 1 < state.queue.size ||
            state.repeatMode == RepeatMode.ALL
        if (hasNext) {
            playNext(fromUserAction = false)
        }
    }

    fun jumpToQueueIndex(index: Int) {
        val track = _uiState.value.queue.getOrNull(index) ?: return
        _uiState.update { it.copy(currentTrack = track, queueIndex = index) }
        startPlaybackOf(track)
    }

    fun addToQueue(track: DesktopTrack) {
        trackRegistry[track.id] = track
        _uiState.update { state ->
            if (state.currentTrack == null) {
                playTrack(track, listOf(track))
                return
            }
            state.copy(
                queue = state.queue + track,
                statusMessage = "Added \"${track.title}\" to queue"
            )
        }
    }

    fun playNextInQueue(track: DesktopTrack) {
        trackRegistry[track.id] = track
        _uiState.update { state ->
            if (state.currentTrack == null) {
                playTrack(track, listOf(track))
                return
            }
            val insertAt = (state.queueIndex + 1).coerceIn(0, state.queue.size)
            val updated = state.queue.toMutableList().apply { add(insertAt, track) }
            state.copy(
                queue = updated,
                statusMessage = "\"${track.title}\" will play next"
            )
        }
    }

    fun removeFromQueue(index: Int) {
        _uiState.update { state ->
            if (index !in state.queue.indices || index == state.queueIndex) return@update state
            val updated = state.queue.toMutableList().apply { removeAt(index) }
            val adjustedIdx = if (index < state.queueIndex) state.queueIndex - 1 else state.queueIndex
            state.copy(queue = updated, queueIndex = adjustedIdx)
        }
    }

    fun clearQueue() {
        _uiState.update { state ->
            val current = state.currentTrack
            if (current == null) {
                state.copy(queue = emptyList(), queueIndex = -1)
            } else {
                state.copy(queue = listOf(current), queueIndex = 0)
            }
        }
    }

    fun toggleShuffle() {
        _uiState.update { it.copy(isShuffleEnabled = !it.isShuffleEnabled) }
    }

    fun cycleRepeatMode() {
        _uiState.update { state ->
            val next = when (state.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            state.copy(repeatMode = next)
        }
    }

    fun seekTo(positionMs: Long) = mpvEngine.seekTo(positionMs)

    fun seekRelative(deltaMs: Long) = mpvEngine.seekRelative(deltaMs)

    fun setVolume(volumePercent: Int) {
        val clamped = volumePercent.coerceIn(0, 120)
        mpvEngine.setVolume(clamped)
        persisted = persisted.copy(volume = clamped)
        stateStore.save(persisted)
    }

    fun adjustVolume(delta: Int) {
        setVolume(_uiState.value.mpvState.volume + delta)
    }

    fun toggleMute() {
        mpvEngine.setMuted(!_uiState.value.mpvState.isMuted)
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        mpvEngine.setSpeed(clamped)
        persisted = persisted.copy(playbackSpeed = clamped)
        stateStore.save(persisted)
        _uiState.update { it.copy(playbackSpeed = clamped) }
    }

    fun setReplayGain(mode: ReplayGainMode) {
        mpvEngine.setReplayGain(mode)
        persisted = persisted.copy(replayGain = mode.name)
        stateStore.save(persisted)
        _uiState.update { it.copy(replayGain = mode) }
    }

    fun selectEqPreset(preset: EqualizerPreset) {
        mpvEngine.setEqualizerBands(preset.bands)
        persisted = persisted.copy(eqPresetName = preset.name, eqBands = preset.bands)
        stateStore.save(persisted)
        _uiState.update { it.copy(eqPresetName = preset.name, eqBands = preset.bands) }
    }

    fun setEqBand(index: Int, gainDb: Float) {
        val current = _uiState.value.eqBands.toMutableList()
        if (index !in current.indices) return
        current[index] = gainDb.coerceIn(-12f, 12f)
        mpvEngine.setEqualizerBands(current)
        persisted = persisted.copy(eqPresetName = "Custom", eqBands = current)
        stateStore.save(persisted)
        _uiState.update { it.copy(eqPresetName = "Custom", eqBands = current) }
    }

    fun setThemeMode(mode: ThemeMode) {
        persisted = persisted.copy(themeMode = mode.name)
        stateStore.save(persisted)
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun toggleFavorite(track: DesktopTrack) {
        trackRegistry[track.id] = track
        val current = _uiState.value.favoriteIds
        val updated = if (track.id in current) current - track.id else current + track.id
        persisted = persisted.copy(favoriteTrackIds = updated)
        stateStore.save(persisted)
        _uiState.update { it.copy(favoriteIds = updated) }
    }

    fun createPlaylist(name: String, initialTrack: DesktopTrack? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        if (initialTrack != null) trackRegistry[initialTrack.id] = initialTrack
        val playlist = DesktopPlaylist(
            id = "pl:${UUID.randomUUID()}",
            name = trimmed,
            trackIds = initialTrack?.let { listOf(it.id) } ?: emptyList()
        )
        val updated = _uiState.value.playlists + playlist
        persisted = persisted.copy(playlists = updated)
        stateStore.save(persisted)
        _uiState.update { it.copy(playlists = updated, statusMessage = "Created playlist \"$trimmed\"") }
    }

    fun addTrackToPlaylist(playlistId: String, track: DesktopTrack) {
        trackRegistry[track.id] = track
        val updated = _uiState.value.playlists.map { pl ->
            if (pl.id == playlistId && track.id !in pl.trackIds) {
                pl.copy(trackIds = pl.trackIds + track.id)
            } else pl
        }
        persisted = persisted.copy(playlists = updated)
        stateStore.save(persisted)
        _uiState.update { it.copy(playlists = updated, statusMessage = "Added \"${track.title}\" to playlist") }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        val updated = _uiState.value.playlists.map { pl ->
            if (pl.id == playlistId) pl.copy(trackIds = pl.trackIds.filterNot { it == trackId }) else pl
        }
        persisted = persisted.copy(playlists = updated)
        stateStore.save(persisted)
        _uiState.update { state ->
            val updatedDetail = (state.detailPage as? DetailPage.Playlist)?.let { detail ->
                if (detail.playlist.id == playlistId) {
                    val pl = updated.first { it.id == playlistId }
                    detail.copy(playlist = pl, tracks = detail.tracks.filterNot { it.id == trackId })
                } else detail
            }
            state.copy(playlists = updated, detailPage = updatedDetail ?: state.detailPage)
        }
    }

    fun deletePlaylist(playlistId: String) {
        val updated = _uiState.value.playlists.filterNot { it.id == playlistId }
        persisted = persisted.copy(playlists = updated)
        stateStore.save(persisted)
        _uiState.update { state ->
            val closeDetail = (state.detailPage as? DetailPage.Playlist)?.playlist?.id == playlistId
            state.copy(
                playlists = updated,
                detailPage = if (closeDetail) null else state.detailPage
            )
        }
    }

    fun updateYouTubeCookies(cookies: String?) {
        val normalized = cookies?.trim()?.takeIf { it.isNotBlank() }
        youTubeClient.authCookies = normalized
        persisted = persisted.copy(youtubeCookies = normalized)
        stateStore.save(persisted)
        _uiState.update { it.copy(youtubeCookies = normalized, statusMessage = "Updated YouTube Music session") }
        loadHomeShelves()
    }

    fun resolveTrackById(id: String): DesktopTrack? {
        return trackRegistry[id] ?: _uiState.value.localTracks.firstOrNull { it.id == id }
    }

    fun shutdown() {
        mpvEngine.shutdown()
    }
}
