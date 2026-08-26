package com.quietrays.tonarc.data.recommendation

import com.quietrays.tonarc.data.model.ArtistRef
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.network.youtube.InnertubeApiService
import com.quietrays.tonarc.data.network.youtube.InnertubeTrack
import com.quietrays.tonarc.data.repository.MusicRepository
import com.quietrays.tonarc.data.repository.SmartPlaylistGenerator
import com.quietrays.tonarc.data.youtube.YouTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of radio station generation.
 */
data class RadioResult(
    val seed: Song,
    val tracks: List<Song>,
    val continuationToken: String? = null,
    val radioTitle: String
)

/**
 * Hybrid radio synthesis engine blending Innertube cloud discovery with local library affinity.
 */
@Singleton
class SmartRadioEngine @Inject constructor(
    private val innertubeApiService: InnertubeApiService,
    private val youTubeRepository: YouTubeRepository,
    private val smartPlaylistGenerator: SmartPlaylistGenerator,
    private val candidateAggregator: CandidateAggregator,
    private val musicRepository: MusicRepository
) {

    companion object {
        private const val TAG = "SmartRadioEngine"
        private const val LOCAL_FAVORITES_SAMPLE_LIMIT = 10
        // Interleaving schedule: 7 discovery, 3 local in a 10-step block (~70% discovery, 30% local)
        private val INTERLEAVE_PATTERN = listOf(true, true, false, true, true, false, true, true, true, false)
    }

    /**
     * Generates an infinite radio tracklist seeded by a specific song.
     */
    suspend fun generateRadioForSong(seedSong: Song, initialLimit: Int = 25): RadioResult = withContext(Dispatchers.IO) {
        val radioTitle = if (seedSong.title.isNotBlank()) "${seedSong.title} Radio" else "Radio"

        if (initialLimit <= 0) {
            return@withContext RadioResult(
                seed = seedSong,
                tracks = emptyList(),
                continuationToken = null,
                radioTitle = radioTitle
            )
        }

        val seedVideoId = resolveSeedVideoId(seedSong)

        if (!seedVideoId.isNullOrBlank()) {
            val discoveryTracks = runCatching {
                innertubeApiService.getRadioTracks(seedVideoId).map { it.toDomainSong() }
            }.onFailure {
                Timber.tag(TAG).w(it, "Failed to fetch Innertube radio tracks for videoId: $seedVideoId")
            }.getOrDefault(emptyList())

            if (discoveryTracks.isNotEmpty()) {
                val localTracks = runCatching {
                    smartPlaylistGenerator.getSmartQueueForSong(seedSong, limit = LOCAL_FAVORITES_SAMPLE_LIMIT)
                }.onFailure {
                    Timber.tag(TAG).w(it, "Failed to fetch local smart queue for seed: ${seedSong.id}")
                }.getOrDefault(emptyList())

                val combined = interleaveTracks(
                    discovery = discoveryTracks,
                    local = localTracks,
                    seedSong = seedSong,
                    limit = initialLimit
                )

                if (combined.isNotEmpty()) {
                    return@withContext RadioResult(
                        seed = seedSong,
                        tracks = combined,
                        continuationToken = null,
                        radioTitle = radioTitle
                    )
                }
            }
        }

        // Graceful offline / fallback strategy
        Timber.tag(TAG).d("Falling back to local smart playlist generator for seed: ${seedSong.title}")
        val fallbackTracks = runCatching {
            smartPlaylistGenerator.getSmartQueueForSong(seedSong, initialLimit)
        }.getOrDefault(emptyList())

        val deduplicatedFallback = filterAndDeduplicate(fallbackTracks, seedSong).take(initialLimit)

        RadioResult(
            seed = seedSong,
            tracks = deduplicatedFallback,
            continuationToken = null,
            radioTitle = radioTitle
        )
    }

    /**
     * Generates a radio station seeded by artist name.
     */
    suspend fun generateRadioForArtist(artistName: String, initialLimit: Int = 25): RadioResult = withContext(Dispatchers.IO) {
        val cleanArtist = artistName.trim().ifBlank { "Artist" }
        val radioTitle = "$cleanArtist Radio"

        if (initialLimit <= 0) {
            val emptySeed = Song.emptySong().copy(
                id = "artist_${cleanArtist.hashCode()}",
                title = cleanArtist,
                artist = cleanArtist
            )
            return@withContext RadioResult(
                seed = emptySeed,
                tracks = emptyList(),
                continuationToken = null,
                radioTitle = radioTitle
            )
        }

        // 1. Try finding top song via Innertube search
        val onlineTopSong = runCatching {
            val searchResult = innertubeApiService.search(cleanArtist, InnertubeApiService.YTM_FILTER_SONGS)
            searchResult.songs.firstOrNull()?.toDomainSong()
        }.onFailure {
            Timber.tag(TAG).w(it, "Failed to search top song for artist: $cleanArtist")
        }.getOrNull()

        if (onlineTopSong != null) {
            val result = generateRadioForSong(onlineTopSong, initialLimit)
            return@withContext result.copy(radioTitle = radioTitle)
        }

        // 2. Offline / local fallback by artist
        val allLocalSongs = runCatching { musicRepository.getAudioFiles().first() }.getOrDefault(emptyList())
        val artistSongs = allLocalSongs.filter { song ->
            song.artist.contains(cleanArtist, ignoreCase = true) ||
                song.artists.any { it.name.contains(cleanArtist, ignoreCase = true) }
        }

        val localSeed = artistSongs.firstOrNull()
        if (localSeed != null) {
            val localTracks = runCatching {
                smartPlaylistGenerator.getSmartQueueForSong(localSeed, initialLimit)
            }.getOrDefault(emptyList())

            val combined = filterAndDeduplicate(artistSongs + localTracks, localSeed).take(initialLimit)
            return@withContext RadioResult(
                seed = localSeed,
                tracks = combined,
                continuationToken = null,
                radioTitle = radioTitle
            )
        }

        val fallbackSeed = Song.emptySong().copy(
            id = "artist_${cleanArtist.hashCode()}",
            title = cleanArtist,
            artist = cleanArtist
        )
        val fallbackTracks = runCatching {
            smartPlaylistGenerator.getSmartQueueForSong(fallbackSeed, initialLimit)
        }.getOrDefault(emptyList())

        RadioResult(
            seed = fallbackSeed,
            tracks = filterAndDeduplicate(fallbackTracks, fallbackSeed).take(initialLimit),
            continuationToken = null,
            radioTitle = radioTitle
        )
    }

    /**
     * Fetches the next paginated batch of radio tracks using a continuation token.
     */
    suspend fun fetchNextBatch(
        seedVideoId: String,
        continuationToken: String,
        limit: Int = 15
    ): List<Song> = withContext(Dispatchers.IO) {
        if (seedVideoId.isBlank() && continuationToken.isBlank()) {
            return@withContext emptyList()
        }
        if (limit <= 0) {
            return@withContext emptyList()
        }
        val tracks = runCatching {
            innertubeApiService.getRadioTracks(seedVideoId, continuationToken)
        }.onFailure {
            Timber.tag(TAG).w(it, "Failed to fetch next radio batch with continuation: $continuationToken")
        }.getOrDefault(emptyList())

        tracks.map { it.toDomainSong() }.take(limit)
    }

    /**
     * Resolves the YouTube videoId for a given song.
     */
    private suspend fun resolveSeedVideoId(seedSong: Song): String? {
        // 1. Direct explicit youtubeId
        if (!seedSong.youtubeId.isNullOrBlank()) {
            return seedSong.youtubeId
        }

        // 2. URI scheme check
        if (seedSong.contentUriString.startsWith("youtube://")) {
            return seedSong.contentUriString.removePrefix("youtube://").takeIf { it.isNotBlank() }
        }

        // 3. ID prefix check
        if (seedSong.id.startsWith("youtube_")) {
            return seedSong.id.removePrefix("youtube_").takeIf { it.isNotBlank() }
        }

        // 4. Online search resolution
        val query = "${seedSong.title} ${seedSong.artist}".trim()
        if (query.isNotBlank()) {
            val searchResult = runCatching {
                innertubeApiService.search(query, InnertubeApiService.YTM_FILTER_SONGS)
            }.getOrNull() ?: runCatching {
                innertubeApiService.search(query)
            }.getOrNull()

            val foundVideoId = searchResult?.songs?.firstOrNull()?.videoId
            if (!foundVideoId.isNullOrBlank()) {
                return foundVideoId
            }
        }

        return null
    }

    /**
     * Interleaves discovery and local tracks (~70% discovery, 30% local) while deduplicating
     * and omitting seed track instances.
     */
    internal fun interleaveTracks(
        discovery: List<Song>,
        local: List<Song>,
        seedSong: Song,
        limit: Int
    ): List<Song> {
        val result = mutableListOf<Song>()
        val seenKeys = mutableSetOf<String>()
        seenKeys.add(songDeduplicationKey(seedSong))

        var dIdx = 0
        var lIdx = 0
        var step = 0

        while (result.size < limit && (dIdx < discovery.size || lIdx < local.size)) {
            val preferDiscovery = INTERLEAVE_PATTERN[step % INTERLEAVE_PATTERN.size]
            step++

            if (preferDiscovery) {
                if (dIdx < discovery.size) {
                    val candidate = discovery[dIdx++]
                    if (!isSameSong(candidate, seedSong) && seenKeys.add(songDeduplicationKey(candidate))) {
                        result.add(candidate)
                    }
                } else if (lIdx < local.size) {
                    val candidate = local[lIdx++]
                    if (!isSameSong(candidate, seedSong) && seenKeys.add(songDeduplicationKey(candidate))) {
                        result.add(candidate)
                    }
                }
            } else {
                if (lIdx < local.size) {
                    val candidate = local[lIdx++]
                    if (!isSameSong(candidate, seedSong) && seenKeys.add(songDeduplicationKey(candidate))) {
                        result.add(candidate)
                    }
                } else if (dIdx < discovery.size) {
                    val candidate = discovery[dIdx++]
                    if (!isSameSong(candidate, seedSong) && seenKeys.add(songDeduplicationKey(candidate))) {
                        result.add(candidate)
                    }
                }
            }
        }

        return result.take(limit)
    }

    private fun filterAndDeduplicate(tracks: List<Song>, seedSong: Song): List<Song> {
        val result = mutableListOf<Song>()
        val seenKeys = mutableSetOf<String>()
        seenKeys.add(songDeduplicationKey(seedSong))

        for (track in tracks) {
            if (!isSameSong(track, seedSong) && seenKeys.add(songDeduplicationKey(track))) {
                result.add(track)
            }
        }
        return result
    }

    internal fun isSameSong(a: Song, b: Song): Boolean {
        if (a.id.isNotBlank() && a.id == b.id) return true
        if (!a.youtubeId.isNullOrBlank() && a.youtubeId == b.youtubeId) return true
        if (a.contentUriString.isNotBlank() && a.contentUriString == b.contentUriString) return true

        val aYt = a.youtubeId
            ?: a.id.takeIf { it.startsWith("youtube_") }?.removePrefix("youtube_")
            ?: a.contentUriString.takeIf { it.startsWith("youtube://") }?.removePrefix("youtube://")
        val bYt = b.youtubeId
            ?: b.id.takeIf { it.startsWith("youtube_") }?.removePrefix("youtube_")
            ?: b.contentUriString.takeIf { it.startsWith("youtube://") }?.removePrefix("youtube://")
        if (!aYt.isNullOrBlank() && aYt == bYt) return true

        if (a.title.isNotBlank() && a.artist.isNotBlank() &&
            a.title.trim().equals(b.title.trim(), ignoreCase = true) &&
            a.artist.trim().equals(b.artist.trim(), ignoreCase = true)
        ) {
            return true
        }
        return false
    }

    internal fun songDeduplicationKey(song: Song): String {
        val ytid = song.youtubeId?.takeIf { it.isNotBlank() }
            ?: song.contentUriString.takeIf { it.startsWith("youtube://") }?.removePrefix("youtube://")
            ?: song.id.takeIf { it.startsWith("youtube_") }?.removePrefix("youtube_")
        if (ytid != null) return "yt::$ytid"
        if (song.id.isNotBlank()) return "id::${song.id}"
        return "norm::${song.title.trim().lowercase()}:::${song.artist.trim().lowercase()}"
    }

    internal fun InnertubeTrack.toDomainSong(): Song {
        val artistList = if (artists.isNotEmpty()) artists else listOf(artist)
        val artistRefs = artistList.mapIndexed { index, name ->
            ArtistRef(
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
}
