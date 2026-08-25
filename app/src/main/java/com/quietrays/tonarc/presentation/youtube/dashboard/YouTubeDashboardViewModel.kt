package com.quietrays.tonarc.presentation.youtube.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietrays.tonarc.data.database.EngagementDao
import com.quietrays.tonarc.data.database.SongEngagementEntity
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.network.youtube.InnertubeBrowseSection
import com.quietrays.tonarc.data.recommendation.AdaptiveWeightTuner
import com.quietrays.tonarc.data.recommendation.CandidateAggregator
import com.quietrays.tonarc.data.recommendation.PersonalizedRanker
import com.quietrays.tonarc.data.recommendation.RecommendationCandidate
import com.quietrays.tonarc.data.repository.MusicRepository
import com.quietrays.tonarc.data.youtube.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

sealed interface YouTubeDashboardUiState {
    data object Loading : YouTubeDashboardUiState
    data class Success(
        val forYou: List<Song>,
        val charts: List<Song>,
        val sections: List<InnertubeBrowseSection>,
        val selectedMood: PersonalizedRanker.RecommendationMood = PersonalizedRanker.RecommendationMood.ALL
    ) : YouTubeDashboardUiState
    data class Error(val message: String) : YouTubeDashboardUiState
}

@HiltViewModel
class YouTubeDashboardViewModel @Inject constructor(
    private val youTubeRepository: YouTubeRepository,
    private val candidateAggregator: CandidateAggregator,
    private val personalizedRanker: PersonalizedRanker,
    private val adaptiveWeightTuner: AdaptiveWeightTuner,
    private val engagementDao: EngagementDao,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<YouTubeDashboardUiState>(YouTubeDashboardUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _selectedMood = MutableStateFlow(PersonalizedRanker.RecommendationMood.ALL)
    val selectedMood = _selectedMood.asStateFlow()

    private var cachedCandidates: List<RecommendationCandidate> = emptyList()
    private var cachedEngagements: Map<String, SongEngagementEntity> = emptyMap()
    private var cachedTunedWeights: PersonalizedRanker.RankingWeights = PersonalizedRanker.RankingWeights()
    private var cachedCharts: List<Song> = emptyList()
    private var cachedSections: List<InnertubeBrowseSection> = emptyList()

    init {
        loadDashboard()
    }

    fun selectMood(mood: PersonalizedRanker.RecommendationMood) {
        _selectedMood.value = mood
        val currentState = _uiState.value
        if (currentState is YouTubeDashboardUiState.Success) {
            val forYou = computeForYou(mood)
            _uiState.value = currentState.copy(
                forYou = forYou,
                selectedMood = mood
            )
        }
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.value = YouTubeDashboardUiState.Loading
            try {
                youTubeRepository.getExploreSections()
                    .catch { e ->
                        Timber.e(e, "Failed to load explore sections")
                        _uiState.value = YouTubeDashboardUiState.Error(e.message ?: "Failed to load Explore")
                    }
                    .collect { sections ->
                        cachedSections = sections
                        cachedCharts = sections.flatMap { it.tracks }.map { track ->
                            val syntheticArtistId = if (track.artist.isNotBlank()) {
                                -Math.abs(track.artist.hashCode().toLong().takeIf { it != 0L } ?: 1L)
                            } else 0L
                            Song(
                                id = "youtube_${track.videoId}",
                                title = track.title,
                                artist = track.artist,
                                artistId = syntheticArtistId,
                                album = track.album ?: "YouTube Music",
                                albumId = 0L,
                                albumArtist = track.artist,
                                path = "youtube://${track.videoId}",
                                contentUriString = "youtube://${track.videoId}",
                                albumArtUriString = track.thumbnailUri,
                                duration = track.durationSeconds * 1000L,
                                mimeType = "audio/webm",
                                bitrate = 160000,
                                sampleRate = 48000,
                                youtubeId = track.videoId
                            )
                        }

                        withContext(Dispatchers.IO) {
                            runCatching {
                                val allEngagements = engagementDao.getAllEngagements()
                                cachedEngagements = allEngagements.associateBy { it.songId }
                                cachedTunedWeights = adaptiveWeightTuner.computeTunedWeights(allEngagements)

                                val topSongs = engagementDao.getTopPlayedSongs(10)
                                val recentSongs = engagementDao.getRecentlyPlayedSongs(20)
                                val recentPlayedIds = recentSongs.map { it.songId }.toSet()
                                val seedIds = (topSongs + recentSongs.take(5)).map { it.songId }.toSet()
                                val allAudioSongs = runCatching { musicRepository.getAllSongsOnce() }.getOrDefault(emptyList())
                                val localSeedSongs = allAudioSongs.filter { it.id in seedIds }
                                val chartSeeds = cachedCharts.filter { it.id in seedIds }
                                val seedSongs = (localSeedSongs + chartSeeds).distinctBy { it.id }

                                cachedCandidates = candidateAggregator.collect(seedSongs, limit = 60, excludedSongIds = recentPlayedIds)
                            }.onFailure {
                                Timber.tag("YouTubeDashboardVM").e(it, "Failed to compute recommendation candidates")
                            }
                        }

                        val forYou = computeForYou(_selectedMood.value)

                        _uiState.value = YouTubeDashboardUiState.Success(
                            forYou = forYou,
                            charts = cachedCharts,
                            sections = sections,
                            selectedMood = _selectedMood.value
                        )
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error initializing explore dashboard")
                _uiState.value = YouTubeDashboardUiState.Error(e.message ?: "Network error")
            }
        }
    }

    private fun computeForYou(mood: PersonalizedRanker.RecommendationMood): List<Song> {
        if (cachedCandidates.isEmpty()) {
            return cachedCharts.take(20)
        }

        val ranked = personalizedRanker.rank(
            candidates = cachedCandidates,
            engagements = cachedEngagements,
            favoriteSongIds = emptySet(),
            weights = cachedTunedWeights,
            mood = mood
        )
        val selected = personalizedRanker.pickWithDiversity(ranked, emptySet(), limit = 20)
        return if (selected.isNotEmpty()) selected else cachedCharts.take(20)
    }
}
