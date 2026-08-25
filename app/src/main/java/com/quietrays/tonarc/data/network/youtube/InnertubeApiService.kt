package com.quietrays.tonarc.data.network.youtube

import com.quietrays.tonarc.data.preferences.UserPreferencesRepository
import com.quietrays.tonarc.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Direct client for YouTube Music Innertube internal API endpoints.
 */
@Singleton
class InnertubeApiService @Inject constructor(
    baseOkHttpClient: OkHttpClient,
    private val userPreferencesRepository: UserPreferencesRepository,
    @AppScope private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "InnertubeApi"
        private const val BASE_URL = "https://music.youtube.com/youtubei/v1"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        private const val CLIENT_NAME = "WEB_REMIX"
        private const val CLIENT_VERSION = "1.20240301.01.00"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        const val YTM_FILTER_SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
        const val YTM_FILTER_ALBUMS = "EgWKAQIBAWoKEAkQBRAKEAMQBA%3D%3D"
        const val YTM_FILTER_ARTISTS = "EgWKAQIgAWoKEAkQBRAKEAMQBA%3D%3D"
        const val YTM_FILTER_PLAYLISTS = "EgWKAQIoAWoKEAkQBRAKEAMQBA%3D%3D"

        @Volatile
        var cachedPlayerJsUrl: String? = null
    }

    private val okHttpClient: OkHttpClient = baseOkHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile
    var authCookies: String? = null
    @Volatile
    var visitorData: String? = null

    init {
        scope.launch(Dispatchers.IO) {
            userPreferencesRepository.youTubeAuthCookiesFlow.collect { cookies ->
                authCookies = cookies
                val sapisid = cookies?.let { extractCookieValue(it, "SAPISID") ?: extractCookieValue(it, "__Secure-3PAPISID") }
                android.util.Log.d("YouTubeMusic", "InnertubeApiService updated authCookies (length=${cookies?.length ?: 0}, hasSapisid=${!sapisid.isNullOrBlank()})")
            }
        }
        scope.launch(Dispatchers.IO) {
            userPreferencesRepository.youTubeVisitorDataFlow.collect { vData ->
                if (!vData.isNullOrBlank() && visitorData == null) {
                    visitorData = vData
                    android.util.Log.d("YouTubeMusic", "Loaded cached visitorData from DataStore: ${vData.take(20)}...")
                }
            }
        }
    }

    private fun extractCookieValue(cookies: String, name: String): String? {
        val regex = Regex("(?:^|;\\s*)${Regex.escape(name)}=([^;]+)")
        return regex.find(cookies)?.groupValues?.get(1)?.trim()
    }

    private fun extractVisitorData(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val vData = json.optJSONObject("responseContext")?.optString("visitorData")
            if (!vData.isNullOrBlank() && vData != visitorData) {
                visitorData = vData
                android.util.Log.d("YouTubeMusic", "Captured visitorData from response: ${vData.take(20)}...")
                scope.launch(Dispatchers.IO) {
                    userPreferencesRepository.setYouTubeVisitorData(vData)
                }
            }
        } catch (_: Exception) {}
    }

    private fun generateSapisidHash(sapisid: String, origin: String = "https://music.youtube.com"): String {
        val timestamp = System.currentTimeMillis() / 1000
        val toHash = "$timestamp $sapisid $origin"
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(toHash.toByteArray(Charsets.UTF_8))
        val hash = digest.joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestamp}_$hash"
    }

    private fun createBaseContext(): JSONObject {
        val client = JSONObject().apply {
            put("clientName", CLIENT_NAME)
            put("clientVersion", CLIENT_VERSION)
            put("hl", "en")
            put("gl", "US")
            visitorData?.let { put("visitorData", it) }
        }
        return JSONObject().apply {
            put("client", client)
        }
    }

    private fun createAndroidContext(): JSONObject {
        val client = JSONObject().apply {
            put("clientName", "ANDROID_MUSIC")
            put("clientVersion", "6.42.52")
            put("androidSdkVersion", 34)
            put("hl", "en")
            put("gl", "US")
            visitorData?.let { put("visitorData", it) }
        }
        return JSONObject().apply {
            put("client", client)
        }
    }

    private fun buildRequest(
        endpoint: String,
        bodyJson: JSONObject,
        clientName: String = CLIENT_NAME,
        clientVersion: String = CLIENT_VERSION,
        clientHeaderName: String = "67",
        userAgent: String = USER_AGENT
    ): Request {
        val url = "$BASE_URL/$endpoint?prettyPrint=false"
        val requestBody = bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE)

        val builder = Request.Builder()
            .url(url)
            .post(requestBody)
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
                val authHeader = generateSapisidHash(sapisid)
                builder.header("Authorization", authHeader)
            }
        }

        return builder.build()
    }

    @Volatile
    private var cachedSignatureTimestamp: Int? = null
    @Volatile
    private var lastStsFetchTime: Long = 0L

    private fun getSignatureTimestamp(): Int? {
        val now = System.currentTimeMillis()
        if (cachedSignatureTimestamp != null && now - lastStsFetchTime < 6 * 3600 * 1000L) {
            return cachedSignatureTimestamp
        }
        try {
            val req = Request.Builder()
                .url("https://www.youtube.com/")
                .header("User-Agent", USER_AGENT)
                .build()
            val resp = okHttpClient.newCall(req).execute()
            val html = resp.body?.string() ?: return cachedSignatureTimestamp
            val jsMatch = Regex("(/s/player/[^\"]+player_ias\\.vflset/[^\"]+base\\.js)").find(html)
                ?: Regex("\"jsUrl\":\"([^\"]+)\"").find(html)
            val jsPath = jsMatch?.groupValues?.get(1) ?: return cachedSignatureTimestamp
            val jsUrl = if (jsPath.startsWith("/")) "https://www.youtube.com$jsPath" else jsPath
            cachedPlayerJsUrl = jsUrl

            val jsReq = Request.Builder()
                .url(jsUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            val jsResp = okHttpClient.newCall(jsReq).execute()
            val jsCode = jsResp.body?.string() ?: return cachedSignatureTimestamp
            val stsMatch = Regex("signatureTimestamp:([0-9]+)").find(jsCode)
            val sts = stsMatch?.groupValues?.get(1)?.toIntOrNull()
            if (sts != null) {
                cachedSignatureTimestamp = sts
                lastStsFetchTime = now
                android.util.Log.d("YouTubeMusic", "Fetched YouTube signatureTimestamp: $sts (jsUrl=$jsUrl)")
            }
        } catch (e: Exception) {
            android.util.Log.w("YouTubeMusic", "Failed to fetch signatureTimestamp: ${e.message}")
        }
        return cachedSignatureTimestamp
    }

    suspend fun getStreamInfo(videoId: String): InnertubeStreamInfo? = withContext(Dispatchers.IO) {
        android.util.Log.d("YouTubeMusic", "getStreamInfo requested for videoId: $videoId")
        val sts = getSignatureTimestamp()

        // 1. Try Web Remix client with signatureTimestamp
        try {
            val body = JSONObject().apply {
                put("context", createBaseContext())
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
                if (sts != null) {
                    val playbackContext = JSONObject().apply {
                        val contentPlaybackContext = JSONObject().apply {
                            put("signatureTimestamp", sts)
                            put("html5Preference", "HTML5_PREF_WANTS")
                        }
                        put("contentPlaybackContext", contentPlaybackContext)
                    }
                    put("playbackContext", playbackContext)
                }
            }
            val request = buildRequest("player", body)
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            android.util.Log.d("YouTubeMusic", "Web player response: code=${response.code}, bodyLength=${responseBody?.length ?: 0}")

            if (responseBody != null) {
                extractVisitorData(responseBody)
                val info = InnertubeParser.parsePlayerResponse(responseBody)
                android.util.Log.d("YouTubeMusic", "Parsed stream info: title='${info?.title}', formats=${info?.formats?.size}, selectedUrl=${info?.selectedFormatUrl != null}")
                if (info != null) {
                    return@withContext info
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("YouTubeMusic", "Web player call failed for $videoId", e)
        }

        // 2. Try Android Music client as fallback
        try {
            val body = JSONObject().apply {
                put("context", createAndroidContext())
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }
            val request = buildRequest(
                endpoint = "player",
                bodyJson = body,
                clientName = "ANDROID_MUSIC",
                clientVersion = "6.42.52",
                clientHeaderName = "21",
                userAgent = "com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14; en_US) gzip"
            )
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            if (responseBody != null) {
                extractVisitorData(responseBody)
                val info = InnertubeParser.parsePlayerResponse(responseBody)
                if (info?.selectedFormatUrl != null) {
                    android.util.Log.d("YouTubeMusic", "Android client resolved formatUrl for $videoId")
                    return@withContext info
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("YouTubeMusic", "Android client player call failed for $videoId", e)
        }

        null
    }

    suspend fun search(
        query: String,
        params: String? = YTM_FILTER_SONGS,
        continuation: String? = null
    ): InnertubeSearchResult = withContext(Dispatchers.IO) {
        android.util.Log.d("YouTubeMusic", "search requested: query='$query', params='$params', continuation=${continuation != null}")
        try {
            val body = JSONObject().apply {
                put("context", createBaseContext())
                if (!continuation.isNullOrBlank()) {
                    put("continuation", continuation)
                } else {
                    put("query", query)
                    if (!params.isNullOrBlank()) {
                        put("params", params)
                    }
                }
            }
            val request = buildRequest("search", body)
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.w("YouTubeMusic", "Search API error: ${response.code}")
                return@withContext InnertubeSearchResult(query)
            }
            val responseBody = response.body?.string() ?: return@withContext InnertubeSearchResult(query)
            extractVisitorData(responseBody)
            val result = InnertubeParser.parseSearchResults(query, responseBody)
            android.util.Log.d("YouTubeMusic", "Search results for '$query': ${result.songs.size} songs, ${result.albums.size} albums, ${result.playlists.size} playlists, ${result.artists.size} artists, continuation=${result.continuationToken != null}")
            result
        } catch (e: Exception) {
            android.util.Log.e("YouTubeMusic", "Error searching Innertube for: $query", e)
            InnertubeSearchResult(query)
        }
    }

    suspend fun getBrowse(browseId: String = "FEmusic_home"): List<InnertubeBrowseSection> = withContext(Dispatchers.IO) {
        android.util.Log.d("YouTubeMusic", "getBrowse requested: $browseId")
        try {
            val body = JSONObject().apply {
                put("context", createBaseContext())
                put("browseId", browseId)
            }
            val request = buildRequest("browse", body)
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.w("YouTubeMusic", "Browse API error: ${response.code}")
                return@withContext emptyList()
            }
            val responseBody = response.body?.string() ?: return@withContext emptyList()
            extractVisitorData(responseBody)
            val sections = InnertubeParser.parseBrowseSections(responseBody)
            android.util.Log.d("YouTubeMusic", "Browse sections for $browseId: ${sections.size} sections")
            sections
        } catch (e: Exception) {
            android.util.Log.e("YouTubeMusic", "Error fetching browse for: $browseId", e)
            emptyList()
        }
    }

    suspend fun getLikedSongs(continuation: String? = null): Pair<List<InnertubeTrack>, String?> = withContext(Dispatchers.IO) {
        android.util.Log.d("YouTubeMusic", "getLikedSongs requested: continuation=${continuation != null}")
        try {
            val body = JSONObject().apply {
                put("context", createBaseContext())
                if (!continuation.isNullOrBlank()) {
                    put("continuation", continuation)
                } else {
                    put("browseId", "FEmusic_liked_videos")
                }
            }
            val request = buildRequest("browse", body)
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.w("YouTubeMusic", "Liked songs browse API error: ${response.code}")
                return@withContext emptyList<InnertubeTrack>() to null
            }
            val responseBody = response.body?.string() ?: return@withContext emptyList<InnertubeTrack>() to null
            extractVisitorData(responseBody)
            InnertubeParser.parseLikedSongs(responseBody)
        } catch (e: Exception) {
            android.util.Log.e("YouTubeMusic", "Error fetching liked songs", e)
            emptyList<InnertubeTrack>() to null
        }
    }

    suspend fun getUserPlaylists(continuation: String? = null): Pair<List<InnertubePlaylist>, String?> = withContext(Dispatchers.IO) {
        android.util.Log.d("YouTubeMusic", "getUserPlaylists requested: continuation=${continuation != null}")
        try {
            val body = JSONObject().apply {
                put("context", createBaseContext())
                if (!continuation.isNullOrBlank()) {
                    put("continuation", continuation)
                } else {
                    put("browseId", "FEmusic_library_playlists")
                }
            }
            val request = buildRequest("browse", body)
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.w("YouTubeMusic", "User playlists browse API error: ${response.code}")
                return@withContext emptyList<InnertubePlaylist>() to null
            }
            val responseBody = response.body?.string() ?: return@withContext emptyList<InnertubePlaylist>() to null
            extractVisitorData(responseBody)
            InnertubeParser.parseLibraryPlaylists(responseBody)
        } catch (e: Exception) {
            android.util.Log.e("YouTubeMusic", "Error fetching user playlists", e)
            emptyList<InnertubePlaylist>() to null
        }
    }

    suspend fun getPlaylist(playlistId: String): Pair<InnertubePlaylist, List<InnertubeTrack>>? = withContext(Dispatchers.IO) {
        val actualBrowseId = if (!playlistId.startsWith("VL") && playlistId.startsWith("PL")) "VL$playlistId" else playlistId
        android.util.Log.d("YouTubeMusic", "getPlaylist requested: $playlistId -> $actualBrowseId")
        try {
            val body = JSONObject().apply {
                put("context", createBaseContext())
                put("browseId", actualBrowseId)
            }
            val request = buildRequest("browse", body)
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.w("YouTubeMusic", "Playlist browse API error: ${response.code}")
                return@withContext null
            }
            val responseBody = response.body?.string() ?: return@withContext null
            extractVisitorData(responseBody)
            InnertubeParser.parsePlaylistDetails(playlistId, responseBody)
        } catch (e: Exception) {
            android.util.Log.e("YouTubeMusic", "Error fetching playlist for: $playlistId", e)
            null
        }
    }

    suspend fun getAlbum(browseId: String): Pair<InnertubeAlbum, List<InnertubeTrack>>? = withContext(Dispatchers.IO) {
        android.util.Log.d("YouTubeMusic", "getAlbum requested: $browseId")
        try {
            val body = JSONObject().apply {
                put("context", createBaseContext())
                put("browseId", browseId)
            }
            val request = buildRequest("browse", body)
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.w("YouTubeMusic", "Album browse API error: ${response.code}")
                return@withContext null
            }
            val responseBody = response.body?.string() ?: return@withContext null
            extractVisitorData(responseBody)
            InnertubeParser.parseAlbumDetails(browseId, responseBody)
        } catch (e: Exception) {
            android.util.Log.e("YouTubeMusic", "Error fetching album for: $browseId", e)
            null
        }
    }

    suspend fun getArtist(browseId: String): Pair<InnertubeArtist, List<InnertubeTrack>>? = withContext(Dispatchers.IO) {
        android.util.Log.d("YouTubeMusic", "getArtist requested: $browseId")
        try {
            val body = JSONObject().apply {
                put("context", createBaseContext())
                put("browseId", browseId)
            }
            val request = buildRequest("browse", body)
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.w("YouTubeMusic", "Artist browse API error: ${response.code}")
                return@withContext null
            }
            val responseBody = response.body?.string() ?: return@withContext null
            extractVisitorData(responseBody)
            InnertubeParser.parseArtistDetails(browseId, responseBody)
        } catch (e: Exception) {
            android.util.Log.e("YouTubeMusic", "Error fetching artist for: $browseId", e)
            null
        }
    }

    suspend fun getTranscriptLyrics(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("context", createBaseContext())
                val params = JSONObject().apply {
                    put("videoId", videoId)
                }
                put("params", "CAESAhAB")
            }
            val request = buildRequest("get_transcript", body)
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val responseBody = response.body?.string() ?: return@withContext null
            InnertubeParser.parseTranscriptLyrics(responseBody)
        } catch (e: Exception) {
            android.util.Log.w("YouTubeMusic", "Failed to parse transcript lyrics for $videoId", e)
            null
        }
    }

    suspend fun getRadioTracks(videoId: String, continuation: String? = null): List<InnertubeTrack> = withContext(Dispatchers.IO) {
        android.util.Log.d("YouTubeMusic", "getRadioTracks requested for: $videoId")
        try {
            val body = JSONObject().apply {
                put("context", createBaseContext())
                if (!continuation.isNullOrBlank()) {
                    put("continuation", continuation)
                } else {
                    put("videoId", videoId)
                    put("playlistId", "RDAMVM$videoId")
                    put("isAudioOnly", true)
                    put("enablePersistentPlaylistPanel", true)
                }
            }
            val request = buildRequest("next", body)
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.w("YouTubeMusic", "Radio Next API error: ${response.code}")
                return@withContext emptyList()
            }
            val responseBody = response.body?.string() ?: return@withContext emptyList()
            extractVisitorData(responseBody)
            val tracks = InnertubeParser.parseRadioTracks(responseBody)
            android.util.Log.d("YouTubeMusic", "Fetched ${tracks.size} radio tracks for seed $videoId")
            tracks
        } catch (e: Exception) {
            android.util.Log.e("YouTubeMusic", "Error fetching radio tracks for: $videoId", e)
            emptyList()
        }
    }

    suspend fun setLikeStatus(videoId: String, isLiked: Boolean): Boolean = withContext(Dispatchers.IO) {
        val endpoint = if (isLiked) "like/like" else "like/removelike"
        android.util.Log.d("YouTubeMusic", "setLikeStatus requested: videoId=$videoId, isLiked=$isLiked, endpoint=$endpoint")
        try {
            val body = JSONObject().apply {
                put("context", createBaseContext())
                put("target", JSONObject().apply {
                    put("videoId", videoId)
                })
            }
            val request = buildRequest(endpoint, body)
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    extractVisitorData(responseBody)
                }
                android.util.Log.d("YouTubeMusic", "setLikeStatus succeeded for videoId=$videoId (isLiked=$isLiked)")
                true
            } else {
                android.util.Log.w("YouTubeMusic", "setLikeStatus failed: HTTP ${response.code} for videoId=$videoId (endpoint=$endpoint)")
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("YouTubeMusic", "Error in setLikeStatus for videoId=$videoId", e)
            false
        }
    }

    /**
     * Self-diagnostic method to test YouTube Music API connectivity.
     */
    suspend fun testConnection(): Map<String, String> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, String>()
        try {
            // 1. Search Test
            val searchRes = search("Queen Bohemian Rhapsody")
            results["search"] = "Found ${searchRes.songs.size} songs, ${searchRes.albums.size} albums"

            // 2. Browse Test
            val browseRes = getBrowse()
            results["browse"] = "Found ${browseRes.size} sections"

            // 3. Stream Info Test
            val testVid = searchRes.songs.firstOrNull()?.videoId ?: "fJ9rUzIMcZQ"
            val streamRes = getStreamInfo(testVid)
            results["stream"] = "Video '$testVid': status=${streamRes != null}, formats=${streamRes?.formats?.size ?: 0}"
            results["auth"] = if (!authCookies.isNullOrBlank()) "Logged In" else "Anonymous (No Cookies)"
        } catch (e: Exception) {
            results["error"] = e.message ?: "Unknown error"
        }
        results
    }
}
