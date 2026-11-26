package com.quietrays.tonarc.desktop.data

import com.quietrays.tonarc.data.network.youtube.InnertubeAlbum
import com.quietrays.tonarc.data.network.youtube.InnertubeArtist
import com.quietrays.tonarc.data.network.youtube.InnertubeBrowseSection
import com.quietrays.tonarc.data.network.youtube.InnertubeParser
import com.quietrays.tonarc.data.network.youtube.InnertubePlaylist
import com.quietrays.tonarc.data.network.youtube.InnertubeSearchResult
import com.quietrays.tonarc.data.network.youtube.InnertubeStreamInfo
import com.quietrays.tonarc.data.network.youtube.InnertubeTrack
import com.quietrays.tonarc.data.network.youtube.NewPipeDownloader
import com.quietrays.tonarc.desktop.model.DesktopAlbum
import com.quietrays.tonarc.desktop.model.DesktopArtist
import com.quietrays.tonarc.desktop.model.DesktopPlaylist
import com.quietrays.tonarc.desktop.model.DesktopTrack
import com.quietrays.tonarc.desktop.model.HomeShelf
import com.quietrays.tonarc.desktop.model.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pure-JVM YouTube Music Innertube + NewPipeExtractor client for Tonarc Desktop.
 * Reuses shared InnertubeModels, InnertubeParser, and NewPipeDownloader from :app.
 */
class DesktopYouTubeClient(
    baseOkHttpClient: OkHttpClient,
    initialVisitorData: String? = null,
    initialCookies: String? = null,
    private val onVisitorDataUpdated: (String) -> Unit = {}
) {
    companion object {
        private const val BASE_URL = "https://music.youtube.com/youtubei/v1"
        private const val USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        private const val CLIENT_NAME = "WEB_REMIX"
        private const val CLIENT_VERSION = "1.20260908.14.00"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        const val YTM_FILTER_SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
        const val YTM_FILTER_ALBUMS = "EgWKAQIBAWoKEAkQBRAKEAMQBA%3D%3D"
        const val YTM_FILTER_ARTISTS = "EgWKAQIgAWoKEAkQBRAKEAMQBA%3D%3D"
        const val YTM_FILTER_PLAYLISTS = "EgWKAQIoAWoKEAkQBRAKEAMQBA%3D%3D"
    }

    private data class CachedStreamUrl(val url: String, val expiresAtMs: Long)

    private val okHttpClient: OkHttpClient = baseOkHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val newPipeInitialized = AtomicBoolean(false)
    private val streamCache = ConcurrentHashMap<String, CachedStreamUrl>()

    @Volatile
    var visitorData: String? = initialVisitorData

    @Volatile
    var authCookies: String? = initialCookies

    @Volatile
    private var cachedSignatureTimestamp: Int? = null

    @Volatile
    private var lastStsFetchTime: Long = 0L

    private fun ensureNewPipeInitialized() {
        if (newPipeInitialized.compareAndSet(false, true)) {
            runCatching { NewPipe.init(NewPipeDownloader(okHttpClient)) }
        }
    }

    private fun extractCookieValue(cookies: String, name: String): String? {
        val regex = Regex("(?:^|;\\s*)${Regex.escape(name)}=([^;]+)")
        return regex.find(cookies)?.groupValues?.get(1)?.trim()
    }

    private fun generateSapisidHash(sapisid: String, origin: String = "https://music.youtube.com"): String {
        val timestamp = System.currentTimeMillis() / 1000
        val toHash = "$timestamp $sapisid $origin"
        val digest = MessageDigest.getInstance("SHA-1").digest(toHash.toByteArray(Charsets.UTF_8))
        val hash = digest.joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestamp}_$hash"
    }

    private fun extractVisitorData(jsonString: String) {
        runCatching {
            val vData = JSONObject(jsonString)
                .optJSONObject("responseContext")
                ?.optString("visitorData")
            if (!vData.isNullOrBlank() && vData != visitorData) {
                visitorData = vData
                onVisitorDataUpdated(vData)
            }
        }
    }

    private fun createBaseContext(): JSONObject = JSONObject().apply {
        put("client", JSONObject().apply {
            put("clientName", CLIENT_NAME)
            put("clientVersion", CLIENT_VERSION)
            put("hl", "en")
            put("gl", "US")
            visitorData?.let { put("visitorData", it) }
        })
    }

    private fun createAndroidContext(): JSONObject = JSONObject().apply {
        put("client", JSONObject().apply {
            put("clientName", "ANDROID_MUSIC")
            put("clientVersion", "6.42.52")
            put("androidSdkVersion", 34)
            put("hl", "en")
            put("gl", "US")
            visitorData?.let { put("visitorData", it) }
        })
    }

    private fun buildRequest(
        endpoint: String,
        bodyJson: JSONObject,
        clientVersion: String = CLIENT_VERSION,
        clientHeaderName: String = "67",
        userAgent: String = USER_AGENT
    ): Request {
        val url = "$BASE_URL/$endpoint?prettyPrint=false"
        val builder = Request.Builder()
            .url(url)
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", userAgent)
            .header("Referer", "https://music.youtube.com/")
            .header("Origin", "https://music.youtube.com")
            .header("X-YouTube-Client-Name", clientHeaderName)
            .header("X-YouTube-Client-Version", clientVersion)

        visitorData?.takeIf { it.isNotBlank() }?.let {
            builder.header("X-YouTube-Visitor-Data", it)
        }

        authCookies?.takeIf { it.isNotBlank() }?.let { cookies ->
            builder.header("Cookie", cookies)
            val sapisid = extractCookieValue(cookies, "SAPISID")
                ?: extractCookieValue(cookies, "__Secure-3PAPISID")
                ?: extractCookieValue(cookies, "__Secure-1PAPISID")
            if (!sapisid.isNullOrBlank()) {
                builder.header("Authorization", generateSapisidHash(sapisid))
            }
        }

        return builder.build()
    }

    private fun getSignatureTimestamp(): Int? {
        val now = System.currentTimeMillis()
        if (cachedSignatureTimestamp != null && now - lastStsFetchTime < 6 * 3600 * 1000L) {
            return cachedSignatureTimestamp
        }
        runCatching {
            val html = okHttpClient.newCall(
                Request.Builder().url("https://www.youtube.com/").header("User-Agent", USER_AGENT).build()
            ).execute().use { it.body?.string() } ?: return cachedSignatureTimestamp
            val jsMatch = Regex("(/s/player/[^\"]+player_ias\\.vflset/[^\"]+base\\.js)").find(html)
                ?: Regex("\"jsUrl\":\"([^\"]+)\"").find(html)
            val jsPath = jsMatch?.groupValues?.get(1) ?: return cachedSignatureTimestamp
            val jsUrl = if (jsPath.startsWith("/")) "https://www.youtube.com$jsPath" else jsPath
            com.quietrays.tonarc.data.network.youtube.InnertubeApiService.cachedPlayerJsUrl = jsUrl
            val jsCode = okHttpClient.newCall(
                Request.Builder().url(jsUrl).header("User-Agent", USER_AGENT).build()
            ).execute().use { it.body?.string() } ?: return cachedSignatureTimestamp
            val sts = Regex("signatureTimestamp:([0-9]+)").find(jsCode)?.groupValues?.get(1)?.toIntOrNull()
            if (sts != null) {
                cachedSignatureTimestamp = sts
                lastStsFetchTime = now
            }
        }
        return cachedSignatureTimestamp
    }

    suspend fun getHomeShelves(): List<HomeShelf> = withContext(Dispatchers.IO) {
        val sections = getBrowse("FEmusic_home")
        sections.map { it.toHomeShelf() }
    }

    suspend fun getBrowse(browseId: String = "FEmusic_home"): List<InnertubeBrowseSection> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("context", createBaseContext())
                put("browseId", browseId)
            }
            okHttpClient.newCall(buildRequest("browse", body)).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val text = resp.body?.string() ?: return@withContext emptyList()
                extractVisitorData(text)
                InnertubeParser.parseBrowseSections(text)
            }
        }.getOrDefault(emptyList())
    }

    suspend fun search(
        query: String,
        params: String? = YTM_FILTER_SONGS
    ): InnertubeSearchResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext InnertubeSearchResult(query)
        runCatching {
            val body = JSONObject().apply {
                put("context", createBaseContext())
                put("query", query)
                if (!params.isNullOrBlank()) put("params", params)
            }
            okHttpClient.newCall(buildRequest("search", body)).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext InnertubeSearchResult(query)
                val text = resp.body?.string() ?: return@withContext InnertubeSearchResult(query)
                extractVisitorData(text)
                InnertubeParser.parseSearchResults(query, text)
            }
        }.getOrDefault(InnertubeSearchResult(query))
    }

    suspend fun getAlbum(browseId: String): DesktopAlbum? = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("context", createBaseContext())
                put("browseId", browseId)
            }
            okHttpClient.newCall(buildRequest("browse", body)).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val text = resp.body?.string() ?: return@withContext null
                extractVisitorData(text)
                val pair = InnertubeParser.parseAlbumDetails(browseId, text) ?: return@withContext null
                pair.first.toDesktopAlbum(pair.second.map { it.toDesktopTrack() })
            }
        }.getOrNull()
    }

    suspend fun getArtist(browseId: String): DesktopArtist? = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("context", createBaseContext())
                put("browseId", browseId)
            }
            okHttpClient.newCall(buildRequest("browse", body)).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val text = resp.body?.string() ?: return@withContext null
                extractVisitorData(text)
                val pair = InnertubeParser.parseArtistDetails(browseId, text) ?: return@withContext null
                pair.first.toDesktopArtist(pair.second.map { it.toDesktopTrack() })
            }
        }.getOrNull()
    }

    suspend fun getPlaylist(playlistId: String): Pair<DesktopPlaylist, List<DesktopTrack>>? = withContext(Dispatchers.IO) {
        val actualBrowseId = if (!playlistId.startsWith("VL") && playlistId.startsWith("PL")) "VL$playlistId" else playlistId
        runCatching {
            val body = JSONObject().apply {
                put("context", createBaseContext())
                put("browseId", actualBrowseId)
            }
            okHttpClient.newCall(buildRequest("browse", body)).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val text = resp.body?.string() ?: return@withContext null
                extractVisitorData(text)
                val pair = InnertubeParser.parsePlaylistDetails(playlistId, text) ?: return@withContext null
                pair.first.toDesktopPlaylist() to pair.second.map { it.toDesktopTrack() }
            }
        }.getOrNull()
    }

    suspend fun getRadioTracks(videoId: String): List<DesktopTrack> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("context", createBaseContext())
                put("videoId", videoId)
                put("playlistId", "RDAMVM$videoId")
                put("isAudioOnly", true)
                put("enablePersistentPlaylistPanel", true)
            }
            okHttpClient.newCall(buildRequest("next", body)).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val text = resp.body?.string() ?: return@withContext emptyList()
                extractVisitorData(text)
                InnertubeParser.parseRadioTracks(text).map { it.toDesktopTrack() }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun getTranscriptLyrics(videoId: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("context", createBaseContext())
                put("videoId", videoId)
                put("params", "CAESAhAB")
            }
            okHttpClient.newCall(buildRequest("get_transcript", body)).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val text = resp.body?.string() ?: return@withContext null
                extractVisitorData(text)
                InnertubeParser.parseTranscriptLyrics(text)
            }
        }.getOrNull()
    }

    suspend fun resolveAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        streamCache[videoId]?.takeIf { it.expiresAtMs > now + 60_000L }?.let { return@withContext it.url }

        // 1. Primary: NewPipeExtractor with n-sig & cipher deobfuscation
        ensureNewPipeInitialized()
        val extractedUrl = runCatching {
            val watchUrl = "https://www.youtube.com/watch?v=$videoId"
            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, watchUrl)
            val audioStreams = streamInfo.audioStreams.filter { !it.content.isNullOrBlank() }
            val best = audioStreams.maxByOrNull { stream ->
                val rawBitrate = maxOf(stream.bitrate, stream.averageBitrate, 0)
                val isOpus = stream.format == MediaFormat.WEBMA_OPUS ||
                    stream.codec?.contains("opus", ignoreCase = true) == true
                val isHighQualityAac = rawBitrate >= 256_000
                when {
                    isHighQualityAac -> (rawBitrate * 1.05).toInt()
                    isOpus -> (rawBitrate * 1.25).toInt()
                    else -> rawBitrate
                }
            } ?: audioStreams.firstOrNull()
            best?.content
        }.getOrNull()

        if (!extractedUrl.isNullOrBlank()) {
            streamCache[videoId] = CachedStreamUrl(extractedUrl, now + 3 * 3600 * 1000L)
            return@withContext extractedUrl
        }

        // 2. Fallback: Direct Innertube player endpoint (WEB_REMIX then ANDROID_MUSIC)
        val innertubeInfo = getInnertubeStreamInfo(videoId)
        val fallbackUrl = innertubeInfo?.highestBitrateOpusUrl
            ?: innertubeInfo?.highestBitrateAacUrl
            ?: innertubeInfo?.selectedFormatUrl
        if (!fallbackUrl.isNullOrBlank()) {
            streamCache[videoId] = CachedStreamUrl(fallbackUrl, innertubeInfo?.expireAtTimestampMs ?: (now + 3 * 3600 * 1000L))
        }
        fallbackUrl
    }

    private fun getInnertubeStreamInfo(videoId: String): InnertubeStreamInfo? {
        val sts = getSignatureTimestamp()
        runCatching {
            val body = JSONObject().apply {
                put("context", createBaseContext())
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
                if (sts != null) {
                    put("playbackContext", JSONObject().apply {
                        put("contentPlaybackContext", JSONObject().apply {
                            put("signatureTimestamp", sts)
                            put("html5Preference", "HTML5_PREF_WANTS")
                        })
                    })
                }
            }
            okHttpClient.newCall(buildRequest("player", body)).execute().use { resp ->
                val text = resp.body?.string()
                if (text != null) {
                    extractVisitorData(text)
                    InnertubeParser.parsePlayerResponse(text)?.let { info ->
                        if (info.selectedFormatUrl != null) return info
                    }
                }
            }
        }

        return runCatching {
            val body = JSONObject().apply {
                put("context", createAndroidContext())
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }
            okHttpClient.newCall(
                buildRequest(
                    endpoint = "player",
                    bodyJson = body,
                    clientVersion = "6.42.52",
                    clientHeaderName = "21",
                    userAgent = "com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14; en_US) gzip"
                )
            ).execute().use { resp ->
                val text = resp.body?.string() ?: return null
                extractVisitorData(text)
                InnertubeParser.parsePlayerResponse(text)
            }
        }.getOrNull()
    }
}

fun InnertubeTrack.toDesktopTrack(): DesktopTrack {
    val upscaleThumb = thumbnailUri?.replace(Regex("=w\\d+-h\\d+[^&]*"), "=w544-h544-l90-rj")
    return DesktopTrack(
        id = "yt:$videoId",
        title = title,
        artist = artist,
        album = album ?: "YouTube Music",
        durationMs = durationSeconds * 1000L,
        source = TrackSource.YOUTUBE,
        videoId = videoId,
        artworkUrl = upscaleThumb ?: thumbnailUri,
        mimeType = "OPUS"
    )
}

fun InnertubeAlbum.toDesktopAlbum(tracks: List<DesktopTrack> = emptyList()): DesktopAlbum {
    val upscaleThumb = thumbnailUri?.replace(Regex("=w\\d+-h\\d+[^&]*"), "=w544-h544-l90-rj")
    return DesktopAlbum(
        id = "yt_album:$browseId",
        title = title,
        artist = artist,
        year = year,
        artworkModel = upscaleThumb ?: thumbnailUri,
        trackCount = trackCount.takeIf { it > 0 } ?: tracks.size,
        source = TrackSource.YOUTUBE,
        browseId = browseId,
        tracks = tracks
    )
}

fun InnertubeArtist.toDesktopArtist(tracks: List<DesktopTrack> = emptyList()): DesktopArtist {
    val upscaleThumb = thumbnailUri?.replace(Regex("=w\\d+-h\\d+[^&]*"), "=w544-h544-l90-rj")
    return DesktopArtist(
        id = "yt_artist:$browseId",
        name = name,
        artworkModel = upscaleThumb ?: thumbnailUri,
        subtitle = subscribers,
        source = TrackSource.YOUTUBE,
        browseId = browseId,
        tracks = tracks
    )
}

fun InnertubePlaylist.toDesktopPlaylist(): DesktopPlaylist {
    val upscaleThumb = thumbnailUri?.replace(Regex("=w\\d+-h\\d+[^&]*"), "=w544-h544-l90-rj")
    return DesktopPlaylist(
        id = "yt_pl:$playlistId",
        name = title,
        description = author.orEmpty(),
        artworkUrl = upscaleThumb ?: thumbnailUri,
        isYouTube = true,
        browseId = playlistId
    )
}

fun InnertubeBrowseSection.toHomeShelf(): HomeShelf = HomeShelf(
    title = title,
    subtitle = subtitle,
    tracks = tracks.map { it.toDesktopTrack() },
    albums = albums.map { it.toDesktopAlbum() },
    playlists = playlists.map { it.toDesktopPlaylist() }
)
