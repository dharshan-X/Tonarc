package com.quietrays.tonarc.presentation.viewmodel

import com.quietrays.tonarc.data.ContextualMix
import com.quietrays.tonarc.data.DailyMixManager
import com.quietrays.tonarc.data.MixMood
import com.quietrays.tonarc.data.database.YouTubeDao
import com.quietrays.tonarc.data.database.YouTubeSongEntity
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.preferences.UserPreferencesRepository
import com.quietrays.tonarc.data.repository.MusicRepository
import com.quietrays.tonarc.data.youtube.YouTubeRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Daily Mix and Your Mix state with deep YouTube Music and local library integration.
 * Extracted from PlayerViewModel to improve modularity.
 *
 * Responsibilities:
 * - Generate and update daily/your mixes combining local library and YouTube Music recommendations
 * - Persist and restore mix state across app launches
 * - Check if mix needs updating based on day change
 */
@Singleton
class DailyMixStateHolder @Inject constructor(
    val dailyMixManager: DailyMixManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicRepository: MusicRepository,
    private val youTubeRepository: YouTubeRepository,
    private val youTubeDao: YouTubeDao
) {
    internal var ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO

    constructor(
        dailyMixManager: DailyMixManager,
        userPreferencesRepository: UserPreferencesRepository,
        musicRepository: MusicRepository,
        youTubeRepository: YouTubeRepository,
        youTubeDao: YouTubeDao,
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
    ) : this(dailyMixManager, userPreferencesRepository, musicRepository, youTubeRepository, youTubeDao) {
        this.ioDispatcher = ioDispatcher
    }
    private var scope: CoroutineScope? = null
    private var updateJob: Job? = null

    private val _dailyMixSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val dailyMixSongs: StateFlow<ImmutableList<Song>> = _dailyMixSongs.asStateFlow()

    private val _yourMixSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val yourMixSongs: StateFlow<ImmutableList<Song>> = _yourMixSongs.asStateFlow()

    private val _contextualMixes = MutableStateFlow<List<ContextualMix>>(emptyList())
    val contextualMixes: StateFlow<List<ContextualMix>> = _contextualMixes.asStateFlow()

    private val _selectedMood = MutableStateFlow<MixMood>(dailyMixManager.getCurrentTimeMood())
    val selectedMood: StateFlow<MixMood> = _selectedMood.asStateFlow()

    /**
     * Select a contextual mix mood.
     */
    fun selectMood(mood: MixMood) {
        _selectedMood.value = mood
    }

    /**
     * Initialize with coroutine scope from ViewModel.
     */
    fun initialize(coroutineScope: CoroutineScope) {
        scope = coroutineScope
    }

    /**
     * Remove a song from the daily mix.
     */
    fun removeFromDailyMix(songId: String) {
        _dailyMixSongs.update { currentList ->
            currentList.filterNot { it.id == songId }.toImmutableList()
        }
    }

    /**
     * Update the daily mix and your mix with multi-source candidate songs
     * (Local media library + YouTube Music cached/discovered/personalized mixes).
     */
    fun updateDailyMix(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        updateJob?.cancel()
        updateJob = scope?.launch(ioDispatcher) {
            val localSongs = runCatching { musicRepository.getAllSongsOnce() }.getOrDefault(emptyList())
            val ytCachedSongs = runCatching { youTubeDao.getAllYouTubeSongsList().map { it.toSong() } }.getOrDefault(emptyList())

            // Fetch YouTube Music quick picks & recommendations
            val ytRecs = runCatching { youTubeRepository.getHomeRecommendations() }.getOrNull()
            val ytQuickPicks = ytRecs?.quickPicks ?: emptyList()
            val ytCommunity = ytRecs?.fromCommunity ?: emptyList()

            // Fetch top songs from user's favorite artists on YouTube Music
            val favoriteArtistNames = runCatching { userPreferencesRepository.favoriteArtistsFlow.first() }.getOrDefault(emptySet())
            val favArtistSongs = if (favoriteArtistNames.isNotEmpty()) {
                favoriteArtistNames.flatMap { artistName ->
                    runCatching { youTubeRepository.searchSongsPaginated(artistName).songs.take(10) }.getOrDefault(emptyList())
                }
            } else emptyList()

            val allYtDiscovered = (ytQuickPicks + ytCommunity + favArtistSongs).distinctBy { it.id }

            // Cache discovered YouTube tracks into YouTubeDao for persistent offline / history retrieval
            if (allYtDiscovered.isNotEmpty()) {
                val entities = allYtDiscovered.map { song ->
                    val videoId = song.youtubeId ?: song.id.removePrefix("youtube_")
                    YouTubeSongEntity(
                        id = song.id,
                        videoId = videoId,
                        playlistId = "__mix_cache__",
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        duration = song.duration,
                        thumbnailUrl = song.albumArtUriString,
                        year = song.year,
                        dateAdded = System.currentTimeMillis()
                    )
                }
                runCatching { youTubeDao.insertSongs(entities) }
            }

            val allCandidateSongs = (localSongs + ytCachedSongs + allYtDiscovered).distinctBy { it.id }

            if (allCandidateSongs.isNotEmpty()) {
                val favoriteIds = favoriteSongIdsFlow.first()

                val mix = dailyMixManager.generateDailyMix(allCandidateSongs, favoriteIds)
                _dailyMixSongs.value = mix.toImmutableList()
                userPreferencesRepository.saveDailyMixSongIds(mix.map { it.id })

                val yourMix = dailyMixManager.generateYourMix(allCandidateSongs, favoriteIds)
                _yourMixSongs.value = yourMix.toImmutableList()
                userPreferencesRepository.saveYourMixSongIds(yourMix.map { it.id })

                val contextual = dailyMixManager.generateAllContextualMixes(allCandidateSongs, favoriteIds)
                _contextualMixes.value = contextual
            } else {
                _dailyMixSongs.value = persistentListOf()
                _yourMixSongs.value = persistentListOf()
                _contextualMixes.value = emptyList()
            }
        }
    }

    /**
     * Load persisted daily mix from storage using multi-source ID resolution.
     */
    fun loadPersistedDailyMix() {
        scope?.launch(ioDispatcher) {
            val dailyMixIds = userPreferencesRepository.dailyMixSongIdsFlow.first()
            if (dailyMixIds.isNotEmpty() && _dailyMixSongs.value.isEmpty()) {
                val songs = musicRepository.getSongsByIds(dailyMixIds).first()
                if (songs.isNotEmpty()) {
                    val songMap = mutableMapOf<String, Song>()
                    songs.forEach { song ->
                        songMap[song.id] = song
                        songMap[song.id.removePrefix("youtube_")] = song
                        song.youtubeId?.let { songMap[it] = song }
                    }
                    val orderedSongs = dailyMixIds.mapNotNull { id ->
                        songMap[id] ?: songMap["youtube_$id"] ?: songMap[id.removePrefix("youtube_")]
                    }
                    if (orderedSongs.isNotEmpty()) {
                        _dailyMixSongs.value = orderedSongs.toImmutableList()
                    }
                }
            }
        }

        scope?.launch(ioDispatcher) {
            val yourMixIds = userPreferencesRepository.yourMixSongIdsFlow.first()
            if (yourMixIds.isNotEmpty() && _yourMixSongs.value.isEmpty()) {
                val songs = musicRepository.getSongsByIds(yourMixIds).first()
                if (songs.isNotEmpty()) {
                    val songMap = mutableMapOf<String, Song>()
                    songs.forEach { song ->
                        songMap[song.id] = song
                        songMap[song.id.removePrefix("youtube_")] = song
                        song.youtubeId?.let { songMap[it] = song }
                    }
                    val orderedSongs = yourMixIds.mapNotNull { id ->
                        songMap[id] ?: songMap["youtube_$id"] ?: songMap[id.removePrefix("youtube_")]
                    }
                    if (orderedSongs.isNotEmpty()) {
                        _yourMixSongs.value = orderedSongs.toImmutableList()
                    }
                }
            }
        }
    }

    /**
     * Force update the daily mix regardless of day.
     */
    fun forceUpdate(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        scope?.launch {
            updateDailyMix(favoriteSongIdsFlow)
            userPreferencesRepository.saveLastDailyMixUpdateTimestamp(System.currentTimeMillis())
        }
    }

    /**
     * Check if daily mix needs updating (new day) and update if so.
     */
    fun checkAndUpdateIfNeeded(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        scope?.launch {
            val lastUpdate = userPreferencesRepository.lastDailyMixUpdateFlow.first()
            val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
            val lastUpdateDay = Calendar.getInstance().apply {
                timeInMillis = lastUpdate
            }.get(Calendar.DAY_OF_YEAR)

            if (today != lastUpdateDay) {
                updateDailyMix(favoriteSongIdsFlow)
                userPreferencesRepository.saveLastDailyMixUpdateTimestamp(System.currentTimeMillis())
            }
        }
    }

    fun onCleared() {
        updateJob?.cancel()
        scope = null
    }
}
