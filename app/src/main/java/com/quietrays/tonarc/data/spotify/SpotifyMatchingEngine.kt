package com.quietrays.tonarc.data.spotify

import com.quietrays.tonarc.data.model.ArtistRef
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.network.spotify.SpotifyTrack
import com.quietrays.tonarc.data.network.youtube.InnertubeApiService
import com.quietrays.tonarc.data.network.youtube.InnertubeTrack
import com.quietrays.tonarc.data.repository.MusicRepository
import com.quietrays.tonarc.utils.FuzzySearchMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class MatchProgress(
    val current: Int,
    val total: Int,
    val currentTrackTitle: String
)

data class MatchResult(
    val originalTrack: SpotifyTrack,
    val matchedSong: Song?,
    val matchedYouTubeTrack: InnertubeTrack? = null,
    val isLocalMatch: Boolean = false
)

/**
 * Matching engine for resolving imported Spotify tracks against the user's local music library
 * and falling back to YouTube Music (Innertube) cloud streams.
 */
@Singleton
class SpotifyMatchingEngine @Inject constructor(
    private val innertubeApiService: InnertubeApiService,
    private val musicRepository: MusicRepository
) {

    companion object {
        private const val TAG = "SpotifyMatchingEngine"
        private const val MAX_CONCURRENT_CLOUD_SEARCHES = 3
        private const val LOCAL_DURATION_TOLERANCE_MS = 12_000L
        private const val CLOUD_DURATION_TOLERANCE_MS = 15_000L
        private const val LOCAL_MATCH_THRESHOLD = 0.65f
        private const val CLOUD_MATCH_THRESHOLD = 0.50f
        private const val PROGRESS_THROTTLE_MS = 60L
    }

    private data class IndexedLocalSong(
        val song: Song,
        val normTitle: String,
        val primaryArtist: String,
        val allArtists: List<String>
    )

    /**
     * Matches a list of [SpotifyTrack] items against local library songs and/or YouTube Music.
     *
     * @param tracks List of Spotify tracks to match.
     * @param matchLocal When true, attempts to match against local library first.
     * @param matchCloud When true, searches YouTube Music for tracks that were not matched locally.
     * @param onProgress Optional callback invoked on every processed track with [MatchProgress].
     * @return List of [MatchResult] corresponding 1:1 in order to the input [tracks].
     */
    suspend fun matchTracks(
        tracks: List<SpotifyTrack>,
        matchLocal: Boolean = true,
        matchCloud: Boolean = true,
        onProgress: ((MatchProgress) -> Unit)? = null
    ): List<MatchResult> = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext emptyList()

        val indexedLocalSongs = if (matchLocal) {
            val localSongs = try {
                musicRepository.getAllSongsOnce()
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to fetch local songs for matching")
                emptyList()
            }
            localSongs.map { song ->
                IndexedLocalSong(
                    song = song,
                    normTitle = normalizeTitle(song.title),
                    primaryArtist = extractPrimaryArtist(song.artist),
                    allArtists = song.artists.map { extractPrimaryArtist(it.name) }
                        .ifEmpty { listOf(extractPrimaryArtist(song.artist)) }
                )
            }
        } else {
            emptyList()
        }

        val cloudSemaphore = Semaphore(MAX_CONCURRENT_CLOUD_SEARCHES)
        val progressCounter = AtomicInteger(0)
        val lastProgressEmitTimestamp = AtomicLong(0L)
        val total = tracks.size

        coroutineScope {
            tracks.map { track ->
                async {
                    var result: MatchResult? = null

                    // 1. Try local library matching
                    if (matchLocal && indexedLocalSongs.isNotEmpty()) {
                        val localMatch = findBestLocalMatch(track, indexedLocalSongs)
                        if (localMatch != null) {
                            result = MatchResult(
                                originalTrack = track,
                                matchedSong = localMatch,
                                matchedYouTubeTrack = null,
                                isLocalMatch = true
                            )
                        }
                    }

                    // 2. Fallback to YouTube Music cloud search
                    if (result == null && matchCloud) {
                        result = cloudSemaphore.withPermit {
                            searchCloudTrack(track)
                        }
                    }

                    // 3. Fallback when neither local nor cloud found a match
                    val finalResult = result ?: MatchResult(
                        originalTrack = track,
                        matchedSong = null,
                        matchedYouTubeTrack = null,
                        isLocalMatch = false
                    )

                    val current = progressCounter.incrementAndGet()
                    val now = System.currentTimeMillis()
                    val lastEmit = lastProgressEmitTimestamp.get()
                    if (current == total || now - lastEmit >= PROGRESS_THROTTLE_MS) {
                        lastProgressEmitTimestamp.set(now)
                        onProgress?.invoke(
                            MatchProgress(
                                current = current,
                                total = total,
                                currentTrackTitle = track.title
                            )
                        )
                    }

                    finalResult
                }
            }.awaitAll()
        }
    }

    private fun findBestLocalMatch(
        track: SpotifyTrack,
        candidates: List<IndexedLocalSong>
    ): Song? {
        var bestSong: Song? = null
        var bestScore = LOCAL_MATCH_THRESHOLD

        val spotifyArtists = track.artists.ifEmpty { listOf(track.artist) }

        for (candidate in candidates) {
            // Check duration tolerance if local duration is > 0 and track durationMs is > 0
            if (candidate.song.duration > 0 && track.durationMs > 0) {
                if (abs(candidate.song.duration - track.durationMs) > LOCAL_DURATION_TOLERANCE_MS) {
                    continue
                }
            }

            val score = scoreCandidate(
                spotifyTitle = track.title,
                spotifyArtist = track.artist,
                spotifyArtists = spotifyArtists,
                candidateTitle = candidate.normTitle,
                candidateArtist = candidate.primaryArtist,
                candidateArtists = candidate.allArtists
            )

            if (score > bestScore) {
                bestScore = score
                bestSong = candidate.song
                if (score >= 0.98f) {
                    break // Exact or near-perfect match found
                }
            }
        }

        return bestSong
    }

    private suspend fun searchCloudTrack(track: SpotifyTrack): MatchResult {
        val query = "${track.title} ${track.artist}".trim()
        if (query.isBlank()) {
            return MatchResult(
                originalTrack = track,
                matchedSong = null,
                matchedYouTubeTrack = null,
                isLocalMatch = false
            )
        }

        return try {
            val searchResult = innertubeApiService.search(query, InnertubeApiService.YTM_FILTER_SONGS)
            val songs = searchResult.songs
            if (songs.isNotEmpty()) {
                val bestCandidate = findBestCloudMatch(track, songs) ?: songs.first()
                MatchResult(
                    originalTrack = track,
                    matchedSong = bestCandidate.toSong(),
                    matchedYouTubeTrack = bestCandidate,
                    isLocalMatch = false
                )
            } else {
                MatchResult(
                    originalTrack = track,
                    matchedSong = null,
                    matchedYouTubeTrack = null,
                    isLocalMatch = false
                )
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Cloud search failed for query: $query")
            MatchResult(
                originalTrack = track,
                matchedSong = null,
                matchedYouTubeTrack = null,
                isLocalMatch = false
            )
        }
    }

    private fun findBestCloudMatch(
        track: SpotifyTrack,
        candidates: List<InnertubeTrack>
    ): InnertubeTrack? {
        var bestTrack: InnertubeTrack? = null
        var bestScore = CLOUD_MATCH_THRESHOLD

        val spotifyArtists = track.artists.ifEmpty { listOf(track.artist) }

        for (candidate in candidates) {
            val candDurationMs = candidate.durationSeconds * 1000L
            val durationWithinRange = candDurationMs <= 0 || track.durationMs <= 0 ||
                    abs(candDurationMs - track.durationMs) <= CLOUD_DURATION_TOLERANCE_MS

            val candArtists = candidate.artists.ifEmpty { listOf(candidate.artist) }

            val score = scoreCandidate(
                spotifyTitle = track.title,
                spotifyArtist = track.artist,
                spotifyArtists = spotifyArtists,
                candidateTitle = candidate.title,
                candidateArtist = candidate.artist,
                candidateArtists = candArtists
            )

            val finalScore = if (durationWithinRange) score else score * 0.8f

            if (finalScore > bestScore) {
                bestScore = finalScore
                bestTrack = candidate
                if (finalScore >= 0.98f) {
                    break
                }
            }
        }

        return bestTrack ?: candidates.firstOrNull()
    }

    private fun scoreCandidate(
        spotifyTitle: String,
        spotifyArtist: String,
        spotifyArtists: List<String>,
        candidateTitle: String,
        candidateArtist: String,
        candidateArtists: List<String>
    ): Float {
        val normSpotTitle = normalizeTitle(spotifyTitle)
        val normCandTitle = normalizeTitle(candidateTitle)

        if (normSpotTitle.isBlank() || normCandTitle.isBlank()) return 0f

        // Title score
        val titleScore: Float = if (normSpotTitle == normCandTitle) {
            1.0f
        } else {
            val sim = FuzzySearchMatcher.similarity(normSpotTitle, normCandTitle)
            val score = FuzzySearchMatcher.scoreMatch(normCandTitle, normSpotTitle)
            maxOf(sim, score)
        }

        if (titleScore < 0.55f) return 0f

        // Artist score
        val normSpotPrimaryArtist = extractPrimaryArtist(spotifyArtist)
        val normCandPrimaryArtist = extractPrimaryArtist(candidateArtist)

        val artistScore: Float = if (normSpotPrimaryArtist == normCandPrimaryArtist && normSpotPrimaryArtist.isNotBlank()) {
            1.0f
        } else {
            var bestArtistSim = if (normSpotPrimaryArtist.isNotBlank() && normCandPrimaryArtist.isNotBlank()) {
                FuzzySearchMatcher.similarity(normSpotPrimaryArtist, normCandPrimaryArtist)
            } else 0f

            for (sa in spotifyArtists) {
                val normSa = extractPrimaryArtist(sa)
                if (normSa.isBlank()) continue
                for (ca in candidateArtists) {
                    val normCa = extractPrimaryArtist(ca)
                    if (normCa.isBlank()) continue
                    if (normSa == normCa) {
                        bestArtistSim = maxOf(bestArtistSim, 1.0f)
                    } else {
                        val sim = FuzzySearchMatcher.similarity(normSa, normCa)
                        bestArtistSim = maxOf(bestArtistSim, sim)
                    }
                }
            }
            bestArtistSim
        }

        if (artistScore < 0.45f) return 0f

        return (titleScore * 0.65f) + (artistScore * 0.35f)
    }

    internal fun normalizeText(input: String): String {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace(Regex("""\p{M}"""), "")
            .lowercase()
        return PUNCTUATION_PATTERN.replace(normalized, " ")
            .replace(MULTI_SPACE_PATTERN, " ")
            .trim()
    }

    internal fun normalizeTitle(rawTitle: String): String {
        var title = rawTitle.trim()
        title = title.replace(FEATURE_PATTERN, " ")
        title = title.replace(BRACKET_TAG_PATTERN, " ")
        title = title.replace(PAREN_METADATA_PATTERN, " ")
        title = title.replace(INLINE_FEATURE_PATTERN, " ")
        return normalizeText(title)
    }

    internal fun extractPrimaryArtist(rawArtist: String): String {
        var artist = rawArtist.trim()
        artist = artist.replace(FEATURE_PATTERN, " ")
        artist = artist.replace(BRACKET_TAG_PATTERN, " ")
        val firstToken = artist.split(ARTIST_SPLIT_PATTERN).firstOrNull()?.trim() ?: artist
        return normalizeText(firstToken)
    }

    internal fun InnertubeTrack.toSong(): Song {
        val artistList = if (artists.isNotEmpty()) artists else listOf(artist)
        val artistRefs = artistList.mapIndexed { index, name ->
            ArtistRef(
                id = if (index == 0) -abs(name.hashCode().toLong().takeIf { it != 0L } ?: 1L)
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

    private val FEATURE_PATTERN = Regex("""(?i)\s*[\(\[\{]\s*(?:feat|featuring|ft|with)\.?\s+[^)\]\}]+[\)\]\}]""")
    private val BRACKET_TAG_PATTERN = Regex("""(?i)\s*\[[^\]]*\]""")
    private val PAREN_METADATA_PATTERN = Regex("""(?i)\s*\(\s*(?:official\s*(?:audio|video|music\s*video|lyric\s*video)?|remaster(?:ed)?(?:\s*\d+)?|\d{4}\s*remaster|deluxe(?:\s*edition)?|bonus\s*track|explicit|clean|audio|visualizer|album\s*version|single\s*version|live(?:\s+at\s+[^)]+)?)\s*\)""")
    private val INLINE_FEATURE_PATTERN = Regex("""(?i)\s+(?:feat|featuring|ft|with)\.?\s+.+$""")
    private val PUNCTUATION_PATTERN = Regex("""[^\p{L}\p{N}\s]""")
    private val MULTI_SPACE_PATTERN = Regex("""\s+""")
    private val ARTIST_SPLIT_PATTERN = Regex("""(?i)\s*(?:feat\.?|ft\.?|featuring|&|,|/|x|X|with|vs\.?|;)\s+""")
}
