package com.quietrays.tonarc.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.preferences.UserPreferencesRepository
import com.quietrays.tonarc.data.repository.ArtistImageRepository
import com.quietrays.tonarc.data.repository.MusicRepository
import com.quietrays.tonarc.data.youtube.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class FavoriteArtistSongsUiState(
    val artistName: String = "",
    val artistImageUrl: String? = null,
    val songs: List<Song> = emptyList(),
    val isFavorite: Boolean = false,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class FavoriteArtistSongsViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val youTubeRepository: YouTubeRepository,
    private val artistImageRepository: ArtistImageRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val rawArtistName: String = savedStateHandle.get<String>("artistName")?.let {
        runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it)
    } ?: ""

    private val _uiState = MutableStateFlow(FavoriteArtistSongsUiState(artistName = rawArtistName))
    val uiState: StateFlow<FavoriteArtistSongsUiState> = _uiState.asStateFlow()

    private var currentContinuationToken: String? = null
    private var isFetchingMore = false

    init {
        observeFavoriteStatus()
        loadArtistSongs(rawArtistName)
    }

    private fun observeFavoriteStatus() {
        userPreferencesRepository.favoriteArtistsFlow
            .onEach { favSet ->
                _uiState.update { it.copy(isFavorite = favSet.contains(rawArtistName)) }
            }
            .launchIn(viewModelScope)
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            if (rawArtistName.isNotBlank()) {
                userPreferencesRepository.toggleFavoriteArtist(rawArtistName)
            }
        }
    }

    fun loadArtistSongs(artistName: String, forceRefresh: Boolean = false) {
        if (artistName.isBlank()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Invalid artist name") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                // 1. Fetch artist image
                val artistImage: String? = runCatching {
                    artistImageRepository.getArtistImageUrl(artistName, 0L)
                }.getOrNull()

                // 2. Fetch local matching songs
                val localSongs = runCatching {
                    musicRepository.getAudioFiles().first()
                }.getOrDefault(emptyList()).filter { song ->
                    song.artist.equals(artistName, ignoreCase = true) ||
                        song.artists.any { it.name.equals(artistName, ignoreCase = true) } ||
                        song.artist.contains(artistName, ignoreCase = true)
                }

                // 3. Fetch multiple pages of online songs from YouTube Music (up to 60-80 songs initial batch)
                val onlineSongs = mutableListOf<Song>()
                var token: String? = null

                val firstPage = runCatching {
                    youTubeRepository.searchSongsPaginated(artistName)
                }.getOrNull()

                if (firstPage != null) {
                    onlineSongs.addAll(firstPage.songs)
                    token = firstPage.continuationToken
                }

                // Fetch up to 3 more consecutive pages for a rich full discography
                var pageCount = 1
                while (!token.isNullOrBlank() && pageCount < 4) {
                    val nextPage = runCatching {
                        youTubeRepository.searchSongsPaginated(artistName, continuation = token)
                    }.getOrNull()

                    if (nextPage != null && nextPage.songs.isNotEmpty()) {
                        onlineSongs.addAll(nextPage.songs)
                        token = nextPage.continuationToken
                        pageCount++
                    } else {
                        break
                    }
                }

                // If still fewer than 30 songs, search "$artistName songs" to supplement
                if (onlineSongs.size < 30) {
                    val fallbackSearch = runCatching {
                        youTubeRepository.searchSongsPaginated("$artistName songs")
                    }.getOrNull()
                    if (fallbackSearch != null) {
                        onlineSongs.addAll(fallbackSearch.songs)
                        if (token.isNullOrBlank()) {
                            token = fallbackSearch.continuationToken
                        }
                    }
                }

                currentContinuationToken = token

                // 4. Combine & deduplicate songs (local priority, followed by online)
                val allSongs = (localSongs + onlineSongs).distinctBy { it.id }

                val resolvedImage = artistImage
                    ?: allSongs.firstOrNull { !it.albumArtUriString.isNullOrBlank() }?.albumArtUriString

                _uiState.update {
                    it.copy(
                        artistName = artistName,
                        artistImageUrl = resolvedImage,
                        songs = allSongs,
                        isLoading = false,
                        hasMore = !currentContinuationToken.isNullOrBlank()
                    )
                }
            } catch (e: Exception) {
                Timber.tag("FavArtistSongsVM").e(e, "Failed to load songs for artist %s", artistName)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Failed to load artist songs"
                    )
                }
            }
        }
    }

    fun loadMore() {
        val token = currentContinuationToken
        if (token.isNullOrBlank() || isFetchingMore || _uiState.value.isLoading) return

        isFetchingMore = true
        _uiState.update { it.copy(isLoadingMore = true) }

        viewModelScope.launch {
            try {
                val nextPage = runCatching {
                    youTubeRepository.searchSongsPaginated(rawArtistName, continuation = token)
                }.getOrNull()

                if (nextPage != null) {
                    currentContinuationToken = nextPage.continuationToken
                    val currentList = _uiState.value.songs
                    val newCombined = (currentList + nextPage.songs).distinctBy { it.id }
                    _uiState.update {
                        it.copy(
                            songs = newCombined,
                            isLoadingMore = false,
                            hasMore = !currentContinuationToken.isNullOrBlank()
                        )
                    }
                } else {
                    currentContinuationToken = null
                    _uiState.update { it.copy(isLoadingMore = false, hasMore = false) }
                }
            } catch (e: Exception) {
                Timber.tag("FavArtistSongsVM").e(e, "Failed to load more songs")
                _uiState.update { it.copy(isLoadingMore = false) }
            } finally {
                isFetchingMore = false
            }
        }
    }
}
