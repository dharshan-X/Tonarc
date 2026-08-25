package com.quietrays.tonarc.data.recommendation

import com.quietrays.tonarc.data.listenbrainz.ListenBrainzRepository
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.playlist.nlp.GenreTaxonomy
import com.quietrays.tonarc.data.preferences.UserPreferencesRepository
import com.quietrays.tonarc.data.repository.MusicRepository
import com.quietrays.tonarc.data.youtube.YouTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stage 1 of recommendation engine: aggregates candidate tracks across multiple sources in parallel.
 * Sources:
 * 1. Innertube radio graph (YT_RADIO)
 * 2. ListenBrainz Labs similar-artists graph (LB_SIMILAR_ARTIST)
 * 3. Library genre taxonomy expansion (GENRE_EXPANSION)
 * 4. On-device session co-occurrences (LIBRARY_COOCCURRENCE)
 * 5. Selected favorite artists
 */
@Singleton
class CandidateAggregator @Inject constructor(
    private val youTubeRepository: YouTubeRepository,
    private val listenBrainzRepository: ListenBrainzRepository,
    private val musicRepository: MusicRepository,
    private val itemEmbeddingStore: ItemEmbeddingStore,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    companion object {
        private const val TAG = "CandidateAggregator"
    }

    suspend fun collect(
        seedSongs: List<Song>,
        limit: Int = 100,
        excludedSongIds: Set<String> = emptySet()
    ): List<RecommendationCandidate> = withContext(Dispatchers.IO) {
        val favoriteArtists = runCatching { userPreferencesRepository.favoriteArtistsFlow.first() }.getOrDefault(emptySet())
        val validSeeds = seedSongs.filterNot { it.id in excludedSongIds }

        // Cold Start Fallback: If no seeds and no favorite artists are present, query top genres and randomized unplayed tracks
        if (validSeeds.isEmpty() && favoriteArtists.isEmpty()) {
            return@withContext collectColdStartFallback(limit, excludedSongIds)
        }

        val topSeeds = validSeeds.take(5)
        val ytDeferred = async { collectYouTubeRadioCandidates(topSeeds) }
        val lbDeferred = async { collectListenBrainzCandidates(topSeeds) }
        val genreDeferred = async { collectGenreCandidates(topSeeds) }
        val cooccurDeferred = async { collectCooccurrenceCandidates(topSeeds) }
        val favDeferred = async { collectFavoriteArtistCandidates(favoriteArtists) }

        val ytCandidates = runCatching { ytDeferred.await() }
            .onFailure { Timber.tag(TAG).w(it, "YouTube candidate collection failed") }
            .getOrDefault(emptyList())

        val lbCandidates = runCatching { lbDeferred.await() }
            .onFailure { Timber.tag(TAG).w(it, "ListenBrainz candidate collection failed") }
            .getOrDefault(emptyList())

        val genreCandidates = runCatching { genreDeferred.await() }
            .onFailure { Timber.tag(TAG).w(it, "Genre candidate collection failed") }
            .getOrDefault(emptyList())

        val cooccurCandidates = runCatching { cooccurDeferred.await() }
            .onFailure { Timber.tag(TAG).w(it, "Cooccurrence candidate collection failed") }
            .getOrDefault(emptyList())

        val favCandidates = runCatching { favDeferred.await() }
            .onFailure { Timber.tag(TAG).w(it, "Favorite artist candidate collection failed") }
            .getOrDefault(emptyList())

        val allCandidates = favCandidates + cooccurCandidates + ytCandidates + lbCandidates + genreCandidates
        val deduplicated = deduplicateCandidates(allCandidates, excludedSongIds)

        // Deterministic Fallback: If co-occurrence and seed queries return fewer than target minimum (10), pad with fallback
        if (deduplicated.size < 10) {
            val fallback = collectColdStartFallback(limit, excludedSongIds)
            deduplicateCandidates(deduplicated + fallback, excludedSongIds).take(limit)
        } else {
            deduplicated.take(limit)
        }
    }

    private suspend fun collectColdStartFallback(
        limit: Int,
        excludedSongIds: Set<String>
    ): List<RecommendationCandidate> {
        val candidates = mutableListOf<RecommendationCandidate>()
        val allLocalSongs = runCatching { musicRepository.getAudioFiles().first() }.getOrDefault(emptyList())

        if (allLocalSongs.isNotEmpty()) {
            val unexcludedSongs = allLocalSongs.filterNot { it.id in excludedSongIds }

            // 1. Group by top-rated / taxonomy genres
            val genreGroups = unexcludedSongs
                .filter { !it.genre.isNullOrBlank() }
                .groupBy { GenreTaxonomy.familyOf(it.genre!!.lowercase()) }

            for ((_, songsInGenre) in genreGroups) {
                songsInGenre.shuffled().take(6).forEach { song ->
                    candidates.add(
                        RecommendationCandidate(
                            song = song,
                            sourceType = CandidateSourceType.GENRE_EXPANSION,
                            sourceStrength = 0.70,
                            seedSongId = null
                        )
                    )
                }
            }

            // 2. Randomized unplayed tracks from local library
            val randomizedUnplayed = unexcludedSongs.shuffled().take(20)
            randomizedUnplayed.forEach { song ->
                candidates.add(
                    RecommendationCandidate(
                        song = song,
                        sourceType = CandidateSourceType.GENRE_EXPANSION,
                        sourceStrength = 0.55,
                        seedSongId = null
                    )
                )
            }
        }

        // 3. Fallback online charts / recommendations if available
        val trendingSongs = runCatching { youTubeRepository.getCharts().first() }.getOrDefault(emptyList())
        trendingSongs.filterNot { it.id in excludedSongIds }.take(15).forEach { song ->
            candidates.add(
                RecommendationCandidate(
                    song = song,
                    sourceType = CandidateSourceType.YT_RADIO,
                    sourceStrength = 0.65,
                    seedSongId = null
                )
            )
        }

        return deduplicateCandidates(candidates, excludedSongIds).take(limit)
    }

    private suspend fun collectFavoriteArtistCandidates(favoriteArtists: Set<String>): List<RecommendationCandidate> = coroutineScope {
        val artistsToFetch = favoriteArtists
        val deferredList = artistsToFetch.map { artist ->
            async {
                val songs = runCatching { youTubeRepository.searchSongsPaginated(artist).songs }.getOrDefault(emptyList())
                songs.take(4).map { song ->
                    RecommendationCandidate(
                        song = song,
                        sourceType = CandidateSourceType.LB_SIMILAR_ARTIST,
                        sourceStrength = 0.85,
                        seedSongId = null
                    )
                }
            }
        }
        deferredList.flatMap { runCatching { it.await() }.getOrDefault(emptyList()) }
    }

    private suspend fun collectCooccurrenceCandidates(seeds: List<Song>): List<RecommendationCandidate> {
        val results = mutableListOf<RecommendationCandidate>()
        val allSongs = runCatching { musicRepository.getAudioFiles().first() }.getOrDefault(emptyList())
        val songsById = allSongs.associateBy { it.id }

        for (seed in seeds.take(5)) {
            val similar = itemEmbeddingStore.getSimilarSongs(seed.id, limit = 5)
            for ((neighborId, score) in similar) {
                val neighborSong = songsById[neighborId] ?: continue
                results.add(
                    RecommendationCandidate(
                        song = neighborSong,
                        sourceType = CandidateSourceType.LIBRARY_COOCCURRENCE,
                        sourceStrength = score,
                        seedSongId = seed.id
                    )
                )
            }
        }
        return results
    }

    private suspend fun collectYouTubeRadioCandidates(seeds: List<Song>): List<RecommendationCandidate> = coroutineScope {
        val deferredList = seeds.take(5).map { seed ->
            async {
                val radioTracks = runCatching { youTubeRepository.getRadioTracksForSong(seed) }.getOrDefault(emptyList())
                radioTracks.map { track ->
                    RecommendationCandidate(
                        song = track,
                        sourceType = CandidateSourceType.YT_RADIO,
                        sourceStrength = 0.85,
                        seedSongId = seed.id
                    )
                }
            }
        }
        deferredList.flatMap { runCatching { it.await() }.getOrDefault(emptyList()) }
    }

    private suspend fun collectListenBrainzCandidates(seeds: List<Song>): List<RecommendationCandidate> = coroutineScope {
        val deferredList = seeds.take(5).map { seed ->
            async {
                val artistName = seed.artist.trim().takeIf { it.isNotBlank() } ?: return@async emptyList()
                val recordings = runCatching { listenBrainzRepository.getLbRadioTracks(artistName) }.getOrDefault(emptyList())
                val innerDeferred = recordings.take(3).map { rec ->
                    async {
                        val query = "${rec.trackName} ${rec.artistName}".trim()
                        if (query.isBlank()) return@async null
                        val songs = runCatching { youTubeRepository.searchSongsPaginated(query).songs }.getOrDefault(emptyList())
                        songs.firstOrNull()?.let { song ->
                            RecommendationCandidate(
                                song = song,
                                sourceType = CandidateSourceType.LB_SIMILAR_ARTIST,
                                sourceStrength = 0.80,
                                seedSongId = seed.id
                            )
                        }
                    }
                }
                innerDeferred.mapNotNull { runCatching { it.await() }.getOrNull() }
            }
        }
        deferredList.flatMap { runCatching { it.await() }.getOrDefault(emptyList()) }
    }

    private suspend fun collectGenreCandidates(seeds: List<Song>): List<RecommendationCandidate> {
        val results = mutableListOf<RecommendationCandidate>()
        val knownFamilies = seeds.mapNotNull { it.genre?.lowercase()?.let(GenreTaxonomy::familyOf) }.distinct()
        if (knownFamilies.isEmpty()) return results

        val allSongs = runCatching { musicRepository.getAudioFiles().first() }.getOrDefault(emptyList())
        for (family in knownFamilies.take(2)) {
            val matchingSongs = allSongs.filter { it.genre?.lowercase()?.let(GenreTaxonomy::familyOf) == family }
            matchingSongs.shuffled().take(5).forEach { song ->
                results.add(
                    RecommendationCandidate(
                        song = song,
                        sourceType = CandidateSourceType.GENRE_EXPANSION,
                        sourceStrength = 0.60,
                        seedSongId = null
                    )
                )
            }
        }
        return results
    }

    fun deduplicateCandidates(
        candidates: List<RecommendationCandidate>,
        excludedSongIds: Set<String> = emptySet()
    ): List<RecommendationCandidate> {
        val deduplicated = linkedMapOf<String, RecommendationCandidate>()
        for (candidate in candidates) {
            val song = candidate.song
            if (song.id in excludedSongIds) continue
            val ytId = song.youtubeId
            if (ytId != null && ytId in excludedSongIds) continue

            val key = normalizeKey(song)
            val existing = deduplicated[key]
            val safeStrength = if (candidate.sourceStrength.isNaN() || candidate.sourceStrength.isInfinite()) 0.5 else candidate.sourceStrength.coerceIn(0.0, 1.0)
            val updatedCandidate = if (candidate.sourceStrength == safeStrength) candidate else candidate.copy(sourceStrength = safeStrength)

            if (existing == null || updatedCandidate.sourceStrength > existing.sourceStrength) {
                deduplicated[key] = updatedCandidate
            }
        }
        return deduplicated.values.toList()
    }

    private fun normalizeKey(song: Song): String {
        val ytid = song.youtubeId?.takeIf { it.isNotBlank() }
            ?: song.contentUriString.takeIf { it.startsWith("youtube://") }?.removePrefix("youtube://")
            ?: song.id.takeIf { it.startsWith("youtube_") }?.removePrefix("youtube_")
        if (ytid != null) return "yt::$ytid"
        val navId = song.navidromeId?.takeIf { it.isNotBlank() }
        if (navId != null) return "nav::$navId"
        val jellyId = song.jellyfinId?.takeIf { it.isNotBlank() }
        if (jellyId != null) return "jelly::$jellyId"
        if (song.id.isNotBlank()) return "id::${song.id}"
        return "norm::${song.title.trim().lowercase()}:::${song.artist.trim().lowercase()}"
    }
}
