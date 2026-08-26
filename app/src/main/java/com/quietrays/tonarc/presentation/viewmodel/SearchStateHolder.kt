package com.quietrays.tonarc.presentation.viewmodel

import com.quietrays.tonarc.data.model.SearchFilterType
import com.quietrays.tonarc.data.model.SearchHistoryItem
import com.quietrays.tonarc.data.model.SearchResultItem
import com.quietrays.tonarc.data.repository.MusicRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.FlowPreview

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Manages search state and operations.
 * Extracted from PlayerViewModel to improve modularity.
 *
 * Responsibilities:
 * - Search query execution
 * - Search filter management
 * - Search history CRUD operations
 */
@Singleton
class SearchStateHolder @Inject constructor(
    private val musicRepository: MusicRepository,
    private val youTubeRepository: com.quietrays.tonarc.data.youtube.YouTubeRepository
) {
    private companion object {
        const val SEARCH_DEBOUNCE_MS = 150L
    }

    private data class SearchRequest(
        val query: String,
        val requestId: Long,
    )

    private val _searchResults = MutableStateFlow<ImmutableList<SearchResultItem>>(persistentListOf())
    val searchResults = _searchResults.asStateFlow()

    private val _selectedSearchFilter = MutableStateFlow(SearchFilterType.ALL)
    val selectedSearchFilter = _selectedSearchFilter.asStateFlow()

    private val _isSearchingOnline = MutableStateFlow(false)
    val isSearchingOnline = _isSearchingOnline.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _searchHistory = MutableStateFlow<ImmutableList<SearchHistoryItem>>(persistentListOf())
    val searchHistory = _searchHistory.asStateFlow()

    private val searchRequests = MutableSharedFlow<SearchRequest>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val latestSearchRequestId = AtomicLong(0L)
    private var currentContinuationToken: String? = null
    private var lastQuery: String = ""

    private var scope: CoroutineScope? = null
    private var searchJob: Job? = null

    /**
     * Initialize with ViewModel scope.
     */
    fun initialize(scope: CoroutineScope) {
        this.scope = scope
        observeSearchRequests()
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchRequests() {
        searchJob?.cancel()
        searchJob = scope?.launch {
            searchRequests
                .debounce(SEARCH_DEBOUNCE_MS)
                .collectLatest { request ->
                    executeSearchRequest(request)
                }
        }
    }

    private var activeSearchJob: Job? = null
    private var currentLocalResults: List<SearchResultItem> = emptyList()
    private var currentOnlineResults: List<SearchResultItem> = emptyList()

    private fun updateCombinedResults(requestId: Long) {
        if (requestId != latestSearchRequestId.get()) return
        val combined = (currentLocalResults + currentOnlineResults).distinctBy { it.dedupKey() }
        _searchResults.value = combined.toImmutableList()
    }

    private fun executeSearchRequest(request: SearchRequest) {
        activeSearchJob?.cancel()
        activeSearchJob = scope?.launch {
            val normalizedQuery = request.query
            lastQuery = normalizedQuery
            currentContinuationToken = null

            if (normalizedQuery.isBlank()) {
                currentLocalResults = emptyList()
                currentOnlineResults = emptyList()
                _searchResults.value = persistentListOf()
                _isSearchingOnline.value = false
                return@launch
            }

            val currentFilter = _selectedSearchFilter.value
            val requestId = request.requestId

            currentLocalResults = emptyList()
            currentOnlineResults = emptyList()

            // 1. Stage 1: Local Search Flow (FTS4 SQLite)
            launch {
                try {
                    musicRepository.searchAll(normalizedQuery, currentFilter).collect { localList ->
                        if (requestId != latestSearchRequestId.get()) return@collect
                        currentLocalResults = localList
                        updateCombinedResults(requestId)
                    }
                } catch (_: CancellationException) {
                } catch (e: Exception) {
                    Timber.tag("SearchStateHolder").e(e, "Local search error for: $normalizedQuery")
                }
            }

            // 2. Stage 2: Background Progressive Online Search (YouTube Music)
            launch {
                _isSearchingOnline.value = true
                try {
                    val ytResult = youTubeRepository.searchAllPaginated(normalizedQuery, currentFilter, null)
                    if (requestId != latestSearchRequestId.get()) return@launch

                    currentContinuationToken = ytResult.continuationToken
                    currentOnlineResults = ytResult.items
                    updateCombinedResults(requestId)
                } catch (_: CancellationException) {
                } catch (e: Exception) {
                    Timber.tag("SearchStateHolder").e(e, "Online search error for: $normalizedQuery")
                } finally {
                    if (requestId == latestSearchRequestId.get()) {
                        _isSearchingOnline.value = false
                    }
                }
            }
        }
    }

    private fun SearchResultItem.dedupKey(): String = when (this) {
        is SearchResultItem.SongItem -> "song_${song.id}_${song.title.lowercase().trim()}_${song.artist.lowercase().trim()}"
        is SearchResultItem.AlbumItem -> "album_${album.id}_${album.title.lowercase().trim()}"
        is SearchResultItem.ArtistItem -> "artist_${artist.id}_${artist.name.lowercase().trim()}"
        is SearchResultItem.PlaylistItem -> "playlist_${playlist.id}_${playlist.name.lowercase().trim()}"
    }

    fun loadMoreSearchResults() {
        val continuation = currentContinuationToken ?: return
        if (_isLoadingMore.value || lastQuery.isBlank()) return

        scope?.launch {
            _isLoadingMore.value = true
            try {
                val pageResult = youTubeRepository.searchAllPaginated(
                    query = lastQuery,
                    filterType = _selectedSearchFilter.value,
                    continuation = continuation
                )
                currentContinuationToken = pageResult.continuationToken
                if (pageResult.items.isNotEmpty()) {
                    val existingKeys = currentOnlineResults.map { it.dedupKey() }.toSet()
                    val newUniqueItems = pageResult.items.filter { it.dedupKey() !in existingKeys }

                    if (newUniqueItems.isNotEmpty()) {
                        currentOnlineResults = currentOnlineResults + newUniqueItems
                        updateCombinedResults(latestSearchRequestId.get())
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading more search results")
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun updateSearchFilter(filterType: SearchFilterType) {
        if (_selectedSearchFilter.value == filterType) return
        _selectedSearchFilter.value = filterType
        currentContinuationToken = null
        if (lastQuery.isNotBlank()) {
            val requestId = latestSearchRequestId.incrementAndGet()
            executeSearchRequest(SearchRequest(lastQuery, requestId))
        }
    }

    fun loadSearchHistory(limit: Int = 15) {
        scope?.launch {
            try {
                val history = musicRepository.getRecentSearchHistory(limit)
                _searchHistory.value = history.toImmutableList()
            } catch (e: Exception) {
                Timber.e(e, "Error loading search history")
            }
        }
    }

    fun onSearchQuerySubmitted(query: String) {
        scope?.launch {
            if (query.isNotBlank()) {
                try {
                    musicRepository.addSearchHistoryItem(query)
                    loadSearchHistory()
                } catch (e: Exception) {
                    Timber.e(e, "Error adding search history item")
                }
            }
        }
    }

    fun performSearch(query: String) {
        val normalizedQuery = query.trim()

        val requestId = latestSearchRequestId.incrementAndGet()

        if (normalizedQuery.isBlank()) {
            if (_searchResults.value.isNotEmpty()) {
                _searchResults.value = persistentListOf()
            }
        }

        searchRequests.tryEmit(SearchRequest(normalizedQuery, requestId))
    }

    fun deleteSearchHistoryItem(query: String) {
        scope?.launch {
            try {
                musicRepository.deleteSearchHistoryItemByQuery(query)
                loadSearchHistory()
            } catch (e: Exception) {
                Timber.e(e, "Error deleting search history item")
            }
        }
    }

    fun clearSearchHistory() {
        scope?.launch {
            try {
                musicRepository.clearSearchHistory()
                _searchHistory.value = persistentListOf()
            } catch (e: Exception) {
                Timber.e(e, "Error clearing search history")
            }
        }
    }

    fun onCleared() {
        searchJob?.cancel()
        activeSearchJob?.cancel()
        scope = null
    }
}
