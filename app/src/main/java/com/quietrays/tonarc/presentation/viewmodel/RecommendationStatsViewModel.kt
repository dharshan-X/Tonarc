package com.quietrays.tonarc.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.quietrays.tonarc.data.database.EngagementDao
import com.quietrays.tonarc.data.database.ItemCooccurrenceDao
import com.quietrays.tonarc.data.database.ItemCooccurrenceEntity
import com.quietrays.tonarc.data.database.MusicDao
import com.quietrays.tonarc.data.database.OfflineTrackDao
import com.quietrays.tonarc.data.database.SongEngagementEntity
import com.quietrays.tonarc.data.database.YouTubeDao
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.recommendation.AdaptiveWeightTuner
import com.quietrays.tonarc.data.recommendation.PersonalizedRanker
import com.quietrays.tonarc.data.repository.MusicRepository
import com.quietrays.tonarc.data.worker.RecommendationWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class EnrichedEngagement(
    val entity: SongEngagementEntity,
    val song: Song?
)

data class EnrichedCooccurrence(
    val entity: ItemCooccurrenceEntity,
    val songA: Song?,
    val songB: Song?
)

data class RecommendationStatsUiState(
    val isLoading: Boolean = true,
    val totalSongsTracked: Int = 0,
    val totalPlays: Int = 0,
    val totalCompletions: Int = 0,
    val totalSkips: Int = 0,
    val totalRepeats: Int = 0,
    val totalSessions: Int = 0,
    val completionRatePct: Double = 0.0,
    val skipRatePct: Double = 0.0,
    val totalCooccurrenceEdges: Int = 0,
    val tunedWeights: PersonalizedRanker.RankingWeights = PersonalizedRanker.RankingWeights(),
    val topEngagedSongs: List<EnrichedEngagement> = emptyList(),
    val topCooccurrences: List<EnrichedCooccurrence> = emptyList(),
    val allSongs: List<Song> = emptyList(),
    val message: String? = null
)

@HiltViewModel
class RecommendationStatsViewModel internal constructor(
    private val context: Context,
    private val engagementDao: EngagementDao,
    private val itemCooccurrenceDao: ItemCooccurrenceDao,
    private val adaptiveWeightTuner: AdaptiveWeightTuner,
    private val musicRepository: MusicRepository,
    private val musicDao: MusicDao,
    private val youTubeDao: YouTubeDao,
    private val offlineTrackDao: OfflineTrackDao,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        engagementDao: EngagementDao,
        itemCooccurrenceDao: ItemCooccurrenceDao,
        adaptiveWeightTuner: AdaptiveWeightTuner,
        musicRepository: MusicRepository,
        musicDao: MusicDao,
        youTubeDao: YouTubeDao,
        offlineTrackDao: OfflineTrackDao
    ) : this(
        context = context,
        engagementDao = engagementDao,
        itemCooccurrenceDao = itemCooccurrenceDao,
        adaptiveWeightTuner = adaptiveWeightTuner,
        musicRepository = musicRepository,
        musicDao = musicDao,
        youTubeDao = youTubeDao,
        offlineTrackDao = offlineTrackDao,
        ioDispatcher = Dispatchers.IO
    )

    private val _uiState = MutableStateFlow(RecommendationStatsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            loadStatsInternal(showLoading = true)
        }
        observeEngagements()
    }

    private fun observeEngagements() {
        viewModelScope.launch {
            engagementDao.getAllEngagementsFlow()
                .distinctUntilChanged()
                .collect {
                    loadStatsInternal(showLoading = false)
                }
        }
    }

    fun loadStats(showLoading: Boolean = false) {
        viewModelScope.launch {
            loadStatsInternal(showLoading = showLoading)
        }
    }

    private suspend fun loadStatsInternal(showLoading: Boolean) {
        if (showLoading) {
            _uiState.update { it.copy(isLoading = true) }
        }

        withContext(ioDispatcher) {
            val allEngagements = engagementDao.getAllEngagements()
            val allSongs = runCatching { musicRepository.getAudioFiles().first() }.getOrDefault(emptyList())
            val songEntities = runCatching { musicDao.getSongsByIdsListSimple(emptyList()) }.getOrDefault(emptyList())
            val ytSongs = runCatching { youTubeDao.getAllYouTubeSongsList() }.getOrDefault(emptyList())
            val offlineTracks = runCatching { offlineTrackDao.getCompleted() }.getOrDefault(emptyList())

            val songsMap = mutableMapOf<String, Song>()
            allSongs.forEach { songsMap[it.id] = it }
            songEntities.forEach { entity ->
                val idStr = entity.id.toString()
                if (!songsMap.containsKey(idStr)) {
                    songsMap[idStr] = Song(
                        id = idStr,
                        title = entity.title,
                        artist = entity.artistName,
                        artistId = entity.artistId,
                        album = entity.albumName,
                        albumId = entity.albumId,
                        path = entity.filePath,
                        contentUriString = entity.contentUriString,
                        albumArtUriString = entity.albumArtUriString,
                        duration = entity.duration,
                        mimeType = entity.mimeType,
                        bitrate = entity.bitrate,
                        sampleRate = entity.sampleRate
                    )
                }
            }
            ytSongs.forEach { yt ->
                val song = yt.toSong()
                songsMap[yt.id] = song
                songsMap[song.id] = song
            }
            offlineTracks.forEach { off ->
                if (!songsMap.containsKey(off.songId)) {
                    songsMap[off.songId] = Song(
                        id = off.songId,
                        title = off.title,
                        artist = off.provider,
                        artistId = 0L,
                        album = "Offline Downloads",
                        albumId = 0L,
                        path = off.localPath ?: "",
                        contentUriString = off.sourceUri,
                        albumArtUriString = null,
                        duration = 0L,
                        mimeType = off.mimeType,
                        bitrate = null,
                        sampleRate = null
                    )
                }
            }

            val totalPlays = allEngagements.sumOf { it.playCount }
            val totalCompletions = allEngagements.sumOf { it.completionCount }
            val totalSkips = allEngagements.sumOf { it.skipBefore30sCount }
            val totalRepeats = allEngagements.sumOf { it.sessionRepeatCount }

            val totalSessions = allEngagements.sumOf { maxOf(it.playCount, it.completionCount + it.skipBefore30sCount) }
            val denominator = totalSessions.coerceAtLeast(1)
            val completionRate = (totalCompletions.toDouble() / denominator) * 100.0
            val skipRate = (totalSkips.toDouble() / denominator) * 100.0

            val edgeCount = runCatching { itemCooccurrenceDao.getEdgeCount() }.getOrDefault(0)
            val topEdges = runCatching { itemCooccurrenceDao.getTopCooccurrences(15) }.getOrDefault(emptyList())
            val enrichedEdges = topEdges.map { edge ->
                EnrichedCooccurrence(
                    entity = edge,
                    songA = songsMap[edge.songIdA],
                    songB = songsMap[edge.songIdB]
                )
            }

            val tuned = adaptiveWeightTuner.computeTunedWeights(allEngagements)

            val topRaw = allEngagements.sortedByDescending { it.playCount + it.completionCount }.take(30)
            val enriched = topRaw.map { entity ->
                EnrichedEngagement(
                    entity = entity,
                    song = songsMap[entity.songId]
                )
            }

            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    totalSongsTracked = allEngagements.size,
                    totalPlays = totalPlays,
                    totalCompletions = totalCompletions,
                    totalSkips = totalSkips,
                    totalRepeats = totalRepeats,
                    totalSessions = totalSessions,
                    completionRatePct = completionRate.coerceIn(0.0, 100.0),
                    skipRatePct = skipRate.coerceIn(0.0, 100.0),
                    totalCooccurrenceEdges = edgeCount,
                    tunedWeights = tuned,
                    topEngagedSongs = enriched,
                    topCooccurrences = enrichedEdges,
                    allSongs = allSongs
                )
            }
        }
    }

    fun simulatePlay(songId: String) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                val now = System.currentTimeMillis()
                engagementDao.recordPlay(songId, 180000L, now)
            }
            loadStatsInternal(showLoading = false)
        }
    }

    fun simulateCompletion(songId: String) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                val now = System.currentTimeMillis()
                engagementDao.recordCompletion(songId, now)
            }
            loadStatsInternal(showLoading = false)
        }
    }

    fun simulateSkip(songId: String) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                val now = System.currentTimeMillis()
                engagementDao.recordSkip(songId, now)
            }
            loadStatsInternal(showLoading = false)
        }
    }

    fun simulateRepeat(songId: String) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                val now = System.currentTimeMillis()
                engagementDao.recordSessionRepeat(songId, "diag_session_${now}", now)
            }
            loadStatsInternal(showLoading = false)
        }
    }

    fun simulatePairwisePlay(songA: String, songB: String) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                val now = System.currentTimeMillis()
                val (k1, k2) = if (songA < songB) songA to songB else songB to songA
                itemCooccurrenceDao.incrementCooccurrence(k1, k2, now)
            }
            loadStatsInternal(showLoading = false)
        }
    }

    fun seedSampleTelemetry() {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                val now = System.currentTimeMillis()
                val availableSongs = _uiState.value.allSongs.take(5).ifEmpty {
                    listOf(
                        Song(id = "sample_1", title = "Midnight City", artist = "M83", artistId = 1L, album = "Hurry Up", albumId = 1L, path = "", contentUriString = "", albumArtUriString = null, duration = 240000L, mimeType = "audio/mp3", bitrate = 320000, sampleRate = 44100),
                        Song(id = "sample_2", title = "Starboy", artist = "The Weeknd", artistId = 2L, album = "Starboy", albumId = 2L, path = "", contentUriString = "", albumArtUriString = null, duration = 230000L, mimeType = "audio/mp3", bitrate = 320000, sampleRate = 44100),
                        Song(id = "sample_3", title = "Blinding Lights", artist = "The Weeknd", artistId = 2L, album = "After Hours", albumId = 3L, path = "", contentUriString = "", albumArtUriString = null, duration = 200000L, mimeType = "audio/mp3", bitrate = 320000, sampleRate = 44100),
                        Song(id = "sample_4", title = "Get Lucky", artist = "Daft Punk", artistId = 3L, album = "RAM", albumId = 4L, path = "", contentUriString = "", albumArtUriString = null, duration = 248000L, mimeType = "audio/mp3", bitrate = 320000, sampleRate = 44100),
                        Song(id = "sample_5", title = "Instant Crush", artist = "Daft Punk", artistId = 3L, album = "RAM", albumId = 4L, path = "", contentUriString = "", albumArtUriString = null, duration = 337000L, mimeType = "audio/mp3", bitrate = 320000, sampleRate = 44100)
                    )
                }

                availableSongs.forEachIndexed { index, song ->
                    val plays = (index + 1) * 3
                    val completions = (index + 1) * 2
                    val skips = if (index % 2 == 0) 1 else 0
                    val repeats = if (index > 2) 2 else 0
                    for (p in 0 until plays) engagementDao.recordPlay(song.id, 180000L, now - (p * 3600000L))
                    for (c in 0 until completions) engagementDao.recordCompletion(song.id, now - (c * 3600000L))
                    for (s in 0 until skips) engagementDao.recordSkip(song.id, now - (s * 3600000L))
                    for (r in 0 until repeats) engagementDao.recordSessionRepeat(song.id, "sample_session", now - (r * 3600000L))
                }

                for (i in 0 until availableSongs.size - 1) {
                    val a = availableSongs[i].id
                    val b = availableSongs[i + 1].id
                    val (k1, k2) = if (a < b) a to b else b to a
                    for (c in 0..3) {
                        itemCooccurrenceDao.incrementCooccurrence(k1, k2, now - (c * 1800000L))
                    }
                }
            }

            loadStatsInternal(showLoading = false)
            _uiState.update { it.copy(message = "Sample recommendation telemetry seeded successfully") }
        }
    }

    fun clearAllTelemetry() {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                engagementDao.clearAllEngagements()
                itemCooccurrenceDao.clearAll()
            }
            loadStatsInternal(showLoading = false)
            _uiState.update { it.copy(message = "All recommendation telemetry cleared") }
        }
    }

    fun triggerWorkerNow() {
        val request = OneTimeWorkRequestBuilder<RecommendationWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
        _uiState.update { it.copy(message = "RecommendationWorker triggered in background") }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
