package com.quietrays.tonarc.data.repository

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.core.net.toUri
import com.google.gson.Gson
import com.kyant.taglib.TagLib
import com.quietrays.tonarc.R
import com.quietrays.tonarc.data.database.LyricsDao
import com.quietrays.tonarc.data.database.LyricsEntity
import com.quietrays.tonarc.data.database.MusicDao
import com.quietrays.tonarc.data.model.Lyrics
import com.quietrays.tonarc.data.model.SyncedLine
import com.quietrays.tonarc.data.model.LyricsSourcePreference
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.network.lyrics.LrcLibApiService
import com.quietrays.tonarc.data.network.lyrics.LrcLibResponse
import com.quietrays.tonarc.data.network.youtube.InnertubeApiService
import com.quietrays.tonarc.data.preferences.UserPreferencesRepository
import com.quietrays.tonarc.utils.LyricsImportSecurity
import com.quietrays.tonarc.utils.LyricsImportValidationResult
import com.quietrays.tonarc.utils.LogUtils
import com.quietrays.tonarc.utils.LyricsUtils
import com.quietrays.tonarc.utils.NetworkRetryUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private val EMBEDDED_LYRICS_KEYS = listOf("LYRICS", "SYNCEDLYRICS", "TTML", "UNSYNCEDLYRICS")

private fun Lyrics.isValid(): Boolean = !synced.isNullOrEmpty() || !plain.isNullOrEmpty()

internal fun parseBestEmbeddedLyricsField(propertyMap: Map<String, Array<String>>?): Lyrics? {
    var firstPlainLyrics: Lyrics? = null

    EMBEDDED_LYRICS_KEYS.forEach { key ->
        propertyMap?.get(key).orEmpty().forEach { field ->
            if (field.isBlank()) return@forEach

            val parsedLyrics = LyricsUtils.parseLyrics(field)
            if (!parsedLyrics.isValid()) return@forEach

            val localLyrics = parsedLyrics.copy(areFromRemote = false)
            if (!localLyrics.synced.isNullOrEmpty()) return localLyrics
            if (firstPlainLyrics == null) firstPlainLyrics = localLyrics
        }
    }

    return firstPlainLyrics
}

/**
 * LyricsData for JSON disk cache (matches Rhythm's format)
 */
private data class LyricsData(
    val plainLyrics: String?,
    val syncedLyrics: String?,
    val wordByWordLyrics: String? = null
) {
    fun hasLyrics(): Boolean =
        !plainLyrics.isNullOrBlank() ||
            !syncedLyrics.isNullOrBlank() ||
            !wordByWordLyrics.isNullOrBlank()
}

private data class RemoteSearchStrategy(
    val name: String,
    val request: suspend () -> Array<LrcLibResponse>?
)

private data class RemoteSearchBatch(
    val strategyName: String,
    val responses: List<LrcLibResponse>
)

private enum class RemoteLyricsMatchMode {
    AUTOMATIC,
    CANDIDATE
}

private data class RemoteLyricsMatch(
    val response: LrcLibResponse,
    val score: Int
)

@Singleton
class LyricsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lrcLibApiService: LrcLibApiService,
    private val innertubeApiService: InnertubeApiService,
    private val lyricsDao: LyricsDao,
    private val okHttpClient: OkHttpClient,
    private val userPreferencesRepository: UserPreferencesRepository
) : LyricsRepository {


    companion object {
        private const val TAG = "LyricsRepository"
        
        private const val MAX_LYRICS_CACHE_SIZE = 150
        
        private const val LRCLIB_MIN_DELAY = 100L
        private const val MAX_CALLS_PER_MINUTE = 30
        private const val NETWORK_RETRY_ATTEMPTS = 3
        private const val NETWORK_RETRY_INITIAL_DELAY_MS = 500L

        private val BRACKETED_QUALIFIER_REGEX = Regex("""[\(\[\{]([^)\]\}]*)[\)\]\}]""")
        private val FEATURE_QUALIFIER_REGEX = Regex("""\b(feat(?:uring)?|ft)\.?\b""", RegexOption.IGNORE_CASE)
        private val TITLE_SEPARATOR_REGEX = Regex("""\s+[-\u2013\u2014:]\s+""")
        private val TIMING_VARIANT_KEYWORDS = setOf(
            "remix",
            "mix",
            "mashup",
            "bootleg",
            "edit",
            "extended",
            "radio",
            "club",
            "vip",
            "dub",
            "live",
            "acoustic",
            "unplugged",
            "sped",
            "slowed",
            "nightcore",
            "instrumental",
            "karaoke",
            "cover",
            "demo",
            "rework",
            "flip",
            "refix"
        )
        private val TITLE_DROP_QUALIFIERS = setOf(
            "explicit",
            "clean",
            "mono",
            "stereo",
            "official audio",
            "official video",
            "official music video",
            "official visualizer",
            "visualizer",
            "lyrics",
            "lyric video",
            "official lyric video",
            "color coded lyrics",
            "audio",
            "mv",
            "4k",
            "hd",
            "clip officiel",
            "album version",
            "single version",
            "original version",
            "deluxe",
            "deluxe edition",
            "anniversary edition",
            "bonus track",
            "remastered",
            "remaster",
            "soundtrack",
            "ost"
        )
        private val UNKNOWN_ARTISTS = setOf(
            "",
            "<unknown>",
            "unknown",
            "unknown artist",
            "various artists",
            "various"
        )
        private val ARTIST_CONNECTOR_TOKENS = setOf(
            "feat",
            "featuring",
            "ft",
            "and",
            "with",
            "x",
            "vs",
            "the"
        )
    }

    object LyricsQueryCleaner {
        private val MEDIA_TAG_PATTERNS = listOf(
            Regex("""(?i)\s*[\(\[\{]\s*(?:official\s+)?(?:music\s+)?video\s*[\)\]\}]"""),
            Regex("""(?i)\s*[\(\[\{]\s*(?:official\s+)?audio\s*[\)\]\}]"""),
            Regex("""(?i)\s*[\(\[\{]\s*(?:official\s+)?visualizer\s*[\)\]\}]"""),
            Regex("""(?i)\s*[\(\[\{]\s*(?:official\s+)?lyric(?:s)?\s*(?:video)?\s*[\)\]\}]"""),
            Regex("""(?i)\s*[\(\[\{]\s*color\s*coded\s*lyrics?\s*[\)\]\}]"""),
            Regex("""(?i)\s*[\(\[\{]\s*mv\s*[\)\]\}]"""),
            Regex("""(?i)\s*[\(\[\{]\s*4k(?:\s*60fps)?\s*[\)\]\}]"""),
            Regex("""(?i)\s*[\(\[\{]\s*hd\s*[\)\]\}]"""),
            Regex("""(?i)\s*[\(\[\{]\s*clip\s*officiel\s*[\)\]\}]"""),
            Regex("""(?i)\s*[\(\[\{]\s*(?:slowed\s*[+&]\s*reverb|slowed|reverb)\s*[\)\]\}]"""),
            Regex("""(?i)\s*[\(\[\{]\s*(?:sped\s*up|speed\s*up|nightcore)\s*[\)\]\}]"""),
            Regex("""(?i)\s*[\(\[\{]\s*(?:acoustic|unplugged|live(?:\s+at\s+[^)\]\}]+)?)\s*[\)\]\}]"""),
            Regex("""(?i)\s*[\(\[\{]\s*(?:remastered(?:\s*\d+)?|\d{4}\s*remaster|deluxe(?:\s*edition)?|anniversary(?:\s*edition)?|bonus\s*track)\s*[\)\]\}]"""),
            Regex("""(?i)\s*[\(\[\{]\s*(?:from\s+["'].*?["']|soundtrack|ost)\s*[\)\]\}]"""),
            Regex("""(?i)\s*[\(\[\{]\s*prod\.?\s+by\s+[^)\]\}]+[\)\]\}]""")
        )

        private val FEATURE_PATTERN = Regex("""(?i)\s*[\(\[\{]\s*(?:feat|featuring|ft)\.?\s+[^)\]\}]+[\)\]\}]""")
        private val INLINE_FEATURE_PATTERN = Regex("""(?i)\s+(?:feat|featuring|ft)\.?\s+.+$""")
        private val TRACK_NUMBER_PREFIX = Regex("""^(\d{1,3}[\.\-\s_]+)""")
        private val AUDIO_EXTENSIONS = Regex("""(?i)\.(mp3|flac|m4a|wav|ogg|opus|aac|wma)$""")
        private val TITLE_ARTIST_SEPARATORS = Regex("""\s+[-–—:|~•]\s+""")

        fun normalizeText(input: String): String {
            var text = input
                .replace('’', '\'')
                .replace('‘', '\'')
                .replace('“', '"')
                .replace('”', '"')
                .replace('«', '"')
                .replace('»', '"')
                .replace(Regex("""[【《「『]"""), "[")
                .replace(Regex("""[】》」』]"""), "]")
            text = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("""\p{M}"""), "")
            return text.trim()
        }

        fun cleanTitle(rawTitle: String): String {
            var title = rawTitle.trim()
            title = title.replace(AUDIO_EXTENSIONS, "")
            title = title.replace(TRACK_NUMBER_PREFIX, "")
            MEDIA_TAG_PATTERNS.forEach { regex ->
                title = title.replace(regex, "")
            }
            title = title.replace(FEATURE_PATTERN, "")
            title = title.replace(INLINE_FEATURE_PATTERN, "")
            return title.trim().trim('-', '–', '—', ':', '|', '~', '•').trim()
        }

        fun cleanArtist(rawArtist: String): String {
            var artist = rawArtist.trim()
            artist = artist.replace(Regex("""(?i)\s*\([^)]*\)"""), "")
            artist = artist.replace(Regex("""(?i)\s*\[[^\]]*\]"""), "")
            return artist.trim()
        }

        fun getPrimaryArtist(rawArtist: String): String {
            val clean = cleanArtist(rawArtist)
            val splitRegex = Regex("""(?i)\s*(?:feat\.?|ft\.?|featuring|&|,|/|x|X|with|vs\.?)\s+""")
            return clean.split(splitRegex).firstOrNull()?.trim().orEmpty().ifBlank { clean }
        }

        fun extractTitleAndArtistFromSeparator(title: String): Pair<String, String>? {
            val parts = title.split(TITLE_ARTIST_SEPARATORS, limit = 2)
            if (parts.size == 2) {
                val p1 = cleanArtist(parts[0])
                val p2 = cleanTitle(parts[1])
                if (p1.isNotBlank() && p2.isNotBlank()) {
                    return Pair(p1, p2)
                }
            }
            return null
        }
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val lyricsCache = LruCache<String, Lyrics>(MAX_LYRICS_CACHE_SIZE)

    private val lastApiCalls = ConcurrentHashMap<String, Long>()
    private val apiCallCounts = ConcurrentHashMap<String, RateLimitWindow>()

    private val gson = Gson()

    private suspend fun isExternalLyricsEnabled(): Boolean =
        userPreferencesRepository.externalLyricsEnabledFlow.first()

    /**
     * Executes multiple remote search strategies in parallel and returns the first non-empty result set.
     * This keeps search responsive while preserving the existing "first useful strategy wins" behavior.
     */
    private suspend fun runSearchStrategiesFast(
        strategies: List<RemoteSearchStrategy>
    ): List<LrcLibResponse> = coroutineScope {
        if (strategies.isEmpty()) return@coroutineScope emptyList()

        val channel = Channel<RemoteSearchBatch>(capacity = strategies.size)
        val jobs = strategies.map { strategy ->
            launch {
                val responses = runCatching {
                    withNetworkRetry(operationName = "lrclib_strategy:${strategy.name}") {
                        strategy.request()
                    }
                }.getOrElse { error ->
                    Timber.tag(TAG).d(
                        "Strategy ${strategy.name} failed after retries: ${error.message}"
                    )
                    null
                }
                    ?.toList()
                    .orEmpty()
                channel.trySend(
                    RemoteSearchBatch(
                        strategyName = strategy.name,
                        responses = responses
                    )
                )
            }
        }

        repeat(strategies.size) {
            val batch = channel.receive()
            if (batch.responses.isNotEmpty()) {
                Timber.tag(TAG).d("Fast search hit from strategy: ${batch.strategyName} (${batch.responses.size} results)")
                jobs.forEach { it.cancel() }
                channel.close()
                return@coroutineScope batch.responses.distinctBy { it.id }
            }
        }

        channel.close()
        emptyList()
    }

    private suspend fun <T> withNetworkRetry(
        operationName: String,
        maxAttempts: Int = NETWORK_RETRY_ATTEMPTS,
        initialDelayMs: Long = NETWORK_RETRY_INITIAL_DELAY_MS,
        shouldRetry: (Throwable) -> Boolean = { it is IOException || (it is HttpException && (it.code() == 429 || it.code() >= 500)) },
        block: suspend () -> T
    ): T {
        return NetworkRetryUtils.withNetworkRetry(
            operationName = operationName,
            maxAttempts = maxAttempts,
            initialDelayMs = initialDelayMs,
            shouldRetry = shouldRetry,
            onRetry = { attempt, attempts, throwable ->
                Timber.tag(TAG).d(
                    "Retrying $operationName after failure ($attempt/$attempts): ${throwable.message}"
                )
            },
            block = block
        )
    }

    private fun Int.isRetryableHttpStatusCode(): Boolean {
        return this == 429 || this in 500..599
    }

    /**
     * Calculate delay needed before next API call (matching Rhythm)
     */
    private data class RateLimitWindow(
        val windowStartMillis: Long,
        val count: Int
    )

    private fun calculateApiDelay(apiName: String, currentTime: Long): Long {
        val lastCall = lastApiCalls[apiName] ?: 0L
        val minDelay = when (apiName.lowercase()) {
            "lrclib" -> LRCLIB_MIN_DELAY
            else -> 250L
        }

        val timeSinceLastCall = currentTime - lastCall
        if (timeSinceLastCall < minDelay) {
            return minDelay - timeSinceLastCall
        }

        // Check if we're making too many calls in the current fixed 60s window
        val window = apiCallCounts[apiName]
        val callsInLastMinute = if (window != null && (currentTime - window.windowStartMillis) < 60000L) {
            window.count
        } else {
            0
        }
        if (callsInLastMinute >= MAX_CALLS_PER_MINUTE) {
            // Exponential backoff
            return minDelay * 2
        }

        return 0L
    }

    /**
     * Update last API call timestamp (matching Rhythm)
     */
    private fun updateLastApiCall(apiName: String, timestamp: Long) {
        lastApiCalls[apiName] = timestamp

        val currentWindow = apiCallCounts[apiName]
        val updatedWindow = if (currentWindow == null || (timestamp - currentWindow.windowStartMillis) >= 60000L) {
            RateLimitWindow(
                windowStartMillis = timestamp,
                count = 1
            )
        } else {
            currentWindow.copy(count = currentWindow.count + 1)
        }
        apiCallCounts[apiName] = updatedWindow
    }

    /**
     * Main lyrics fetching method with source preference support (matching Rhythm)
     */
    override suspend fun getLyrics(
        song: Song,
        sourcePreference: LyricsSourcePreference,
        forceRefresh: Boolean
    ): Lyrics? = withContext(Dispatchers.IO) {
        val cacheKey = generateCacheKey(song.id)
        
        Timber.tag(TAG).d("===== FETCH LYRICS START: ${song.displayArtist} - ${song.title} (forceRefresh=$forceRefresh, source=$sourcePreference) =====")

        if (!forceRefresh) {
            lyricsCache.get(cacheKey)?.let { cached ->
                Timber.tag(TAG).d("===== RETURNING IN-MEMORY CACHED LYRICS =====")
                return@withContext cached
            }
            Timber.tag(TAG).d("===== NO IN-MEMORY CACHE HIT, proceeding to fetch =====")
        } else {
            Timber.tag(TAG).d("===== FORCE REFRESH - BYPASSING IN-MEMORY CACHE =====")
        }

        if (!forceRefresh) {
            loadStoredLyrics(song, cacheKey, includeMemoryCache = false)?.let { stored ->
                lyricsCache.put(cacheKey, stored.first)
                Timber.tag(TAG).d("===== RETURNING STORED LYRICS WITHOUT REMOTE FETCH =====")
                return@withContext stored.first
            }
        }

        val remoteLyricsEnabled = isExternalLyricsEnabled()
        val fetchFromApi: Pair<String, suspend () -> Lyrics?> = "API" to { fetchLyricsFromAPI(song) }
        val fetchFromEmbedded: Pair<String, suspend () -> Lyrics?> = "Embedded" to { loadEmbeddedLyricsFromMetadata(song) }
        val fetchFromLocal: Pair<String, suspend () -> Lyrics?> = "Local" to { findLocalLyricsFile(song) }

        val sourceFetchers = when (sourcePreference) {
            LyricsSourcePreference.API_FIRST -> buildList {
                if (remoteLyricsEnabled) add(fetchFromApi)
                add(fetchFromEmbedded)
                add(fetchFromLocal)
            }
            LyricsSourcePreference.EMBEDDED_FIRST -> buildList {
                add(fetchFromEmbedded)
                if (remoteLyricsEnabled) add(fetchFromApi)
                add(fetchFromLocal)
            }
            LyricsSourcePreference.LOCAL_FIRST -> buildList {
                add(fetchFromLocal)
                add(fetchFromEmbedded)
                if (remoteLyricsEnabled) add(fetchFromApi)
            }
        }

        for ((sourceName, fetcher) in sourceFetchers) {
            try {
                val lyrics = fetcher()
                if (lyrics != null && lyrics.isValid()) {
                    Timber.tag(TAG).d("Found lyrics from $sourceName for: ${song.displayArtist} - ${song.title}")
                    
                    lyricsCache.put(cacheKey, lyrics)
                    
                    if (sourceName == "API") {
                        saveLocalLyricsJson(song, lyrics)
                    }
                    
                    return@withContext lyrics
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w("Error fetching from source $sourceName: ${e.message}")
            }
        }

        Timber.tag(TAG).d("No lyrics found from any source for: ${song.displayArtist} - ${song.title}")
        return@withContext null
    }

    override suspend fun getStoredLyrics(song: Song): Pair<Lyrics, String>? = withContext(Dispatchers.IO) {
        val cacheKey = generateCacheKey(song.id)
        loadStoredLyrics(song, cacheKey, includeMemoryCache = true)?.also { stored ->
            lyricsCache.put(cacheKey, stored.first)
        }
    }

    /**
     * Resolves the YouTube videoId for a song, extracting direct IDs or falling back to searching Innertube.
     */
    private suspend fun resolveVideoId(song: Song): String? {
        val directId = song.youtubeId?.takeIf { it.isNotBlank() }
            ?: song.id.removePrefix("youtube_").takeIf { song.id.startsWith("youtube_") && it.isNotBlank() }
            ?: song.contentUriString.removePrefix("youtube://").substringBefore('?').takeIf { song.contentUriString.startsWith("youtube://") && it.isNotBlank() }

        if (!directId.isNullOrBlank()) {
            return directId
        }

        val query = "${song.title} ${song.artist}".trim()
        if (query.isBlank()) return null

        return try {
            val searchResult = innertubeApiService.search(query)
            searchResult.songs.firstOrNull()?.videoId
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to search Innertube for videoId: $query")
            null
        }
    }

    /**
     * Attempts to fetch and parse YouTube Music transcript synchronized lyrics for a song.
     */
    private suspend fun fetchYouTubeTranscriptLyrics(song: Song): Pair<Lyrics, String>? {
        val videoId = resolveVideoId(song) ?: return null
        Timber.tag(TAG).d("Attempting YouTube Music transcript synced lyrics for videoId: $videoId")
        val transcript = runCatching { innertubeApiService.getTranscriptLyrics(videoId) }.getOrNull()
        if (!transcript.isNullOrBlank()) {
            val parsedLyrics = LyricsUtils.parseLyrics(transcript).copy(areFromRemote = true)
            if (parsedLyrics.isValid() && !parsedLyrics.synced.isNullOrEmpty()) {
                Timber.tag(TAG).d("YouTube Music synchronized transcript lyrics found for videoId: $videoId")
                song.id.toLongOrNull()?.let { songIdLong ->
                    try {
                        lyricsDao.insert(
                            LyricsEntity(
                                songId = songIdLong,
                                content = transcript,
                                isSynced = true,
                                source = "youtube"
                            )
                        )
                    } catch (e: Exception) {
                        Timber.tag(TAG).w(e, "Skipping database save for song ID: ${song.id}")
                    }
                }
                saveLocalLyricsJson(song, parsedLyrics)
                return Pair(parsedLyrics, transcript)
            }
        }
        return null
    }

    /**
     * Fetches lyrics from LRCLIB API with rate limiting, falling back to YouTube Music transcript synced lyrics.
     */
    private suspend fun fetchLyricsFromAPI(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val cachedJson = loadLocalLyricsJson(song)
        if (cachedJson != null) {
            Timber.tag(TAG).d("===== LOADED LYRICS FROM JSON DISK CACHE =====")
            return@withContext cachedJson
        }

        try {
            // 1. First check LRCLIB for synchronized lyrics
            val currentTime = System.currentTimeMillis()
            val delayNeeded = calculateApiDelay("lrclib", currentTime)
            if (delayNeeded > 0) {
                Timber.tag(TAG).d("Rate limiting: waiting ${delayNeeded}ms before API call")
                delay(delayNeeded)
            }
            updateLastApiCall("lrclib", System.currentTimeMillis())

            val rawTitle = song.title
            val rawArtist = song.displayArtist

            val cleanTitle = LyricsQueryCleaner.cleanTitle(rawTitle)
            val cleanArtist = LyricsQueryCleaner.cleanArtist(rawArtist)
            val primaryArtist = LyricsQueryCleaner.getPrimaryArtist(rawArtist)
            val normTitle = LyricsQueryCleaner.normalizeText(cleanTitle)
            val normArtist = LyricsQueryCleaner.normalizeText(cleanArtist)
            val extracted = LyricsQueryCleaner.extractTitleAndArtistFromSeparator(rawTitle)

            val searchStrategies = buildList {
                // 1. Clean track + artist
                if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank() && !isUnknownArtist(cleanArtist)) {
                    add(RemoteSearchStrategy("clean_track+artist") {
                        lrcLibApiService.searchLyrics(trackName = cleanTitle, artistName = cleanArtist)
                    })
                }
                // 2. Primary artist + clean track
                if (primaryArtist.isNotBlank() && primaryArtist != cleanArtist && !isUnknownArtist(primaryArtist)) {
                    add(RemoteSearchStrategy("primary_artist+track") {
                        lrcLibApiService.searchLyrics(trackName = cleanTitle, artistName = primaryArtist)
                    })
                }
                // 3. Combined query
                if (cleanTitle.isNotBlank()) {
                    val query = if (cleanArtist.isNotBlank() && !isUnknownArtist(cleanArtist)) {
                        "$cleanArtist $cleanTitle"
                    } else {
                        cleanTitle
                    }
                    add(RemoteSearchStrategy("combined_query") {
                        lrcLibApiService.searchLyrics(query = query)
                    })
                }
                // 4. Normalized without diacritics
                if ((normTitle != cleanTitle || normArtist != cleanArtist) && normTitle.isNotBlank()) {
                    add(RemoteSearchStrategy("normalized_track+artist") {
                        if (normArtist.isNotBlank() && !isUnknownArtist(normArtist)) {
                            lrcLibApiService.searchLyrics(trackName = normTitle, artistName = normArtist)
                        } else {
                            lrcLibApiService.searchLyrics(trackName = normTitle)
                        }
                    })
                }
                // 5. Extracted from separator in title (e.g. "Artist - Title")
                if (extracted != null) {
                    add(RemoteSearchStrategy("extracted_separator") {
                        lrcLibApiService.searchLyrics(trackName = extracted.second, artistName = extracted.first)
                    })
                    add(RemoteSearchStrategy("extracted_separator_reverse") {
                        lrcLibApiService.searchLyrics(trackName = extracted.first, artistName = extracted.second)
                    })
                }
                // 6. Track name only
                if (cleanTitle.isNotBlank()) {
                    add(RemoteSearchStrategy("track_only") {
                        lrcLibApiService.searchLyrics(trackName = cleanTitle)
                    })
                }
            }

            val results = runSearchStrategiesFast(searchStrategies)

            if (results.isNotEmpty()) {
                val bestMatch = rankRemoteLyricsMatches(
                    song = song,
                    responses = results,
                    mode = RemoteLyricsMatchMode.AUTOMATIC
                ).firstOrNull()?.response

                if (bestMatch != null) {
                    val rawSyncedLyrics = bestMatch.syncedLyrics
                    if (!rawSyncedLyrics.isNullOrBlank()) {
                        val parsedLyrics = LyricsUtils.parseLyrics(rawSyncedLyrics).copy(areFromRemote = true)
                        if (parsedLyrics.isValid() && !parsedLyrics.synced.isNullOrEmpty()) {
                            Timber.tag(TAG).d("LRCLIB synchronized lyrics found for: ${song.title}")
                            song.id.toLongOrNull()?.let { songIdLong ->
                                try {
                                    lyricsDao.insert(
                                        LyricsEntity(
                                            songId = songIdLong,
                                            content = rawSyncedLyrics,
                                            isSynced = true,
                                            source = "remote"
                                        )
                                    )
                                } catch (_: NumberFormatException) {
                                    Timber.tag(TAG).w("Skipping database save for non-numeric song ID: ${song.id}. Lyrics will be cached in JSON.")
                                }
                            }
                            return@withContext parsedLyrics
                        }
                    }
                }
            }

            // 2. Fallback: YouTube Music transcript synced lyrics when LRCLIB has no synced lyrics
            val youtubeLyrics = fetchYouTubeTranscriptLyrics(song)
            if (youtubeLyrics != null) {
                return@withContext youtubeLyrics.first
            }

            return@withContext null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Lyrics fetch failed: ${e.message}")
            return@withContext null
        }
    }

    private fun hasLyrics(response: LrcLibResponse): Boolean =
        !response.plainLyrics.isNullOrBlank() || !response.syncedLyrics.isNullOrBlank()

    private fun hasSyncedLyrics(response: LrcLibResponse): Boolean =
        !response.syncedLyrics.isNullOrBlank()

    private fun rankRemoteLyricsMatches(
        song: Song,
        responses: List<LrcLibResponse>,
        mode: RemoteLyricsMatchMode
    ): List<RemoteLyricsMatch> {
        val songDurationSeconds = song.duration / 1000.0

        return responses
            .mapNotNull { response ->
                val score = remoteLyricsMatchScore(
                    song = song,
                    response = response,
                    mode = mode,
                    songDurationSeconds = songDurationSeconds
                ) ?: return@mapNotNull null
                RemoteLyricsMatch(response, score)
            }
            .sortedWith(
                compareByDescending<RemoteLyricsMatch> { it.score }
                    .thenByDescending { hasSyncedLyrics(it.response) }
                    .thenBy { if (songDurationSeconds > 0.0) abs(it.response.duration - songDurationSeconds) else 0.0 }
            )
    }

    private fun remoteLyricsMatchScore(
        song: Song,
        response: LrcLibResponse,
        mode: RemoteLyricsMatchMode,
        songDurationSeconds: Double
    ): Int? {
        if (!hasLyrics(response)) return null
        if (!variantDescriptorsCompatible(song, response)) return null
        val hasSynced = hasSyncedLyrics(response)
        if (mode == RemoteLyricsMatchMode.AUTOMATIC && !hasSynced) return null

        val titleScore = titleMatchScore(song.title, response.name, mode) ?: return null
        val artistScore = artistMatchScore(song.displayArtist, response.artistName)
        if (!isUnknownArtist(song.displayArtist) && artistScore == null) return null

        val durationTolerance = if (songDurationSeconds > 0.0) {
            remoteDurationToleranceSeconds(songDurationSeconds, hasSynced, mode)
        } else {
            0.0
        }
        val durationDiff = if (songDurationSeconds > 0.0 && response.duration > 0.0) {
            abs(response.duration - songDurationSeconds)
        } else {
            0.0
        }

        if (songDurationSeconds > 0.0 && response.duration > 0.0 && durationDiff > durationTolerance) {
            return null
        }

        val durationScore = if (songDurationSeconds > 0.0) {
            (durationTolerance - durationDiff).coerceAtLeast(0.0).toInt()
        } else {
            0
        }
        val syncedScore = if (hasSynced) 10 else 0
        return titleScore + (artistScore ?: 0) + durationScore + syncedScore
    }

    private fun remoteDurationToleranceSeconds(
        songDurationSeconds: Double,
        hasSyncedLyrics: Boolean,
        mode: RemoteLyricsMatchMode
    ): Double {
        return when (mode) {
            RemoteLyricsMatchMode.AUTOMATIC -> {
                if (hasSyncedLyrics) {
                    (songDurationSeconds * 0.05).coerceIn(10.0, 20.0)
                } else {
                    (songDurationSeconds * 0.08).coerceIn(15.0, 35.0)
                }
            }
            RemoteLyricsMatchMode.CANDIDATE -> 25.0
        }
    }

    private fun titleMatchScore(songTitle: String, responseTitle: String, mode: RemoteLyricsMatchMode): Int? {
        val songBase = baseTitleForMatching(songTitle)
        val responseBase = baseTitleForMatching(responseTitle)
        if (songBase.isBlank() || responseBase.isBlank()) return null

        if (songBase == responseBase) return 70

        val songTokens = matchTokens(songBase)
        val responseTokens = matchTokens(responseBase)
        if (songTokens.isEmpty() || responseTokens.isEmpty()) return null

        if (songTokens.size == 1 || responseTokens.size == 1) {
            return if (songTokens == responseTokens) 60 else null
        }

        if (containsWholePhrase(responseBase, songBase) || containsWholePhrase(songBase, responseBase)) {
            return if (mode == RemoteLyricsMatchMode.AUTOMATIC) 58 else 54
        }

        val overlap = songTokens.intersect(responseTokens).size
        val songCoverage = overlap.toDouble() / songTokens.size
        val responseCoverage = overlap.toDouble() / responseTokens.size
        val requiredSongCoverage = if (mode == RemoteLyricsMatchMode.AUTOMATIC) 0.85 else 0.75
        val requiredResponseCoverage = if (mode == RemoteLyricsMatchMode.AUTOMATIC) 0.70 else 0.55

        return if (songCoverage >= requiredSongCoverage && responseCoverage >= requiredResponseCoverage) {
            45
        } else {
            null
        }
    }

    private fun artistMatchScore(songArtist: String, responseArtist: String): Int? {
        if (isUnknownArtist(songArtist)) return 0

        val songBase = normalizeForMatch(songArtist)
        val responseBase = normalizeForMatch(responseArtist)
        if (songBase.isBlank() || responseBase.isBlank()) return null

        if (songBase == responseBase) return 30
        if (containsWholePhrase(responseBase, songBase) || containsWholePhrase(songBase, responseBase)) {
            return 22
        }

        val songTokens = artistTokens(songBase)
        val responseTokens = artistTokens(responseBase)
        if (songTokens.isEmpty() || responseTokens.isEmpty()) return null

        val overlap = songTokens.intersect(responseTokens).size
        val smallerArtistCoverage = overlap.toDouble() / minOf(songTokens.size, responseTokens.size)
        return if (smallerArtistCoverage >= 0.5) 12 else null
    }

    private fun variantDescriptorsCompatible(song: Song, response: LrcLibResponse): Boolean {
        val songVariants = timingVariantTokens(song.title) + timingVariantTokensFromFileName(song)
        val responseVariants = timingVariantTokens(response.name)

        if (songVariants.isEmpty()) {
            return responseVariants.isEmpty()
        }

        return responseVariants == songVariants
    }

    private fun baseTitleForMatching(title: String): String {
        var base = title.replace(Regex("""^\s*\d{1,3}\s*[\._-]\s+"""), "")

        base = BRACKETED_QUALIFIER_REGEX.replace(base) { match ->
            val qualifier = match.groupValues.getOrNull(1).orEmpty()
            if (shouldDropTitleQualifier(qualifier)) " " else " $qualifier "
        }

        var parts = TITLE_SEPARATOR_REGEX.split(base)
        while (parts.size > 1 && shouldDropTitleQualifier(parts.last())) {
            parts = parts.dropLast(1)
        }

        return normalizeForMatch(parts.joinToString(" "))
    }

    private fun shouldDropTitleQualifier(value: String): Boolean {
        val normalized = normalizeForMatch(value)
        if (normalized.isBlank()) return true
        return FEATURE_QUALIFIER_REGEX.containsMatchIn(value) ||
            timingVariantTokens(value).isNotEmpty() ||
            normalized in TITLE_DROP_QUALIFIERS
    }

    private fun timingVariantTokens(value: String): Set<String> {
        val normalized = normalizeForMatch(value)
        if (normalized.isBlank()) return emptySet()

        val tokens = matchTokens(normalized)
        val variants = tokens
            .filter { it in TIMING_VARIANT_KEYWORDS }
            .toMutableSet()

        if (Regex("""\bmash\s+up\b""").containsMatchIn(normalized)) {
            variants += "mashup"
        }
        if ("versus" in tokens || "vs" in tokens) {
            variants += "mashup"
        }

        return variants
    }

    private fun timingVariantTokensFromFileName(song: Song): Set<String> {
        val fileName = songFileName(song)
        if (fileName.isBlank()) return emptySet()

        val variants = BRACKETED_QUALIFIER_REGEX
            .findAll(fileName)
            .flatMap { match -> timingVariantTokens(match.groupValues.getOrNull(1).orEmpty()) }
            .toMutableSet()

        val titleBase = baseTitleForMatching(song.title)
        if (titleBase.isBlank()) return variants

        TITLE_SEPARATOR_REGEX.split(fileName).forEach { part ->
            val normalizedPart = normalizeForMatch(part)
            if (normalizedPart.startsWith("$titleBase ")) {
                variants += timingVariantTokens(normalizedPart.removePrefix(titleBase).trim())
            }
        }

        return variants
    }

    private fun songFileName(song: Song): String {
        if (song.path.isBlank()) return ""
        return runCatching { File(song.path).nameWithoutExtension }.getOrDefault("")
    }

    private fun artistTokens(normalizedArtist: String): Set<String> =
        matchTokens(normalizedArtist)
            .filterNot { it in ARTIST_CONNECTOR_TOKENS }
            .toSet()

    private fun matchTokens(normalizedValue: String): Set<String> =
        normalizedValue
            .split(' ')
            .filter { it.isNotBlank() }
            .toSet()

    private fun containsWholePhrase(haystack: String, needle: String): Boolean {
        if (needle.isBlank()) return false
        return Regex("""(?:^|\s)${Regex.escape(needle)}(?:\s|$)""").containsMatchIn(haystack)
    }

    private fun normalizeForMatch(value: String): String {
        val withoutDiacritics = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")

        return withoutDiacritics
            .replace("&", " and ")
            .replace(Regex("""[\u2019'`]"""), "")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
    }

    private fun isUnknownArtist(value: String): Boolean =
        normalizeForMatch(value) in UNKNOWN_ARTISTS

    private fun parseTtmlTimeToMs(value: String): Long? {
        val raw = value.trim()
        if (raw.isEmpty()) return null

        if (raw.endsWith("s")) {
            val seconds = raw.removeSuffix("s").toDoubleOrNull() ?: return null
            return (seconds * 1000.0).toLong()
        }

        val parts = raw.split(":")
        val secondsPart = parts.lastOrNull()?.toDoubleOrNull() ?: return null
        return when (parts.size) {
            3 -> {
                val hours = parts[0].toLongOrNull() ?: return null
                val minutes = parts[1].toLongOrNull() ?: return null
                (hours * 3_600_000L) + (minutes * 60_000L) + (secondsPart * 1000.0).toLong()
            }
            2 -> {
                val minutes = parts[0].toLongOrNull() ?: return null
                (minutes * 60_000L) + (secondsPart * 1000.0).toLong()
            }
            1 -> (secondsPart * 1000.0).toLong()
            else -> null
        }
    }

    private fun decodeXmlEntities(value: String): String =
        value
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")

    /**
     * Find local .lrc file next to the music file (matching Rhythm)
     */
    private suspend fun findLocalLyricsFile(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        try {
            val songFile = File(song.path)
            val directory = songFile.parentFile ?: return@withContext null
            val songNameWithoutExt = songFile.nameWithoutExtension

            if (directory.exists()) {
                for (extension in LyricsImportSecurity.supportedFileExtensions()) {
                    val lyricsFile = File(directory, "$songNameWithoutExt.$extension")
                    if (!lyricsFile.exists() || !lyricsFile.canRead()) continue

                    val validated = readValidatedLocalLyrics(lyricsFile)
                    if (validated != null) {
                        Timber.tag(TAG).d("===== FOUND LOCAL LYRICS FILE: ${lyricsFile.name} =====")
                        return@withContext validated.parsedLyrics
                    }
                }

                val cleanArtist = song.displayArtist.replace(Regex("[^a-zA-Z0-9]"), "_")
                val cleanTitle = song.title.replace(Regex("[^a-zA-Z0-9]"), "_")

                for (extension in LyricsImportSecurity.supportedFileExtensions()) {
                    val alternativeLyricsFile = File(directory, "${cleanArtist}_${cleanTitle}.$extension")
                    if (!alternativeLyricsFile.exists() || !alternativeLyricsFile.canRead()) continue

                    val validated = readValidatedLocalLyrics(alternativeLyricsFile)
                    if (validated != null) {
                        Timber.tag(TAG).d("===== FOUND LOCAL LYRICS FILE (alt pattern): ${alternativeLyricsFile.name} =====")
                        return@withContext validated.parsedLyrics
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error searching for local lyrics file")
        }
        return@withContext null
    }

    private fun readValidatedLocalLyrics(file: File): com.quietrays.tonarc.utils.ValidatedLyricsImport? {
        return when (val validation = LyricsImportSecurity.validateLocalLyricsFile(file)) {
            is LyricsImportValidationResult.Valid -> validation.value
            is LyricsImportValidationResult.Invalid -> null
        }
    }

    /**
     * Save lyrics to JSON disk cache (matching Rhythm)
     */
    private fun saveLocalLyricsJson(song: Song, lyrics: Lyrics) {
        try {
            val fileName = "${song.id}.json"
            val lyricsDir = File(context.filesDir, "lyrics")
            lyricsDir.mkdirs()

            val wordByWordLyrics = lyrics.synced
                ?.takeIf { lines -> lines.any { !it.words.isNullOrEmpty() } }
                ?.let(::toWordByWordLrc)

            val lyricsData = LyricsData(
                plainLyrics = lyrics.plain?.joinToString("\n"),
                syncedLyrics = lyrics.synced?.joinToString("\n") { "[${formatTimestamp(it.time)}]${it.line}" },
                wordByWordLyrics = wordByWordLyrics
            )

            val file = File(lyricsDir, fileName)
            val json = gson.toJson(lyricsData)
            file.writeText(json)
            Timber.tag(TAG).d("Saved lyrics to JSON cache: ${file.absolutePath}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error saving lyrics to JSON cache: ${e.message}")
        }
    }

    /**
     * Load lyrics from JSON disk cache (matching Rhythm)
     */
    private suspend fun loadLocalLyricsJson(song: Song): Lyrics? {
        try {
            val data = readLyricsJsonCache(song) ?: return null
            if (data.hasLyrics()) {
                val rawLyrics = data.wordByWordLyrics ?: data.syncedLyrics ?: data.plainLyrics
                val parsed = LyricsUtils.parseLyrics(rawLyrics)
                if (parsed.isValid()) {
                    val hasWordTimestamps = parsed.synced?.any { !it.words.isNullOrEmpty() } == true
                    if (!hasWordTimestamps && data.wordByWordLyrics.isNullOrBlank()) {
                        val persistedContent = song.id.toLongOrNull()
                            ?.let { lyricsDao.getLyrics(it)?.content }
                            ?.takeIf { it.isNotBlank() }
                        if (persistedContent != null) {
                            val recovered = LyricsUtils.parseLyrics(persistedContent)
                            val recoveredHasWords = recovered.synced?.any { !it.words.isNullOrEmpty() } == true
                            if (recovered.isValid() && recoveredHasWords) {
                                saveLocalLyricsJson(song, recovered)
                                return recovered
                            }
                        }

                        if (looksLikeFlattenedWordByWordCache(parsed)) {
                            return null
                        }
                    }
                    return parsed
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error reading JSON cache: ${e.message}")
        }
        return null
    }

    private fun formatTimestamp(timeMs: Int): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hundredths = (timeMs % 1000) / 10
        return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, hundredths)
    }

    private fun toWordByWordLrc(lines: List<SyncedLine>): String {
        return lines.joinToString("\n") { line ->
            val linePrefix = "[${formatTimestamp(line.time)}]"
            val words = line.words
            if (words.isNullOrEmpty()) {
                linePrefix + line.line
            } else {
                val wordsPart = words.mapIndexed { index, word ->
                    val separator = if (index > 0 && word.startsNewWord) " " else ""
                    "$separator<${formatTimestamp(word.time)}>${word.word}"
                }
                    .joinToString("")
                linePrefix + wordsPart
            }
        }
    }

    private fun looksLikeFlattenedWordByWordCache(lyrics: Lyrics): Boolean {
        val synced = lyrics.synced ?: return false
        var suspiciousLines = 0

        for (line in synced) {
            val text = line.line
            if (text.isBlank() || text.any { it.isWhitespace() }) continue

            val hasLongLatinRun = Regex("[A-Za-z]{10,}").containsMatchIn(text)
            if (hasLongLatinRun) {
                suspiciousLines += 1
                if (suspiciousLines >= 2) return true
            }
        }

        return false
    }

    /**
     * Load embedded lyrics from audio file metadata
     */
    private suspend fun loadEmbeddedLyricsFromMetadata(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        if (song.contentUriString.isEmpty()) {
            return@withContext null
        }

        return@withContext try {
            val uri = song.contentUriString.toUri()
            val tempFile = createTempFileFromUri(uri)
            if (tempFile == null) {
                LogUtils.w(this@LyricsRepositoryImpl, "Could not create temp file from URI: ${song.contentUriString}")
                return@withContext null
            }

            try {
                ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    val metadata = TagLib.getMetadata(fd.detachFd())
                    val propertyMap = metadata?.propertyMap
                    val parsedLyrics = parseBestEmbeddedLyricsField(propertyMap)
                    if (parsedLyrics != null) {
                        Timber.tag(TAG).d("===== FOUND EMBEDDED LYRICS =====")
                    }
                    parsedLyrics
                }
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            LogUtils.e(this@LyricsRepositoryImpl, e, "Error reading lyrics from file metadata")
            null
        }
    }

    private suspend fun loadStoredLyrics(
        song: Song,
        cacheKey: String,
        includeMemoryCache: Boolean
    ): Pair<Lyrics, String>? = withContext(Dispatchers.IO) {
        song.lyrics
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { rawLyrics ->
                parseStoredLyrics(rawLyrics)?.let { return@withContext it to rawLyrics }
            }

        song.id.toLongOrNull()
            ?.let { lyricsDao.getLyrics(it)?.content }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { rawLyrics ->
                parseStoredLyrics(rawLyrics)?.let { return@withContext it to rawLyrics }
            }

        readLyricsJsonCache(song)
            ?.takeIf { it.hasLyrics() }
            ?.let { data ->
                val rawLyrics = data.wordByWordLyrics ?: data.syncedLyrics ?: data.plainLyrics
                if (!rawLyrics.isNullOrBlank()) {
                    parseStoredLyrics(rawLyrics)?.let { return@withContext it to rawLyrics }
                }
            }

        if (includeMemoryCache) {
            lyricsCache.get(cacheKey)?.let { cached ->
                lyricsToRawContent(cached)?.let { rawLyrics ->
                    return@withContext cached to rawLyrics
                }
            }
        }

        null
    }

    private fun parseStoredLyrics(rawLyrics: String): Lyrics? {
        val parsedLyrics = LyricsUtils.parseLyrics(rawLyrics)
        return parsedLyrics
            .takeIf { it.isValid() }
            ?.copy(areFromRemote = false)
    }

    private fun lyricsToRawContent(lyrics: Lyrics): String? {
        val syncedLyrics = lyrics.synced
        if (!syncedLyrics.isNullOrEmpty()) {
            val hasWordTimestamps = syncedLyrics.any { !it.words.isNullOrEmpty() }
            return if (hasWordTimestamps) {
                toWordByWordLrc(syncedLyrics)
            } else {
                syncedLyrics.joinToString("\n") { line ->
                    "[${formatTimestamp(line.time)}]${line.line}"
                }
            }
        }

        return lyrics.plain
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString("\n")
            ?.takeIf { it.isNotBlank() }
    }

    private fun readLyricsJsonCache(song: Song): LyricsData? {
        val fileName = "${song.id}.json"
        val file = File(context.filesDir, "lyrics/$fileName")
        if (!file.exists()) return null

        val json = file.readText()
        return gson.fromJson(json, LyricsData::class.java)
    }

    override suspend fun fetchFromRemote(song: Song): Result<Pair<Lyrics, String>> = withContext(Dispatchers.IO) {
        try {
            LogUtils.d(this@LyricsRepositoryImpl, "Fetching lyrics from remote for: ${song.title}")

            val cacheKey = generateCacheKey(song.id)
            loadStoredLyrics(song, cacheKey, includeMemoryCache = true)?.let { stored ->
                lyricsCache.put(cacheKey, stored.first)
                LogUtils.d(
                    this@LyricsRepositoryImpl,
                    "Skipping remote lyrics fetch because stored lyrics already exist for: ${song.title}"
                )
                return@withContext Result.success(stored)
            }

            if (!isExternalLyricsEnabled()) {
                return@withContext Result.failure(LyricsException(context.getString(R.string.lyrics_remote_disabled)))
            }

            // 1. First check LRCLIB for synced lyrics
            val searchResult = searchRemote(song)
            if (searchResult.isSuccess) {
                val (_, results) = searchResult.getOrThrow()
                val bestSynced = results.firstOrNull { !it.record.syncedLyrics.isNullOrBlank() }
                if (bestSynced != null) {
                    val rawLyricsToSave = bestSynced.rawLyrics

                    song.id.toLongOrNull()?.let { songIdLong ->
                        try {
                            lyricsDao.insert(
                                LyricsEntity(
                                    songId = songIdLong,
                                    content = rawLyricsToSave,
                                    isSynced = true,
                                    source = "remote"
                                )
                            )
                        } catch (e: NumberFormatException) {
                            Timber.tag(TAG).w("Skipping DB update for non-numeric ID: ${song.id}")
                        }
                    }

                    lyricsCache.put(cacheKey, bestSynced.lyrics)
                    saveLocalLyricsJson(song, bestSynced.lyrics)
                    LogUtils.d(this@LyricsRepositoryImpl, "Fetched and cached remote lyrics for: ${song.title}")

                    return@withContext Result.success(Pair(bestSynced.lyrics, rawLyricsToSave))
                }
            }

            val response = withNetworkRetry(operationName = "lrclib_get_lyrics") {
                lrcLibApiService.getLyrics(
                    trackName = song.title,
                    artistName = song.displayArtist,
                    albumName = song.album,
                    duration = (song.duration / 1000).toInt()
                )
            }

            val exactMatch = response
                ?.let { rankRemoteLyricsMatches(song, listOf(it), RemoteLyricsMatchMode.AUTOMATIC).firstOrNull()?.response }

            if (exactMatch != null && !exactMatch.syncedLyrics.isNullOrBlank()) {
                val rawLyricsToSave = exactMatch.syncedLyrics
                val parsedLyrics = LyricsUtils.parseLyrics(rawLyricsToSave).copy(areFromRemote = true)
                if (parsedLyrics.isValid() && !parsedLyrics.synced.isNullOrEmpty()) {
                    song.id.toLongOrNull()?.let { songIdLong ->
                        try {
                            lyricsDao.insert(
                                LyricsEntity(
                                    songId = songIdLong,
                                    content = rawLyricsToSave,
                                    isSynced = true,
                                    source = "remote"
                                )
                            )
                        } catch (e: NumberFormatException) {
                            Timber.tag(TAG).w("Skipping DB update for non-numeric ID in fallback: ${song.id}")
                        }
                    }

                    lyricsCache.put(cacheKey, parsedLyrics)
                    saveLocalLyricsJson(song, parsedLyrics)
                    LogUtils.d(this@LyricsRepositoryImpl, "Fetched and cached remote lyrics (exact match) for: ${song.title}")

                    return@withContext Result.success(Pair(parsedLyrics, rawLyricsToSave))
                }
            }

            // 2. Fallback to YouTube Music synchronized transcript
            val youtubeLyrics = fetchYouTubeTranscriptLyrics(song)
            if (youtubeLyrics != null) {
                lyricsCache.put(cacheKey, youtubeLyrics.first)
                LogUtils.d(this@LyricsRepositoryImpl, "Fetched and cached YouTube transcript synced lyrics for: ${song.title}")
                return@withContext Result.success(youtubeLyrics)
            }

            // 3. If no synced lyrics found from YouTube or LRCLIB, fall back to plain lyrics from LRCLIB if available
            val plainCandidate = if (searchResult.isSuccess) {
                searchResult.getOrNull()?.second?.firstOrNull()
            } else null

            if (plainCandidate != null) {
                val rawLyricsToSave = plainCandidate.rawLyrics
                song.id.toLongOrNull()?.let { songIdLong ->
                    try {
                        lyricsDao.insert(
                            LyricsEntity(
                                songId = songIdLong,
                                content = rawLyricsToSave,
                                isSynced = false,
                                source = "remote"
                            )
                        )
                    } catch (e: NumberFormatException) {
                        Timber.tag(TAG).w("Skipping DB update for non-numeric ID: ${song.id}")
                    }
                }

                lyricsCache.put(cacheKey, plainCandidate.lyrics)
                saveLocalLyricsJson(song, plainCandidate.lyrics)
                LogUtils.d(this@LyricsRepositoryImpl, "Fetched and cached plain remote lyrics for: ${song.title}")

                return@withContext Result.success(Pair(plainCandidate.lyrics, rawLyricsToSave))
            }

            val exactPlain = exactMatch?.plainLyrics
            if (exactPlain != null) {
                val parsedLyrics = LyricsUtils.parseLyrics(exactPlain).copy(areFromRemote = true)
                if (parsedLyrics.isValid()) {
                    song.id.toLongOrNull()?.let { songIdLong ->
                        try {
                            lyricsDao.insert(
                                LyricsEntity(
                                    songId = songIdLong,
                                    content = exactPlain,
                                    isSynced = false,
                                    source = "remote"
                                )
                            )
                        } catch (e: NumberFormatException) {
                            Timber.tag(TAG).w("Skipping DB update for non-numeric ID: ${song.id}")
                        }
                    }

                    lyricsCache.put(cacheKey, parsedLyrics)
                    saveLocalLyricsJson(song, parsedLyrics)
                    LogUtils.d(this@LyricsRepositoryImpl, "Fetched and cached plain remote lyrics (exact match) for: ${song.title}")

                    return@withContext Result.success(Pair(parsedLyrics, exactPlain))
                }
            }

            LogUtils.d(this@LyricsRepositoryImpl, "No lyrics found remotely for: ${song.title}")
            Result.failure(NoLyricsFoundException())
        } catch (e: Exception) {
            LogUtils.e(this@LyricsRepositoryImpl, e, "Error fetching lyrics from remote")
            when {
                e is HttpException && e.code() == 404 -> Result.failure(NoLyricsFoundException())
                e is SocketTimeoutException -> Result.failure(LyricsException(context.getString(R.string.lyrics_fetch_timeout), e))
                e is UnknownHostException -> Result.failure(LyricsException(context.getString(R.string.lyrics_network_error), e))
                e is IOException -> Result.failure(LyricsException(context.getString(R.string.lyrics_network_error), e))
                e is HttpException -> Result.failure(LyricsException(context.getString(R.string.lyrics_server_error, e.code()), e))
                else -> Result.failure(LyricsException(context.getString(R.string.failed_to_fetch_lyrics_from_remote), e))
            }
        }
    }

    override suspend fun searchRemote(song: Song): Result<Pair<String, List<LyricsSearchResult>>> = withContext(Dispatchers.IO) {
        try {
            if (!isExternalLyricsEnabled()) {
                return@withContext Result.failure(LyricsException(context.getString(R.string.lyrics_remote_disabled)))
            }

            LogUtils.d(this@LyricsRepositoryImpl, "Searching remote for lyrics for: ${song.title} by ${song.displayArtist}")

            val combinedQuery = "${song.title} ${song.displayArtist}"
            val cleanTitle = song.title.trim()
            val cleanArtist = song.displayArtist.trim()

            val strategies = buildList {
                add(RemoteSearchStrategy("query+artist") {
                    lrcLibApiService.searchLyrics(query = combinedQuery, artistName = cleanArtist)
                })
                add(RemoteSearchStrategy("track+artist") {
                    lrcLibApiService.searchLyrics(trackName = cleanTitle, artistName = cleanArtist)
                })
                
                val smartTitle = cleanTitleSmart(cleanTitle)
                if (smartTitle != cleanTitle && smartTitle.isNotBlank()) {
                    LogUtils.d(this@LyricsRepositoryImpl, "Adding smart search strategy for: '$smartTitle' (orig: '$cleanTitle')")
                    add(RemoteSearchStrategy("smart_track_only") {
                        lrcLibApiService.searchLyrics(trackName = smartTitle)
                    })
                }

                add(RemoteSearchStrategy("track_only") {
                    lrcLibApiService.searchLyrics(trackName = cleanTitle)
                })
                add(RemoteSearchStrategy("query_title_only") {
                    lrcLibApiService.searchLyrics(query = cleanTitle)
                })
            }

            val uniqueResults = runSearchStrategiesFast(strategies)

            if (uniqueResults.isNotEmpty()) {
                val rankedMatches = rankRemoteLyricsMatches(
                    song = song,
                    responses = uniqueResults,
                    mode = RemoteLyricsMatchMode.CANDIDATE
                )
                val results = rankedMatches.mapNotNull { match ->
                    val response = match.response
                    val rawLyrics = response.syncedLyrics ?: response.plainLyrics ?: return@mapNotNull null
                    val parsedLyrics = LyricsUtils.parseLyrics(rawLyrics).copy(areFromRemote = true)
                    if (!parsedLyrics.isValid()) {
                        LogUtils.w(this@LyricsRepositoryImpl, "Parsed lyrics are empty for: ${song.title}")
                        return@mapNotNull null
                    }
                    val hasSynced = !response.syncedLyrics.isNullOrEmpty()
                    LogUtils.d(this@LyricsRepositoryImpl, "  Found: ${response.name} by ${response.artistName} (synced: $hasSynced)")
                    LyricsSearchResult(response, parsedLyrics, rawLyrics)
                }

                if (results.isNotEmpty()) {
                    val syncedCount = results.count { !it.record.syncedLyrics.isNullOrEmpty() }
                    LogUtils.d(this@LyricsRepositoryImpl, "Found ${results.size} lyrics for: ${song.title} ($syncedCount with synced)")
                    Result.success(Pair(combinedQuery, results))
                } else {
                    LogUtils.d(this@LyricsRepositoryImpl, "No matching lyrics found for: ${song.title}")
                    Result.failure(NoLyricsFoundException(combinedQuery))
                }
            } else {
                LogUtils.d(this@LyricsRepositoryImpl, "No lyrics found remotely for: ${song.title}")
                Result.failure(NoLyricsFoundException(combinedQuery))
            }
        } catch (e: Exception) {
            LogUtils.e(this@LyricsRepositoryImpl, e, "Error searching remote for lyrics")
            when {
                e is SocketTimeoutException -> Result.failure(LyricsException(context.getString(R.string.lyrics_fetch_timeout), e))
                e is UnknownHostException -> Result.failure(LyricsException(context.getString(R.string.lyrics_network_error), e))
                e is IOException -> Result.failure(LyricsException(context.getString(R.string.lyrics_network_error), e))
                e is HttpException -> Result.failure(LyricsException(context.getString(R.string.lyrics_server_error, e.code()), e))
                else -> Result.failure(LyricsException(context.getString(R.string.failed_to_search_for_lyrics), e))
            }
        }
    }

    override suspend fun searchRemoteByQuery(title: String, artist: String?): Result<Pair<String, List<LyricsSearchResult>>> = withContext(Dispatchers.IO) {
        try {
            if (!isExternalLyricsEnabled()) {
                return@withContext Result.failure(LyricsException(context.getString(R.string.lyrics_remote_disabled)))
            }

            val cleanTitle = title.trim()
            val cleanArtist = artist?.trim()?.takeIf { it.isNotBlank() }
            val query = listOfNotNull(
                cleanTitle.takeIf { it.isNotBlank() },
                cleanArtist
            ).joinToString(" ")

            LogUtils.d(this@LyricsRepositoryImpl, "Manual lyrics search: title=$title, artist=$artist")

            val strategies = buildList {
                add(RemoteSearchStrategy("manual_query") { lrcLibApiService.searchLyrics(query = query) })
                if (!cleanArtist.isNullOrBlank()) {
                    add(
                        RemoteSearchStrategy("manual_track+artist") {
                            lrcLibApiService.searchLyrics(trackName = cleanTitle, artistName = cleanArtist)
                        }
                    )
                }
            }

            val responses = runSearchStrategiesFast(strategies)

            if (responses.isEmpty()) {
                return@withContext Result.failure(NoLyricsFoundException(query))
            }

            val results = responses.mapNotNull { response ->
                val rawLyrics = response.syncedLyrics ?: response.plainLyrics ?: return@mapNotNull null
                val parsed = LyricsUtils.parseLyrics(rawLyrics).copy(areFromRemote = true)
                if (!parsed.isValid()) return@mapNotNull null

                LyricsSearchResult(response, parsed, rawLyrics, source = "LRCLIB")
            }.toMutableList()

            // Also check YouTube Music transcript for the search query
            try {
                val ytmResults = innertubeApiService.search(query, com.quietrays.tonarc.data.network.youtube.InnertubeApiService.YTM_FILTER_SONGS)
                for (ytmTrack in ytmResults.songs.take(2)) {
                    val transcript = innertubeApiService.getTranscriptLyrics(ytmTrack.videoId)
                    if (!transcript.isNullOrBlank()) {
                        val parsedYtm = LyricsUtils.parseLyrics(transcript).copy(areFromRemote = true)
                        if (parsedYtm.isValid() && !parsedYtm.synced.isNullOrEmpty()) {
                            val dummyRecord = LrcLibResponse(
                                id = -Math.abs(ytmTrack.videoId.hashCode()),
                                name = ytmTrack.title,
                                artistName = ytmTrack.artist,
                                albumName = ytmTrack.album ?: "YouTube Music",
                                duration = ytmTrack.durationSeconds.toDouble(),
                                plainLyrics = null,
                                syncedLyrics = transcript
                            )
                            results.add(LyricsSearchResult(dummyRecord, parsedYtm, transcript, source = "YouTube"))
                        }
                    }
                }
            } catch (e: Exception) {
                LogUtils.w(this@LyricsRepositoryImpl, "YouTube transcript search query failed: ${e.message}")
            }

            results.sortByDescending { !it.record.syncedLyrics.isNullOrEmpty() }

            if (results.isEmpty()) {
                Result.failure(NoLyricsFoundException(query))
            } else {
                Result.success(Pair(query, results))
            }
        } catch (e: Exception) {
            LogUtils.e(this@LyricsRepositoryImpl, e, "Manual search failed")
            Result.failure(LyricsException(context.getString(R.string.failed_to_search_for_lyrics), e)
            )
        }
    }

    override suspend fun updateLyrics(songId: Long, lyricsContent: String): Unit = withContext(Dispatchers.IO) {
        LogUtils.d(this@LyricsRepositoryImpl, "Updating lyrics for songId: $songId")

        val parsedLyrics = LyricsUtils.parseLyrics(lyricsContent)
        if (!parsedLyrics.isValid()) {
            LogUtils.w(this@LyricsRepositoryImpl, "Attempted to save empty lyrics for songId: $songId")
            return@withContext
        }

        lyricsDao.insert(
             com.quietrays.tonarc.data.database.LyricsEntity(
                 songId = songId,
                 content = lyricsContent,
                 isSynced = parsedLyrics.synced?.isNotEmpty() == true,
                 source = "manual"
             )
        )

        val cacheKey = generateCacheKey(songId.toString())
        lyricsCache.put(cacheKey, parsedLyrics)
        LogUtils.d(this@LyricsRepositoryImpl, "Updated and cached lyrics for songId: $songId")
    }

    override suspend fun resetLyrics(songId: Long): Unit = withContext(Dispatchers.IO) {
        LogUtils.d(this, "Resetting lyrics for songId: $songId")
        val cacheKey = generateCacheKey(songId.toString())
        lyricsCache.remove(cacheKey)
        try {
            lyricsDao.deleteLyrics(songId)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Error removing lyrics from DB for ID: $songId")
        }
        
        try {
            val file = File(context.filesDir, "lyrics/${songId}.json")
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Timber.tag(TAG).w("Error deleting JSON cache: ${e.message}")
        }
    }

    override suspend fun resetAllLyrics(): Unit = withContext(Dispatchers.IO) {
        LogUtils.d(this, "Resetting all lyrics")
        lyricsCache.evictAll()
        lyricsDao.deleteAll()
        
        try {
            val lyricsDir = File(context.filesDir, "lyrics")
            if (lyricsDir.exists()) {
                lyricsDir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w("Error clearing JSON cache: ${e.message}")
        }
    }

    override suspend fun scanAndAssignLocalLrcFiles(
        songs: List<Song>,
        onProgress: suspend (current: Int, total: Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        LogUtils.d(this@LyricsRepositoryImpl, "Starting bulk scan for .lrc files for ${songs.size} songs")
        val updatedCount = AtomicInteger(0)
        val processedCount = AtomicInteger(0)
        val total = songs.size

        val idsWithPersistedLyrics = songs
            .mapNotNull { it.id.toLongOrNull() }
            .chunked(900)
            .flatMap { chunk -> lyricsDao.getSongIdsWithLyrics(chunk) }
            .toHashSet()

        val songsToScan = songs.filter { song ->
            val songId = song.id.toLongOrNull()
            song.lyrics.isNullOrBlank() && (songId == null || songId !in idsWithPersistedLyrics)
        }
        val skippedCount = total - songsToScan.size
        processedCount.addAndGet(skippedCount)
        
        LogUtils.d(this@LyricsRepositoryImpl, "Skipping $skippedCount songs that already have lyrics. Scanning ${songsToScan.size} songs.")
        
        onProgress(processedCount.get(), total)
        
        if (songsToScan.isEmpty()) {
            return@withContext 0
        }

        val semaphore = Semaphore(8)

        coroutineScope {
            songsToScan.map { song ->
                async {
                    semaphore.withPermit {
                        try {
                            val songFile = File(song.path)
                            val directory = songFile.parentFile
                            
                            if (directory != null && directory.exists()) {
                                var foundFile: File? = null
                                
                                for (extension in LyricsImportSecurity.supportedFileExtensions()) {
                                    val exactMatch = File(directory, "${songFile.nameWithoutExtension}.$extension")
                                    if (exactMatch.exists() && exactMatch.canRead()) {
                                        foundFile = exactMatch
                                        break
                                    }
                                }
                                
                                if (foundFile == null) {
                                    val cleanArtist = song.displayArtist.replace(Regex("[^a-zA-Z0-9]"), "_")
                                    val cleanTitle = song.title.replace(Regex("[^a-zA-Z0-9]"), "_")
                                    for (extension in LyricsImportSecurity.supportedFileExtensions()) {
                                        val altMatch = File(directory, "${cleanArtist}_${cleanTitle}.$extension")
                                        if (altMatch.exists() && altMatch.canRead()) {
                                            foundFile = altMatch
                                            break
                                        }
                                    }
                                }
                                
                                if (foundFile != null) {
                                    val validated = readValidatedLocalLyrics(foundFile)
                                    if (validated != null) {
                                        try {
                                            lyricsDao.insert(
                                                 com.quietrays.tonarc.data.database.LyricsEntity(
                                                     songId = song.id.toLong(),
                                                     content = validated.sanitizedContent,
                                                     isSynced = validated.parsedLyrics.synced?.isNotEmpty() == true,
                                                     source = "local_file"
                                                 )
                                            )
                                            updatedCount.incrementAndGet()
                                            LogUtils.d(this@LyricsRepositoryImpl, "Auto-assigned lyrics from ${foundFile.name}")
                                        } catch (e: Exception) {
                                            Timber.tag(TAG).w(e, "Skipping DB update for ID in scanner: ${song.id}")
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Timber.tag(TAG).w("Error scanning lyrics for ${song.title}: ${e.message}")
                        }
                        
                        val current = processedCount.incrementAndGet()
                        if (current % 20 == 0 || current == total) {
                            onProgress(current, total)
                        }
                    }
                }
            }.awaitAll()
        }
        
        LogUtils.d(this@LyricsRepositoryImpl, "Bulk scan complete. Updated ${updatedCount.get()} songs.")
        return@withContext updatedCount.get()
    }

    override fun clearCache() {
        LogUtils.d(this, "Clearing lyrics in-memory cache")
        lyricsCache.evictAll()
    }

    private fun generateCacheKey(songId: String): String = songId

    private fun createTempFileFromUri(uri: Uri): File? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) cursor.getString(nameIndex) else "temp_audio"
                    } else "temp_audio"
                } ?: "temp_audio"

                val tempFile = File.createTempFile("lyrics_", "_$fileName", context.cacheDir)
                FileOutputStream(tempFile).use { output ->
                    inputStream.copyTo(output)
                }
                tempFile
            }
        } catch (e: Exception) {
            LogUtils.e(this, e, "Error creating temp file from URI")
            null
        }
    }

    private fun cleanTitleSmart(title: String): String {
        var cleaned = title.replace(Regex("^[\\d\\s.\\-]+"), "")
        
        val splitRegex = Regex("[-()]")
        cleaned = cleaned.split(splitRegex).firstOrNull() ?: cleaned
        
        return cleaned.trim()
    }
}

data class LyricsSearchResult(
    val record: LrcLibResponse,
    val lyrics: Lyrics,
    val rawLyrics: String,
    val source: String = "LRCLIB"
)

data class NoLyricsFoundException(val query: String? = null) : Exception()

class LyricsException(message: String, cause: Throwable? = null) : Exception(message, cause)
