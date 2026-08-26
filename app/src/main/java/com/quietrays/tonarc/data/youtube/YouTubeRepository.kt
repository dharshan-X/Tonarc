package com.quietrays.tonarc.data.youtube

import com.quietrays.tonarc.data.database.YouTubeDao
import com.quietrays.tonarc.data.database.YouTubePlaylistEntity
import com.quietrays.tonarc.data.database.YouTubeSongEntity
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.network.youtube.InnertubeAlbum
import com.quietrays.tonarc.data.network.youtube.InnertubeArtist
import com.quietrays.tonarc.data.network.youtube.InnertubeApiService
import com.quietrays.tonarc.data.network.youtube.InnertubeBrowseSection
import com.quietrays.tonarc.data.network.youtube.InnertubePlaylist
import com.quietrays.tonarc.data.network.youtube.InnertubeSearchResult
import com.quietrays.tonarc.data.network.youtube.InnertubeTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository orchestrating YouTube Music search, charts, streaming resolution,
 * and local library caching.
 */
@Singleton
class YouTubeRepository @Inject constructor(
    private val innertubeApiService: InnertubeApiService,
    private val youTubeExtractorManager: com.quietrays.tonarc.data.network.youtube.YouTubeExtractorManager,
    private val youTubeDao: YouTubeDao
) {
    private companion object {
        private const val TAG = "YouTubeRepository"
    }

    /**
     * Observes all locally cached / synced YouTube Music songs from the database.
     */
    val cachedSongsFlow: Flow<List<Song>> = youTubeDao.getAllYouTubeSongs().map { entities ->
        entities.map { it.toSong() }
    }.flowOn(Dispatchers.IO)

    /**
     * Observes all synced YouTube Music playlists from the database.
     */
    val playlistsFlow: Flow<List<YouTubePlaylistEntity>> = youTubeDao.getAllPlaylists()
        .flowOn(Dispatchers.IO)

    data class YouTubePageResult(
        val songs: List<Song>,
        val continuationToken: String?
    )

    data class YouTubeMultiPageResult(
        val items: List<com.quietrays.tonarc.data.model.SearchResultItem>,
        val continuationToken: String?
    )

    data class HomeRecommendations(
        val fromCommunity: List<Song> = emptyList(),
        val trendingCommunityPlaylists: List<com.quietrays.tonarc.data.model.Playlist> = emptyList(),
        val featuredPlaylists: List<com.quietrays.tonarc.data.model.Playlist> = emptyList(),
        val mixedForYou: List<com.quietrays.tonarc.data.model.Playlist> = emptyList(),
        val newAlbums: List<com.quietrays.tonarc.data.model.Album> = emptyList(),
        val quickPicks: List<Song> = emptyList()
    )

    /**
     * Searches YouTube Music for songs matching the query with continuation support.
     */
    suspend fun searchSongsPaginated(query: String, continuation: String? = null): YouTubePageResult = withContext(Dispatchers.IO) {
        if (query.isBlank() && continuation.isNullOrBlank()) {
            return@withContext YouTubePageResult(emptyList(), null)
        }
        val result = innertubeApiService.search(query, InnertubeApiService.YTM_FILTER_SONGS, continuation)
        val songs = result.songs.map { it.toDomainSong() }
        YouTubePageResult(songs, result.continuationToken)
    }

    /**
     * Searches YouTube Music across multi-category filters (Songs, Albums, Artists, Playlists).
     */
    suspend fun searchAllPaginated(
        query: String,
        filterType: com.quietrays.tonarc.data.model.SearchFilterType = com.quietrays.tonarc.data.model.SearchFilterType.ALL,
        continuation: String? = null
    ): YouTubeMultiPageResult = withContext(Dispatchers.IO) {
        if (query.isBlank() && continuation.isNullOrBlank()) {
            return@withContext YouTubeMultiPageResult(emptyList(), null)
        }
        val (effectiveParams, effectiveContinuation) = when {
            continuation == "ALL_FALLBACK_SONGS" -> Pair(InnertubeApiService.YTM_FILTER_SONGS, null)
            else -> {
                val p = when (filterType) {
                    com.quietrays.tonarc.data.model.SearchFilterType.ALL -> null
                    com.quietrays.tonarc.data.model.SearchFilterType.SONGS -> InnertubeApiService.YTM_FILTER_SONGS
                    com.quietrays.tonarc.data.model.SearchFilterType.ALBUMS -> InnertubeApiService.YTM_FILTER_ALBUMS
                    com.quietrays.tonarc.data.model.SearchFilterType.ARTISTS -> InnertubeApiService.YTM_FILTER_ARTISTS
                    com.quietrays.tonarc.data.model.SearchFilterType.PLAYLISTS -> InnertubeApiService.YTM_FILTER_PLAYLISTS
                }
                Pair(p, continuation)
            }
        }
        val result = innertubeApiService.search(query, effectiveParams, effectiveContinuation)
        val items = mutableListOf<com.quietrays.tonarc.data.model.SearchResultItem>()
        result.songs.forEach { items.add(com.quietrays.tonarc.data.model.SearchResultItem.SongItem(it.toDomainSong())) }
        result.albums.forEach { items.add(com.quietrays.tonarc.data.model.SearchResultItem.AlbumItem(it.toDomainAlbum())) }
        result.artists.forEach { items.add(com.quietrays.tonarc.data.model.SearchResultItem.ArtistItem(it.toDomainArtist())) }
        result.playlists.forEach { items.add(com.quietrays.tonarc.data.model.SearchResultItem.PlaylistItem(it.toDomainPlaylist())) }

        val nextToken = result.continuationToken ?: if (filterType == com.quietrays.tonarc.data.model.SearchFilterType.ALL && continuation == null) "ALL_FALLBACK_SONGS" else null

        YouTubeMultiPageResult(items, nextToken)
    }

    /**
     * Fetches categorized recommendations from YouTube Music browse feed.
     */
    suspend fun getHomeRecommendations(): HomeRecommendations = withContext(Dispatchers.IO) {
        try {
            val homeDeferred = async { innertubeApiService.getBrowse("FEmusic_home") }
            val exploreDeferred = async { innertubeApiService.getBrowse("FEmusic_explore") }

            val homeSections = homeDeferred.await()
            val exploreSections = exploreDeferred.await()
            val sections = homeSections + exploreSections

            val communitySongs = mutableListOf<Song>()
            val trendingPlaylists = mutableListOf<com.quietrays.tonarc.data.model.Playlist>()
            val featuredPlaylists = mutableListOf<com.quietrays.tonarc.data.model.Playlist>()
            val mixedPlaylists = mutableListOf<com.quietrays.tonarc.data.model.Playlist>()
            val newAlbums = mutableListOf<com.quietrays.tonarc.data.model.Album>()
            val quickPicks = mutableListOf<Song>()

            for (section in sections) {
                val titleLower = section.title.lowercase()
                val subtitleLower = section.subtitle?.lowercase() ?: ""

                section.albums.forEach { newAlbums.add(it.toDomainAlbum()) }

                when {
                    titleLower.contains("quick pick") || titleLower.contains("start radio") || titleLower.contains("listen again") -> {
                        quickPicks.addAll(section.tracks.map { it.toDomainSong() })
                    }
                    titleLower.contains("mix") || titleLower.contains("for you") || subtitleLower.contains("mix") -> {
                        mixedPlaylists.addAll(section.playlists.map { it.toDomainPlaylist() })
                    }
                    titleLower.contains("trending") || titleLower.contains("community") || titleLower.contains("popular") -> {
                        trendingPlaylists.addAll(section.playlists.map { it.toDomainPlaylist() })
                        communitySongs.addAll(section.tracks.map { it.toDomainSong() })
                    }
                    titleLower.contains("featured") || titleLower.contains("today") || titleLower.contains("charts") || titleLower.contains("hits") || titleLower.contains("biggest hits") -> {
                        featuredPlaylists.addAll(section.playlists.map { it.toDomainPlaylist() })
                        communitySongs.addAll(section.tracks.map { it.toDomainSong() })
                    }
                    titleLower.contains("new") || titleLower.contains("single") || titleLower.contains("release") -> {
                        communitySongs.addAll(section.tracks.map { it.toDomainSong() })
                    }
                    else -> {
                        if (mixedPlaylists.size < 10 && section.playlists.isNotEmpty()) {
                            mixedPlaylists.addAll(section.playlists.map { it.toDomainPlaylist() })
                        } else if (trendingPlaylists.size < 10 && section.playlists.isNotEmpty()) {
                            trendingPlaylists.addAll(section.playlists.map { it.toDomainPlaylist() })
                        } else {
                            featuredPlaylists.addAll(section.playlists.map { it.toDomainPlaylist() })
                        }
                        if (communitySongs.size < 30) {
                            communitySongs.addAll(section.tracks.map { it.toDomainSong() })
                        }
                    }
                }
            }

            val finalQuickPicks = if (quickPicks.isNotEmpty()) quickPicks else communitySongs.take(10)

            HomeRecommendations(
                fromCommunity = communitySongs.distinctBy { it.id },
                trendingCommunityPlaylists = trendingPlaylists.distinctBy { it.id },
                featuredPlaylists = featuredPlaylists.distinctBy { it.id },
                mixedForYou = mixedPlaylists.distinctBy { it.id },
                newAlbums = newAlbums.distinctBy { it.id },
                quickPicks = finalQuickPicks.distinctBy { it.id }
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load home recommendations")
            HomeRecommendations()
        }
    }

    /**
     * Fetches details and songs for an online YouTube playlist.
     */
    suspend fun getPlaylist(playlistId: String): Pair<com.quietrays.tonarc.data.model.Playlist, List<Song>>? = withContext(Dispatchers.IO) {
        try {
            val result = innertubeApiService.getPlaylist(playlistId) ?: return@withContext null
            val (innertubePlaylist, innertubeTracks) = result
            val songs = innertubeTracks.map { it.toDomainSong() }
            val playlist = innertubePlaylist.toDomainPlaylist().copy(
                songIds = songs.map { it.id }
            )
            songs.forEach { song ->
                try {
                    saveTrackToLibrary(song)
                } catch (_: Exception) {}
            }
            Pair(playlist, songs)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error loading playlist: $playlistId")
            null
        }
    }

    /**
     * Searches YouTube Music for songs matching the query.
     */
    fun searchSongs(query: String): Flow<List<Song>> = flow {
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val result = innertubeApiService.search(query)
        val songs = result.songs.map { it.toDomainSong() }
        emit(songs)
    }.flowOn(Dispatchers.IO)

    /**
     * Fetches top charts / trending music tracks.
     */
    fun getCharts(): Flow<List<Song>> = flow {
        val sections = innertubeApiService.getBrowse("FEmusic_charts")
        val tracks = sections.flatMap { it.tracks }.map { it.toDomainSong() }
        emit(tracks)
    }.flowOn(Dispatchers.IO)

    /**
     * Fetches browse sections for the Explore/Discover screen.
     */
    fun getExploreSections(): Flow<List<InnertubeBrowseSection>> = flow {
        val sections = innertubeApiService.getBrowse("FEmusic_home")
        emit(sections)
    }.flowOn(Dispatchers.IO)

    /**
     * Resolves the direct audio stream URL for a given YouTube video ID.
     */
    suspend fun getStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val extractedUrl = runCatching { youTubeExtractorManager.extractAudioStreamUrl(videoId) }.getOrNull()
        if (!extractedUrl.isNullOrBlank()) {
            return@withContext extractedUrl
        }
        val streamInfo = runCatching { innertubeApiService.getStreamInfo(videoId) }.getOrNull()
        streamInfo?.selectedFormatUrl ?: streamInfo?.highestBitrateOpusUrl ?: streamInfo?.highestBitrateAacUrl
    }

    /**
     * Fetches timestamped lyrics for a track if available.
     */
    suspend fun getLyrics(videoId: String): String? = withContext(Dispatchers.IO) {
        innertubeApiService.getTranscriptLyrics(videoId)
    }

    /**
     * Fetches radio / autoplay recommended tracks for a given YouTube video ID.
     */
    suspend fun getRadioTracks(videoId: String): List<Song> = withContext(Dispatchers.IO) {
        val tracks = innertubeApiService.getRadioTracks(videoId)
        tracks.map { it.toDomainSong() }
    }

    /**
     * Fetches radio / autoplay tracks for any song (local or online).
     */
    suspend fun getRadioTracksForSong(song: Song): List<Song> = withContext(Dispatchers.IO) {
        val videoId = song.youtubeId
            ?: song.contentUriString.takeIf { it.startsWith("youtube://") }?.removePrefix("youtube://")
            ?: run {
                val searchResult = innertubeApiService.search("${song.title} ${song.artist}")
                searchResult.songs.firstOrNull()?.videoId
            }

        if (videoId.isNullOrBlank()) return@withContext emptyList()
        getRadioTracks(videoId)
    }

    /**
     * Saves a YouTube Music track to the local database library.
     */
    suspend fun saveTrackToLibrary(song: Song) = withContext(Dispatchers.IO) {
        saveSong(song, playlistId = "__library__")
    }

    suspend fun saveSong(song: Song, playlistId: String = "__library__") = withContext(Dispatchers.IO) {
        val videoId = song.youtubeId ?: song.contentUriString.removePrefix("youtube://")
        val entity = YouTubeSongEntity(
            id = song.id,
            videoId = videoId,
            playlistId = playlistId,
            title = song.title,
            artist = song.artist,
            album = song.album,
            duration = song.duration,
            thumbnailUrl = song.albumArtUriString,
            year = song.year,
            dateAdded = System.currentTimeMillis()
        )
        youTubeDao.insertSong(entity)
    }

    /**
     * Removes a track from the local cached YouTube library.
     */
    suspend fun removeTrackFromLibrary(songId: String) = withContext(Dispatchers.IO) {
        deleteSong(songId)
    }

    suspend fun deleteSong(songId: String) = withContext(Dispatchers.IO) {
        youTubeDao.deleteSong(songId)
    }

    private fun InnertubeTrack.toDomainSong(): Song {
        val artistList = if (artists.isNotEmpty()) artists else listOf(artist)
        val artistRefs = artistList.mapIndexed { index, name ->
            com.quietrays.tonarc.data.model.ArtistRef(
                id = if (index == 0) -Math.abs(name.hashCode().toLong().takeIf { it != 0L } ?: 1L)
                else (name.hashCode().toLong() * -1L) - 10_000L,
                name = name,
                isPrimary = index == 0
            )
        }
        val calculatedArtistId = artistRefs.firstOrNull()?.id ?: 0L
        return Song(
            id = "youtube_$videoId",
            title = title,
            artist = artist,
            artistId = calculatedArtistId,
            artists = artistRefs,
            album = album ?: "YouTube Music",
            albumId = 0L,
            albumArtist = artist,
            path = "youtube://$videoId",
            contentUriString = "youtube://$videoId",
            albumArtUriString = thumbnailUri,
            duration = durationSeconds * 1000L,
            mimeType = "audio/webm",
            bitrate = 160000,
            sampleRate = 48000,
            youtubeId = videoId
        )
    }

    /**
     * Fetches details and songs for an online YouTube album.
     */
    suspend fun getAlbumDetails(albumId: Long): Pair<com.quietrays.tonarc.data.model.Album, List<Song>>? = withContext(Dispatchers.IO) {
        val cached = onlineAlbumsCache[albumId] ?: return@withContext null
        try {
            val result = innertubeApiService.getAlbum(cached.browseId)
            val songs = if (result != null && result.second.isNotEmpty()) {
                result.second.map { it.toDomainSong() }
            } else {
                val searchResult = innertubeApiService.search("${cached.title} ${cached.artist}", InnertubeApiService.YTM_FILTER_SONGS)
                searchResult.songs.map { it.toDomainSong() }
            }
            songs.forEach { song ->
                try { saveTrackToLibrary(song) } catch (_: Exception) {}
            }
            Pair(cached.toDomainAlbum().copy(songCount = songs.size), songs)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error loading album details: $albumId")
            null
        }
    }

    /**
     * Fetches details and top songs for an online YouTube artist.
     */
    suspend fun getArtistDetails(artistId: Long): Pair<com.quietrays.tonarc.data.model.Artist, List<Song>>? = withContext(Dispatchers.IO) {
        val cached = onlineArtistsCache[artistId] ?: return@withContext null
        try {
            val result = innertubeApiService.getArtist(cached.browseId)
            val songs = if (result != null && result.second.isNotEmpty()) {
                result.second.map { it.toDomainSong() }
            } else {
                val searchResult = innertubeApiService.search(cached.name, InnertubeApiService.YTM_FILTER_SONGS)
                searchResult.songs.map { it.toDomainSong() }
            }
            songs.forEach { song ->
                try { saveTrackToLibrary(song) } catch (_: Exception) {}
            }
            Pair(cached.toDomainArtist().copy(songCount = songs.size), songs)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error loading artist details: $artistId")
            null
        }
    }

    private val onlineArtistsCache = java.util.concurrent.ConcurrentHashMap<Long, InnertubeArtist>()
    private val onlineAlbumsCache = java.util.concurrent.ConcurrentHashMap<Long, InnertubeAlbum>()

    private fun InnertubeAlbum.toDomainAlbum(): com.quietrays.tonarc.data.model.Album {
        val calculatedId = -Math.abs(browseId.hashCode().toLong().takeIf { it != 0L } ?: 1L)
        onlineAlbumsCache[calculatedId] = this
        return com.quietrays.tonarc.data.model.Album(
            id = calculatedId,
            title = title,
            artist = artist,
            year = year ?: 0,
            dateAdded = System.currentTimeMillis(),
            albumArtUriString = thumbnailUri,
            songCount = trackCount,
            albumArtist = artist
        )
    }

    private fun InnertubeArtist.toDomainArtist(): com.quietrays.tonarc.data.model.Artist {
        val calculatedId = -Math.abs(browseId.hashCode().toLong().takeIf { it != 0L } ?: 1L)
        onlineArtistsCache[calculatedId] = this
        return com.quietrays.tonarc.data.model.Artist(
            id = calculatedId,
            name = name,
            songCount = 0,
            imageUrl = thumbnailUri,
            customImageUri = null
        )
    }

    private fun InnertubePlaylist.toDomainPlaylist(): com.quietrays.tonarc.data.model.Playlist {
        return com.quietrays.tonarc.data.model.Playlist(
            id = playlistId,
            name = title,
            songIds = emptyList(),
            coverImageUri = thumbnailUri,
            source = "YOUTUBE"
        )
    }
}
