package com.quietrays.tonarc.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietrays.tonarc.data.model.Album
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.repository.MusicRepository
import com.quietrays.tonarc.data.offline.CloudOfflineRepository
import com.quietrays.tonarc.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumDetailUiState(
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val cloudOfflineRepository: CloudOfflineRepository,
    private val youTubeRepository: com.quietrays.tonarc.data.youtube.YouTubeRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()
    val completedOfflineUris: StateFlow<Set<String>> = cloudOfflineRepository.observeCompleted()
        .map { downloads -> downloads.mapTo(mutableSetOf()) { it.sourceUri } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private var loadedAlbumId: Long? = null

    init {
        val albumIdString: String? = savedStateHandle.get("albumId")
        if (albumIdString != null) {
            val albumId = albumIdString.toLongOrNull()
            if (albumId != null) {
                loadedAlbumId = albumId
                loadAlbumData(albumId)
            } else {
                _uiState.update { it.copy(error = context.getString(R.string.invalid_album_id), isLoading = false) }
            }
        } else {
            _uiState.update { it.copy(error = context.getString(R.string.album_id_not_found), isLoading = false) }
        }
    }

    private fun loadAlbumData(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val albumDetailsFlow = musicRepository.getAlbumById(id)
                val albumSongsFlow = musicRepository.getSongsForAlbum(id)

                combine(albumDetailsFlow, albumSongsFlow) { album, songs ->
                    album to songs
                }
                    .catch { e ->
                        _uiState.update {
                            it.copy(
                                error = context.getString(R.string.error_loading_album, e.localizedMessage ?: ""),
                                isLoading = false
                            )
                        }
                    }
                    .collect { (album, songs) ->
                        if (album != null) {
                            _uiState.value = AlbumDetailUiState(
                                album = album,
                                songs = songs.sortedWith(
                                    compareBy<Song> { it.discNumber ?: 1 }
                                        .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                                        .thenBy { it.title.lowercase() }
                                ),
                                isLoading = false
                            )
                        } else {
                            val onlineResult = withContext(Dispatchers.IO) {
                                youTubeRepository.getAlbumDetails(id)
                            }
                            if (onlineResult != null) {
                                val (onlineAlbum, onlineSongs) = onlineResult
                                _uiState.value = AlbumDetailUiState(
                                    album = onlineAlbum,
                                    songs = onlineSongs,
                                    isLoading = false
                                )
                            } else {
                                _uiState.value = AlbumDetailUiState(
                                    error = context.getString(R.string.album_not_found),
                                    isLoading = false
                                )
                            }
                        }
                    }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = context.getString(R.string.error_loading_album, e.localizedMessage ?: ""),
                        isLoading = false
                    )
                }
            }
        }
    }

    /** Re-attempts loading the album after a failure (wired to the error-state retry button). */
    fun retry() {
        loadedAlbumId?.let { loadAlbumData(it) }
    }

    fun update(songs: List<Song>) {
        _uiState.update {
            it.copy(
                isLoading = false,
                songs = songs
            )
        }
    }

    fun downloadAlbum(songs: List<Song>) {
        viewModelScope.launch { cloudOfflineRepository.enqueueAll(songs) }
    }

    fun removeAlbumDownloads(songs: List<Song>) {
        viewModelScope.launch {
            songs.filter(CloudOfflineRepository::isCloudSong)
                .forEach { cloudOfflineRepository.remove(it) }
        }
    }
}
