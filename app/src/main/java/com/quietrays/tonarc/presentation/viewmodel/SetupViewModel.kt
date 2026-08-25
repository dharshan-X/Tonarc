package com.quietrays.tonarc.presentation.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietrays.tonarc.R
import com.quietrays.tonarc.data.backup.BackupManager
import com.quietrays.tonarc.data.backup.model.BackupTransferProgressUpdate
import com.quietrays.tonarc.data.backup.model.BackupOperationType
import com.quietrays.tonarc.data.backup.model.BackupSection
import com.quietrays.tonarc.data.backup.model.RestorePlan
import com.quietrays.tonarc.data.backup.model.RestoreResult
import com.quietrays.tonarc.data.preferences.AppThemeMode
import com.quietrays.tonarc.data.preferences.ThemePreferencesRepository
import com.quietrays.tonarc.data.preferences.UserPreferencesRepository
import com.quietrays.tonarc.data.repository.MusicRepository
import com.quietrays.tonarc.data.worker.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.io.File
import com.quietrays.tonarc.data.youtube.YouTubeRepository
import com.quietrays.tonarc.data.network.deezer.DeezerApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

data class SetupArtistItem(
    val name: String,
    val id: String = name,
    val imageUrl: String? = null
)

val DEFAULT_POPULAR_ARTISTS = listOf(
    SetupArtistItem(id = "taylor_swift", name = "Taylor Swift"),
    SetupArtistItem(id = "the_weeknd", name = "The Weeknd"),
    SetupArtistItem(id = "drake", name = "Drake"),
    SetupArtistItem(id = "billie_eilish", name = "Billie Eilish"),
    SetupArtistItem(id = "coldplay", name = "Coldplay"),
    SetupArtistItem(id = "eminem", name = "Eminem"),
    SetupArtistItem(id = "kendrick_lamar", name = "Kendrick Lamar"),
    SetupArtistItem(id = "ed_sheeran", name = "Ed Sheeran"),
    SetupArtistItem(id = "ariana_grande", name = "Ariana Grande"),
    SetupArtistItem(id = "post_malone", name = "Post Malone"),
    SetupArtistItem(id = "bruno_mars", name = "Bruno Mars"),
    SetupArtistItem(id = "dua_lipa", name = "Dua Lipa"),
    SetupArtistItem(id = "queen", name = "Queen"),
    SetupArtistItem(id = "bts", name = "BTS"),
    SetupArtistItem(id = "bad_bunny", name = "Bad Bunny"),
    SetupArtistItem(id = "imagine_dragons", name = "Imagine Dragons"),
    SetupArtistItem(id = "rihanna", name = "Rihanna"),
    SetupArtistItem(id = "justin_bieber", name = "Justin Bieber"),
    SetupArtistItem(id = "lady_gaga", name = "Lady Gaga"),
    SetupArtistItem(id = "travis_scott", name = "Travis Scott"),
    SetupArtistItem(id = "beyonce", name = "Beyoncé"),
    SetupArtistItem(id = "harry_styles", name = "Harry Styles"),
    SetupArtistItem(id = "linkin_park", name = "Linkin Park"),
    SetupArtistItem(id = "maroon_5", name = "Maroon 5"),
    SetupArtistItem(id = "adele", name = "Adele"),
    SetupArtistItem(id = "arctic_monkeys", name = "Arctic Monkeys"),
    SetupArtistItem(id = "katy_perry", name = "Katy Perry"),
    SetupArtistItem(id = "shawn_mendes", name = "Shawn Mendes"),
    SetupArtistItem(id = "lana_del_rey", name = "Lana Del Rey"),
    SetupArtistItem(id = "david_guetta", name = "David Guetta")
)

data class SetupUiState(
    val mediaPermissionGranted: Boolean = false,
    val notificationsPermissionGranted: Boolean = false,
    val isLoadingDirectories: Boolean = false,
    val blockedDirectories: Set<String> = emptySet(),
    val libraryNavigationMode: String = "tab_row",
    val navBarStyle: String = "default",
    val navBarCornerRadius: Int = 28,
    val externalLyricsEnabled: Boolean = true,
    val externalArtistImagesEnabled: Boolean = false,
    val alarmsPermissionGranted: Boolean = false,
    val appThemeMode: String = AppThemeMode.DARK,
    val isInspectingBackup: Boolean = false,
    val isRestoringBackup: Boolean = false,
    val restorePlan: RestorePlan? = null,
    val backupTransferProgress: BackupTransferProgressUpdate? = null,
    val selectedFavoriteArtists: Set<String> = emptySet(),
    val popularArtists: List<SetupArtistItem> = DEFAULT_POPULAR_ARTISTS,
    val artistSearchQuery: String = "",
    val artistSearchResults: List<SetupArtistItem> = emptyList(),
    val isSearchingArtists: Boolean = false
) {
    val hasMinimumFavoriteArtists: Boolean
        get() = selectedFavoriteArtists.size >= 5

    val allPermissionsGranted: Boolean
        get() {
            val mediaOk = mediaPermissionGranted
            val notificationsOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationsPermissionGranted else true
            return mediaOk && notificationsOk
        }
}

sealed interface SetupEvent {
    data class Message(val value: String) : SetupEvent
    data class RestoreCompleted(val message: String) : SetupEvent
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val syncManager: SyncManager,
    private val backupManager: BackupManager,
    private val musicRepository: MusicRepository,
    private val youTubeRepository: YouTubeRepository,
    private val deezerApiService: DeezerApiService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState = _uiState.asStateFlow()
    private val _events = Channel<SetupEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    
    /**
     * Expose sync progress for UI to show during initial setup
     */
    val isSyncing = syncManager.isSyncing

    private val fileExplorerStateHolder = FileExplorerStateHolder(userPreferencesRepository, viewModelScope, context)

    val currentPath = fileExplorerStateHolder.currentPath
    val currentDirectoryChildren = fileExplorerStateHolder.currentDirectoryChildren
    val blockedDirectories = fileExplorerStateHolder.blockedDirectories
    val availableStorages = fileExplorerStateHolder.availableStorages
    val selectedStorageIndex = fileExplorerStateHolder.selectedStorageIndex
    val isLoadingDirectories = fileExplorerStateHolder.isLoading
    val isExplorerPriming = fileExplorerStateHolder.isPrimingExplorer
    val isExplorerReady = fileExplorerStateHolder.isExplorerReady
    val isCurrentDirectoryResolved = fileExplorerStateHolder.isCurrentDirectoryResolved
    private var hasPendingDirectoryRuleChanges = false
    private var latestDirectoryRuleUpdateJob: Job? = null
    private var loadPopularArtistsJob: Job? = null

    init {
        loadPopularArtists()

        viewModelScope.launch {
            if (!userPreferencesRepository.initialSetupDoneFlow.first()) {
                themePreferencesRepository.initializeAppThemeMode(AppThemeMode.DARK)
            }
        }

        viewModelScope.launch {
            combine<Any?, SetupPrefsUpdate>(
                userPreferencesRepository.blockedDirectoriesFlow,
                userPreferencesRepository.libraryNavigationModeFlow,
                userPreferencesRepository.navBarStyleFlow,
                userPreferencesRepository.navBarCornerRadiusFlow,
                userPreferencesRepository.externalLyricsEnabledFlow,
                userPreferencesRepository.externalArtistImagesEnabledFlow,
                themePreferencesRepository.appThemeModeFlow
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val blockedDirectories = values[0] as Set<String>
                SetupPrefsUpdate(
                    blocked = blockedDirectories,
                    mode = values[1] as String,
                    style = values[2] as String,
                    radius = values[3] as Int,
                    externalLyricsEnabled = values[4] as Boolean,
                    externalArtistImagesEnabled = values[5] as Boolean,
                    appThemeMode = values[6] as String
                )
            }.collect { update ->
                _uiState.update { state ->
                    state.copy(
                        blockedDirectories = update.blocked,
                        libraryNavigationMode = update.mode,
                        navBarStyle = update.style,
                        navBarCornerRadius = update.radius,
                        externalLyricsEnabled = update.externalLyricsEnabled,
                        externalArtistImagesEnabled = update.externalArtistImagesEnabled,
                        appThemeMode = update.appThemeMode
                    )
                }
            }
        }

        viewModelScope.launch {
            fileExplorerStateHolder.isLoading.collect { loading ->
                _uiState.update { it.copy(isLoadingDirectories = loading) }
            }
        }
    }
    
    private data class SetupPrefsUpdate(
        val blocked: Set<String>,
        val mode: String,
        val style: String,
        val radius: Int,
        val externalLyricsEnabled: Boolean,
        val externalArtistImagesEnabled: Boolean,
        val appThemeMode: String
    )

    fun checkPermissions(context: Context) {
        val mediaPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        val notificationsPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val alarmsPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        _uiState.update {
            it.copy(
                mediaPermissionGranted = mediaPermissionGranted,
                notificationsPermissionGranted = notificationsPermissionGranted,
                alarmsPermissionGranted = alarmsPermissionGranted
            )
        }
    }

    fun loadMusicDirectories() {
        viewModelScope.launch {
            if (!userPreferencesRepository.initialSetupDoneFlow.first()) {
            }

            userPreferencesRepository.blockedDirectoriesFlow.first().let { blocked ->
                _uiState.update { it.copy(blockedDirectories = blocked) }
            }
            fileExplorerStateHolder.primeExplorerRoot()?.join()
        }
    }

    fun toggleDirectoryAllowed(file: File) {
        hasPendingDirectoryRuleChanges = true
        latestDirectoryRuleUpdateJob = viewModelScope.launch {
            fileExplorerStateHolder.toggleDirectoryAllowed(file)
        }
    }

    fun applyPendingDirectoryRuleChanges() {
        if (!hasPendingDirectoryRuleChanges) return
        hasPendingDirectoryRuleChanges = false
        viewModelScope.launch {
            latestDirectoryRuleUpdateJob?.join()
            syncManager.forceRefresh()
        }
    }

    fun loadDirectory(file: File) {
        fileExplorerStateHolder.loadDirectory(file)
    }

    fun selectStorage(index: Int) {
        fileExplorerStateHolder.selectStorage(index)
    }

    fun refreshAvailableStorages() {
        fileExplorerStateHolder.refreshAvailableStorages()
    }

    fun refreshCurrentDirectory() {
        fileExplorerStateHolder.refreshCurrentDirectory()
    }

    fun primeExplorer() {
        fileExplorerStateHolder.primeExplorerRoot()
    }

    fun openExplorer() {
        fileExplorerStateHolder.openExplorerRoot()
    }

    fun navigateUp() {
        fileExplorerStateHolder.navigateUp()
    }

    fun isAtRoot(): Boolean = fileExplorerStateHolder.isAtRoot()

    fun explorerRoot(): File = fileExplorerStateHolder.rootDirectory()

    fun setLibraryNavigationMode(mode: String) {
        viewModelScope.launch {
            userPreferencesRepository.setLibraryNavigationMode(mode)
        }
    }

    fun setNavBarStyle(style: String) {
        viewModelScope.launch {
            userPreferencesRepository.setNavBarStyle(style)
        }
    }

    fun setNavBarCornerRadius(radius: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setNavBarCornerRadius(radius)
        }
    }

    fun setExternalLyricsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setExternalLyricsEnabled(enabled)
        }
    }

    fun setExternalArtistImagesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setExternalArtistImagesEnabled(enabled)
        }
    }

    fun setAppThemeMode(mode: String) {
        viewModelScope.launch {
            themePreferencesRepository.setAppThemeMode(mode)
        }
    }

    fun setSetupComplete() {
        viewModelScope.launch {
            completeSetup(syncAfter = true)
        }
    }
    
    /**
     * Retry the initial sync if it failed.
     * Can be called from UI when user wants to retry after a failure.
     */
    fun retrySync() {
        viewModelScope.launch {
            syncManager.fullSync(deepScan = false)
        }
    }

    fun inspectBackupFile(uri: Uri) {
        if (_uiState.value.isInspectingBackup || _uiState.value.isRestoringBackup) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isInspectingBackup = true,
                    restorePlan = null,
                    backupTransferProgress = null
                )
            }
            val result = backupManager.inspectBackup(uri)
            result.fold(
                onSuccess = { plan ->
                    _uiState.update {
                        it.copy(
                            isInspectingBackup = false,
                            restorePlan = plan
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isInspectingBackup = false) }
                    _events.send(
                        SetupEvent.Message(
                            context.getString(
                                R.string.backup_invalid_format,
                                error.localizedMessage ?: context.getString(R.string.error_unknown),
                            )
                        )
                    )
                }
            )
        }
    }

    fun updateRestorePlanSelection(selectedModules: Set<BackupSection>) {
        _uiState.update { state ->
            state.restorePlan?.let { plan ->
                state.copy(restorePlan = plan.copy(selectedModules = selectedModules))
            } ?: state
        }
    }

    fun clearRestorePlan() {
        _uiState.update {
            it.copy(
                restorePlan = null,
                isInspectingBackup = false,
                isRestoringBackup = false,
                backupTransferProgress = null
            )
        }
    }

    fun restoreFromPlan(uri: Uri) {
        val plan = _uiState.value.restorePlan ?: return
        if (plan.selectedModules.isEmpty() || _uiState.value.isRestoringBackup) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRestoringBackup = true,
                    backupTransferProgress = BackupTransferProgressUpdate(
                        operation = BackupOperationType.IMPORT,
                        step = 0,
                        totalSteps = 1,
                        title = context.getString(R.string.backup_progress_preparing_restore),
                        detail = context.getString(R.string.backup_progress_starting_task),
                    )
                )
            }

            val result = backupManager.restore(uri, plan) { progress ->
                _uiState.update { state -> state.copy(backupTransferProgress = progress) }
            }

            when (result) {
                is RestoreResult.Success -> {
                    _events.send(SetupEvent.RestoreCompleted(context.getString(R.string.restore_completed_success)))
                }
                is RestoreResult.PartialFailure -> {
                    val canFinishSetup = result.succeeded.isNotEmpty() || !result.rolledBack
                    if (canFinishSetup) {
                        _events.send(
                            SetupEvent.RestoreCompleted(
                                context.getString(R.string.restore_completed_partial_issues),
                            )
                        )
                    } else {
                        _events.send(
                            SetupEvent.Message(
                                context.getString(
                                    R.string.restore_could_not_complete,
                                    result.failed.values.joinToString(),
                                ),
                            )
                        )
                    }
                }
                is RestoreResult.TotalFailure -> {
                    _events.send(SetupEvent.Message(context.getString(R.string.restore_failed_format, result.error)))
                }
            }

            _uiState.update {
                it.copy(
                    isRestoringBackup = false,
                    restorePlan = null,
                    backupTransferProgress = null
                )
            }
        }
    }

    private var artistSearchJob: Job? = null

    private fun isIgnoredArtist(name: String): Boolean {
        val lower = name.lowercase().trim()
        return lower.isBlank() ||
                lower == "unknown" ||
                lower == "unknown artist" ||
                lower == "<unknown>" ||
                lower == "various artists" ||
                lower == "various" ||
                lower == "soundtrack" ||
                lower == "va"
    }

    fun loadPopularArtists() {
        loadPopularArtistsJob?.cancel()
        loadPopularArtistsJob = viewModelScope.launch {
            val collectedArtists = mutableListOf<SetupArtistItem>()
            val seenNames = mutableSetOf<String>()
            var itemIndex = 0

            fun addArtist(name: String, id: String? = null, imageUrl: String? = null, priority: Boolean = false) {
                val trimmed = name.trim()
                val normalized = trimmed.lowercase()
                if (trimmed.isNotBlank() && !isIgnoredArtist(normalized) && seenNames.add(normalized)) {
                    val safeId = id ?: "artist_${normalized.replace(Regex("[^a-z0-9_]"), "_")}_${itemIndex++}"
                    val item = SetupArtistItem(
                        id = safeId,
                        name = trimmed,
                        imageUrl = imageUrl
                    )
                    if (priority) {
                        collectedArtists.add(0, item)
                    } else {
                        collectedArtists.add(item)
                    }
                }
            }

            // 1. Scan local library for top artists if available
            runCatching {
                val localSongs = musicRepository.getAudioFiles().first()
                if (localSongs.isNotEmpty()) {
                    val topLocalArtists = localSongs
                        .map { it.artist.trim() }
                        .filter { it.isNotBlank() && !isIgnoredArtist(it) }
                        .groupingBy { it }
                        .eachCount()
                        .entries
                        .sortedByDescending { it.value }
                        .take(15)
                        .map { it.key }

                    topLocalArtists.forEach { artistName ->
                        addArtist(name = artistName, priority = true)
                    }
                }
            }

            // 2. Query YouTube Music Charts / Trending for popular artists
            runCatching {
                val chartSongs = youTubeRepository.getCharts().first()
                chartSongs.forEach { song ->
                    if (song.artist.isNotBlank()) {
                        addArtist(name = song.artist, imageUrl = null)
                    }
                }
            }

            // 3. Add default curated artists to ensure a rich diverse catalog
            DEFAULT_POPULAR_ARTISTS.forEach { defaultArtist ->
                addArtist(
                    name = defaultArtist.name,
                    id = defaultArtist.id,
                    imageUrl = defaultArtist.imageUrl
                )
            }

            val baseList = collectedArtists.take(48)
            _uiState.update { it.copy(popularArtists = baseList) }

            // 4. Concurrently resolve high-resolution artist images using Deezer API
            val enrichedList = baseList.map { artist ->
                async {
                    if (artist.imageUrl != null) return@async artist
                    val pictureUrl = runCatching {
                        deezerApiService.searchArtist(artist.name, limit = 1).data.firstOrNull()?.let {
                            it.pictureMedium ?: it.pictureBig ?: it.picture
                        }
                    }.getOrNull()
                    artist.copy(imageUrl = pictureUrl)
                }
            }.awaitAll()

            _uiState.update { it.copy(popularArtists = enrichedList) }
        }
    }

    fun toggleFavoriteArtist(artistName: String) {
        _uiState.update { state ->
            val current = state.selectedFavoriteArtists
            val updated = if (current.contains(artistName)) {
                current - artistName
            } else {
                current + artistName
            }
            state.copy(selectedFavoriteArtists = updated)
        }
    }

    fun setArtistSearchQuery(query: String) {
        _uiState.update { it.copy(artistSearchQuery = query) }
        artistSearchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(artistSearchResults = emptyList(), isSearchingArtists = false) }
            return
        }
        artistSearchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearchingArtists = true) }
            val result = runCatching {
                youTubeRepository.searchAllPaginated(
                    query = query,
                    filterType = com.quietrays.tonarc.data.model.SearchFilterType.ARTISTS
                )
            }.getOrNull()
            val artistItems = result?.items?.mapIndexedNotNull { index, item ->
                if (item is com.quietrays.tonarc.data.model.SearchResultItem.ArtistItem) {
                    val trimmed = item.artist.name.trim()
                    if (trimmed.isNotBlank()) {
                        SetupArtistItem(
                            id = "${item.artist.id}_${trimmed}_$index",
                            name = trimmed,
                            imageUrl = item.artist.imageUrl
                        )
                    } else null
                } else null
            }?.distinctBy { it.name.lowercase().trim() } ?: emptyList()

            // Resolve images via Deezer if YouTube result lacked artwork
            val enrichedResults = artistItems.map { artist ->
                async {
                    if (artist.imageUrl != null) return@async artist
                    val pictureUrl = runCatching {
                        deezerApiService.searchArtist(artist.name, limit = 1).data.firstOrNull()?.let {
                            it.pictureMedium ?: it.pictureBig ?: it.picture
                        }
                    }.getOrNull()
                    artist.copy(imageUrl = pictureUrl)
                }
            }.awaitAll()

            _uiState.update { it.copy(artistSearchResults = enrichedResults, isSearchingArtists = false) }
        }
    }

    fun saveFavoriteArtists() {
        val selected = _uiState.value.selectedFavoriteArtists
        viewModelScope.launch {
            userPreferencesRepository.setFavoriteArtists(selected)
        }
    }

    private suspend fun completeSetup(syncAfter: Boolean) {
        val selected = _uiState.value.selectedFavoriteArtists
        if (selected.isNotEmpty()) {
            userPreferencesRepository.setFavoriteArtists(selected)
        }
        userPreferencesRepository.setInitialSetupDone(true)
        if (syncAfter) {
            syncManager.fullSync(deepScan = false)
        }
    }
}
