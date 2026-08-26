package com.quietrays.tonarc.data.network.youtube

import com.quietrays.tonarc.data.database.YouTubeDao
import com.quietrays.tonarc.data.database.YouTubePlaylistEntity
import com.quietrays.tonarc.data.database.YouTubeSongEntity
import com.quietrays.tonarc.data.preferences.UserPreferencesRepository
import com.quietrays.tonarc.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sealed interface representing the state of YouTube Music library synchronization.
 */
sealed interface SyncState {
    data object Idle : SyncState
    data class Syncing(val message: String, val progress: Float = 0f) : SyncState
    data class Success(val likedCount: Int, val playlistCount: Int, val timestamp: Long = System.currentTimeMillis()) : SyncState
    data class Error(val message: String) : SyncState
}

/**
 * Summary of a completed YouTube Music library sync.
 */
data class SyncSummary(
    val likedCount: Int,
    val playlistCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Synchronization engine for fetching and caching YouTube Music liked songs and user playlists into Room SQLite.
 */
@Singleton
class YouTubeLibrarySyncEngine @Inject constructor(
    private val innertubeApiService: InnertubeApiService,
    private val youTubeDao: YouTubeDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    @AppScope private val scope: CoroutineScope
) {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val syncMutex = Mutex()

    /**
     * Synchronizes the user's YouTube Music library (Liked songs and user playlists) with the local Room database.
     *
     * @param force When true, forces sync even if already synced.
     * @return [Result] containing [SyncSummary] on success, or an exception on failure.
     */
    suspend fun syncLibrary(force: Boolean = false): Result<SyncSummary> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val cookies = innertubeApiService.authCookies
            if (cookies.isNullOrBlank()) {
                val errorMsg = "YouTube Music account not connected"
                _syncState.value = SyncState.Error(errorMsg)
                return@withContext Result.failure(IllegalStateException(errorMsg))
            }

            try {
                // 1. Fetch Liked Songs
                _syncState.value = SyncState.Syncing(message = "Fetching Liked Music...", progress = 0.1f)
                val likedSongs = mutableListOf<InnertubeTrack>()
                var likedContinuation: String? = null
                val visitedLikedContinuations = mutableSetOf<String>()

                do {
                    if (likedContinuation != null) {
                        visitedLikedContinuations.add(likedContinuation)
                    }
                    val (tracks, nextContinuation) = innertubeApiService.getLikedSongs(likedContinuation)
                    likedSongs.addAll(tracks)
                    likedContinuation = if (!nextContinuation.isNullOrBlank() && !visitedLikedContinuations.contains(nextContinuation)) {
                        nextContinuation
                    } else {
                        null
                    }
                } while (likedContinuation != null)

                // 2. Persist Liked Songs to YouTubeDao
                val now = System.currentTimeMillis()
                val likedSongEntities = likedSongs.map { track ->
                    val artistName = if (track.artists.isNotEmpty()) {
                        track.artists.joinToString(", ")
                    } else {
                        track.artist
                    }
                    YouTubeSongEntity(
                        id = "youtube_${track.videoId}",
                        videoId = track.videoId,
                        playlistId = "__liked_music__",
                        title = track.title,
                        artist = artistName,
                        album = track.album ?: "Liked Music",
                        duration = track.durationSeconds * 1000L,
                        thumbnailUrl = track.thumbnailUri,
                        year = 0,
                        dateAdded = now
                    )
                }

                youTubeDao.deleteSongsByPlaylist("__liked_music__")
                if (likedSongEntities.isNotEmpty()) {
                    youTubeDao.insertSongs(likedSongEntities)
                }

                val likedPlaylistEntity = YouTubePlaylistEntity(
                    id = "ytm_liked_music",
                    name = "Liked Music",
                    author = "YouTube Music",
                    songCount = likedSongs.size,
                    thumbnailUrl = likedSongs.firstOrNull()?.thumbnailUri,
                    dateAdded = now,
                    dateModified = now
                )
                youTubeDao.insertPlaylist(likedPlaylistEntity)

                // 3. Fetch User Playlists
                _syncState.value = SyncState.Syncing(message = "Fetching Playlists...", progress = 0.5f)
                val playlists = mutableListOf<InnertubePlaylist>()
                var playlistContinuation: String? = null
                val visitedPlaylistContinuations = mutableSetOf<String>()

                do {
                    if (playlistContinuation != null) {
                        visitedPlaylistContinuations.add(playlistContinuation)
                    }
                    val (items, nextContinuation) = innertubeApiService.getUserPlaylists(playlistContinuation)
                    playlists.addAll(items)
                    playlistContinuation = if (!nextContinuation.isNullOrBlank() && !visitedPlaylistContinuations.contains(nextContinuation)) {
                        nextContinuation
                    } else {
                        null
                    }
                } while (playlistContinuation != null)

                // 4. Persist Playlists to YouTubeDao
                val playlistEntities = playlists.map {
                    YouTubePlaylistEntity(
                        id = it.playlistId,
                        name = it.title,
                        author = it.author,
                        songCount = it.trackCount,
                        thumbnailUrl = it.thumbnailUri,
                        dateAdded = now,
                        dateModified = now
                    )
                }
                if (playlistEntities.isNotEmpty()) {
                    youTubeDao.insertPlaylists(playlistEntities)
                }

                // 5. Complete sync successfully
                val summary = SyncSummary(
                    likedCount = likedSongs.size,
                    playlistCount = playlists.size,
                    timestamp = now
                )
                _syncState.value = SyncState.Success(
                    likedCount = summary.likedCount,
                    playlistCount = summary.playlistCount,
                    timestamp = now
                )
                Result.success(summary)
            } catch (e: Exception) {
                val errorMsg = e.message ?: "YouTube library sync failed"
                Timber.tag(TAG).e(e, "YouTube library sync failed: $errorMsg")
                _syncState.value = SyncState.Error(errorMsg)
                Result.failure(e)
            }
        }
    }

    private companion object {
        private const val TAG = "YouTubeLibrarySyncEngine"
    }
}
