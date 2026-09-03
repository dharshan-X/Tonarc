package com.quietrays.tonarc.data.network.spotify

import com.quietrays.tonarc.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network fetcher for Spotify playlists.
 *
 * Utilizes Spotify's public anonymous access token endpoint to fetch complete playlist metadata
 * and tracks via the Spotify Web API. Automatically paginates through tracks (up to [MAX_TRACKS]).
 * If the Web API or token endpoint fails, gracefully falls back to the Spotify Embed page HTML
 * and oEmbed metadata scraper.
 */
@Singleton
class SpotifyPlaylistFetcher @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    companion object {
        private const val TAG = "SpotifyPlaylistFetcher"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        private const val TOKEN_URL =
            "https://open.spotify.com/get_access_token?reason=transport&productType=web_player"
        private const val WEB_API_BASE_URL = "https://api.spotify.com/v1/playlists/"
        private const val ME_URL = "https://api.spotify.com/v1/me"
        private const val EMBED_BASE_URL = "https://open.spotify.com/embed/playlist/"
        private const val OEMBED_BASE_URL = "https://open.spotify.com/oembed?url=https://open.spotify.com/playlist/"

        private const val MAX_TRACKS = 500
        private const val EXPIRATION_BUFFER_MS = 60_000L
        private const val DEFAULT_TOKEN_TTL_MS = 3_600_000L

        private val URI_REGEX = Regex("""^spotify:playlist:([a-zA-Z0-9]+)(?:\?.*)?$""")
        private val WEB_URL_REGEX =
            Regex("""^(?:https?://)?(?:[a-zA-Z0-9-]+\.)*spotify\.com/(?:intl-[a-zA-Z0-9_-]+/)?(?:embed/)?playlist/([a-zA-Z0-9]+)(?:[/?#].*)?$""")
        private val RAW_ID_REGEX = Regex("""^[a-zA-Z0-9]{22}$""")
        private val NEXT_DATA_REGEX =
            Regex("""<script\s+id="__NEXT_DATA__"\s+type="application/json">([^<]+)</script>""")
    }

    @Volatile
    private var cachedAccessToken: String? = null

    @Volatile
    private var tokenExpirationTimestampMs: Long = 0L

    @Volatile
    private var cachedCookies: String? = null

    private sealed interface WebApiPlaylistResult {
        data class Success(val playlist: SpotifyPlaylist) : WebApiPlaylistResult
        data class ForbiddenOrNotFound(val statusCode: Int) : WebApiPlaylistResult
        data class OtherError(val statusCode: Int) : WebApiPlaylistResult
    }

    /**
     * Extracts a Spotify playlist ID from various URL and URI representations, or returns
     * the raw ID if valid.
     *
     * Supports:
     * - `https://open.spotify.com/playlist/{id}` (with or without query parameters)
     * - `https://open.spotify.com/intl-.../playlist/{id}`
     * - `https://open.spotify.com/embed/playlist/{id}`
     * - `spotify:playlist:{id}`
     * - 22-character base-62 Spotify alphanumeric ID
     *
     * Returns null if the input is not a recognized Spotify playlist URL or ID.
     */
    fun extractPlaylistId(urlOrId: String): String? {
        val trimmed = urlOrId.trim()
        if (trimmed.isEmpty()) return null

        val uriMatch = URI_REGEX.find(trimmed)
        if (uriMatch != null) {
            return uriMatch.groupValues[1]
        }

        val urlMatch = WEB_URL_REGEX.find(trimmed)
        if (urlMatch != null) {
            return urlMatch.groupValues[1]
        }

        if (RAW_ID_REGEX.matches(trimmed)) {
            return trimmed
        }

        return null
    }

    /**
     * Obtains an access token from the Spotify Web Player endpoint, attaching authenticated
     * session cookies if present in preferences. Caches the token in memory until expiration,
     * unless [forceRefresh] is true or the user's cookies have changed.
     */
    suspend fun getAccessToken(forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        val savedCookies = userPreferencesRepository.spotifyAuthCookiesFlow.first()?.takeIf { it.isNotBlank() }
        val now = System.currentTimeMillis()
        val currentToken = cachedAccessToken
        val cookiesChanged = savedCookies != cachedCookies

        if (!forceRefresh && !cookiesChanged && currentToken != null && now < (tokenExpirationTimestampMs - EXPIRATION_BUFFER_MS)) {
            return@withContext currentToken
        }

        try {
            val requestBuilder = Request.Builder()
                .url(TOKEN_URL)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .get()

            if (!savedCookies.isNullOrBlank()) {
                requestBuilder.header("Cookie", savedCookies)
            }

            val request = requestBuilder.build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("Failed to fetch access token: HTTP %d", response.code)
                    if (forceRefresh || cookiesChanged) {
                        cachedAccessToken = null
                        tokenExpirationTimestampMs = 0L
                        cachedCookies = null
                    }
                    return@withContext null
                }
                val bodyString = response.body.string()
                if (bodyString.isBlank()) return@withContext null
                val json = JSONObject(bodyString)
                val token = json.optString("accessToken").takeIf { it.isNotBlank() } ?: return@withContext null
                val expMs = json.optLong("accessTokenExpirationTimestampMs", 0L)

                cachedAccessToken = token
                tokenExpirationTimestampMs = if (expMs > 0) expMs else (now + DEFAULT_TOKEN_TTL_MS)
                cachedCookies = savedCookies
                token
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Exception while fetching Spotify token")
            null
        }
    }

    /**
     * Obtains an anonymous or authenticated Spotify access token from the Web Player endpoint.
     * Backwards-compatible alias for [getAccessToken].
     */
    suspend fun getAnonymousToken(): String? = getAccessToken(forceRefresh = false)

    /**
     * Fetches the current user's profile information from the Spotify Web API.
     *
     * Returns a pair of `(id, displayName)`, or null if unauthorized, unauthenticated,
     * or the request fails.
     */
    suspend fun fetchCurrentUserProfile(token: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(ME_URL)
                .header("Authorization", "Bearer $token")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("Failed to fetch Spotify user profile: HTTP %d", response.code)
                    return@withContext null
                }
                val bodyString = response.body.string()
                if (bodyString.isBlank()) return@withContext null
                val json = JSONObject(bodyString)
                val id = json.optString("id").takeIf { it.isNotBlank() } ?: return@withContext null
                val displayName = json.optString("display_name").trim().ifBlank { id }
                Pair(id, displayName)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Exception while fetching Spotify user profile")
            null
        }
    }

    /**
     * Fetches complete playlist details and track listing for the given [playlistId].
     *
     * Strategy:
     * 1. Attempt Spotify Web API using access token with pagination up to [MAX_TRACKS].
     * 2. If API fails with 404/403 (private or forbidden playlist), checks embed fallback.
     *    If neither Web API nor Embed returns valid tracks, throws [SpotifyPrivatePlaylistException].
     * 3. Fall back to scraping embedded Next.js JSON from embed page.
     * 4. If embed HTML scraper fails, fall back to oEmbed endpoint for basic playlist metadata.
     */
    suspend fun fetchPlaylist(playlistId: String): Result<SpotifyPlaylist> = withContext(Dispatchers.IO) {
        runCatching {
            var wasForbiddenOrNotFound = false
            val token = getAccessToken()
            if (token != null) {
                when (val apiResult = fetchFromWebApi(playlistId, token)) {
                    is WebApiPlaylistResult.Success -> return@runCatching apiResult.playlist
                    is WebApiPlaylistResult.ForbiddenOrNotFound -> wasForbiddenOrNotFound = true
                    is WebApiPlaylistResult.OtherError -> { /* continue to fallbacks */ }
                }
            }

            val embedResult = fetchFromEmbed(playlistId)
            if (embedResult != null && embedResult.tracks.isNotEmpty()) {
                return@runCatching embedResult
            }

            if (wasForbiddenOrNotFound) {
                val isLoggedIn = !userPreferencesRepository.spotifyAuthCookiesFlow.first().isNullOrBlank()
                throw SpotifyPrivatePlaylistException(playlistId, isUserLoggedIn = isLoggedIn)
            }

            if (embedResult != null) {
                return@runCatching embedResult
            }

            val oEmbedResult = fetchFromOEmbed(playlistId)
            if (oEmbedResult != null) {
                return@runCatching oEmbedResult
            }

            throw IOException("Failed to fetch Spotify playlist '$playlistId' via Web API and Embed fallbacks")
        }
    }

    private fun fetchFromWebApi(playlistId: String, token: String): WebApiPlaylistResult {
        try {
            val initialUrl = "$WEB_API_BASE_URL$playlistId"
            val request = Request.Builder()
                .url(initialUrl)
                .header("Authorization", "Bearer $token")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("Spotify Web API request failed: HTTP %d", response.code)
                    if (response.code == 401) {
                        cachedAccessToken = null
                        tokenExpirationTimestampMs = 0L
                        cachedCookies = null
                    }
                    if (response.code == 404 || response.code == 403) {
                        return WebApiPlaylistResult.ForbiddenOrNotFound(response.code)
                    }
                    return WebApiPlaylistResult.OtherError(response.code)
                }
                val bodyString = response.body.string()
                if (bodyString.isBlank()) return WebApiPlaylistResult.OtherError(response.code)
                val rootJson = JSONObject(bodyString)

                val id = rootJson.optString("id").ifEmpty { playlistId }
                val title = rootJson.optString("name").ifEmpty { "Spotify Playlist" }
                val description = rootJson.optString("description").takeIf { it.isNotBlank() }
                val author = rootJson.optJSONObject("owner")?.optString("display_name")?.takeIf { it.isNotBlank() }
                    ?: rootJson.optJSONObject("owner")?.optString("id")?.takeIf { it.isNotBlank() }

                val coverUri = extractCoverUri(rootJson.optJSONArray("images"))

                val tracksObj = rootJson.optJSONObject("tracks")
                val totalTracks = tracksObj?.optInt("total", 0) ?: 0
                val tracksList = mutableListOf<SpotifyTrack>()

                if (tracksObj != null) {
                    parseTracksArray(tracksObj.optJSONArray("items"), tracksList)
                    var nextUrl = tracksObj.optString("next").takeIf { it.isNotBlank() }

                    while (nextUrl != null && tracksList.size < MAX_TRACKS) {
                        val nextRequest = Request.Builder()
                            .url(nextUrl)
                            .header("Authorization", "Bearer $token")
                            .header("User-Agent", USER_AGENT)
                            .header("Accept", "application/json")
                            .get()
                            .build()

                        val (pageTracks, newNext) = okHttpClient.newCall(nextRequest).execute().use { pageResp ->
                            if (!pageResp.isSuccessful) {
                                return@use Pair(emptyList<SpotifyTrack>(), null)
                            }
                            val pageBody = pageResp.body.string()
                            if (pageBody.isBlank()) return@use Pair(emptyList<SpotifyTrack>(), null)
                            val pageJson = JSONObject(pageBody)
                            val pageList = mutableListOf<SpotifyTrack>()
                            parseTracksArray(pageJson.optJSONArray("items"), pageList)
                            Pair(pageList, pageJson.optString("next").takeIf { it.isNotBlank() })
                        }

                        if (pageTracks.isEmpty()) break
                        val remainingSpace = MAX_TRACKS - tracksList.size
                        tracksList.addAll(pageTracks.take(remainingSpace))
                        nextUrl = newNext
                    }
                }

                return WebApiPlaylistResult.Success(
                    SpotifyPlaylist(
                        id = id,
                        title = title,
                        description = description,
                        author = author,
                        coverUri = coverUri,
                        trackCount = if (totalTracks > 0) totalTracks else tracksList.size,
                        tracks = tracksList
                    )
                )
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Error fetching from Spotify Web API")
            return WebApiPlaylistResult.OtherError(-1)
        }
    }

    private fun parseTracksArray(itemsArray: JSONArray?, outList: MutableList<SpotifyTrack>) {
        if (itemsArray == null) return
        for (i in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(i) ?: continue
            val trackObj = item.optJSONObject("track") ?: item
            val title = trackObj.optString("name").takeIf { it.isNotBlank() } ?: continue
            val id = trackObj.optString("id").takeIf { it.isNotBlank() } ?: continue

            val artistsArray = trackObj.optJSONArray("artists")
            val artists = mutableListOf<String>()
            if (artistsArray != null) {
                for (j in 0 until artistsArray.length()) {
                    val artistObj = artistsArray.optJSONObject(j)
                    val artistName = artistObj?.optString("name")
                    if (!artistName.isNullOrBlank()) {
                        artists.add(artistName)
                    }
                }
            }
            val mainArtist = artists.firstOrNull() ?: "Unknown Artist"

            val albumObj = trackObj.optJSONObject("album")
            val albumName = albumObj?.optString("name")?.takeIf { it.isNotBlank() }
            val coverUri = extractCoverUri(albumObj?.optJSONArray("images"))
            val durationMs = trackObj.optLong("duration_ms", 0L)

            outList.add(
                SpotifyTrack(
                    id = id,
                    title = title,
                    artist = mainArtist,
                    artists = if (artists.isNotEmpty()) artists else listOf(mainArtist),
                    album = albumName,
                    durationMs = durationMs,
                    coverUri = coverUri
                )
            )
        }
    }

    private fun fetchFromEmbed(playlistId: String): SpotifyPlaylist? {
        try {
            val url = "$EMBED_BASE_URL$playlistId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("Spotify embed request failed: HTTP %d", response.code)
                    return null
                }
                val html = response.body.string()
                if (html.isBlank()) return null
                return parseEmbedHtml(playlistId, html)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Error fetching Spotify embed page")
            return null
        }
    }

    internal fun parseEmbedHtml(playlistId: String, html: String): SpotifyPlaylist? {
        val match = NEXT_DATA_REGEX.find(html) ?: return null
        val jsonString = match.groupValues[1].trim()
        if (jsonString.isEmpty()) return null

        try {
            val root = JSONObject(jsonString)
            val entity = findEntityObject(root) ?: return null

            val id = entity.optString("id").ifEmpty { playlistId }
            val title = entity.optString("title").takeIf { it.isNotBlank() }
                ?: entity.optString("name").takeIf { it.isNotBlank() }
                ?: "Spotify Playlist"
            val description = entity.optString("description").takeIf { it.isNotBlank() }
            val author = entity.optString("subtitle").takeIf { it.isNotBlank() }
                ?: entity.optJSONObject("owner")?.optString("name")
                ?: entity.optJSONObject("owner")?.optString("display_name")

            var coverUri: String? = null
            val visualIdentity = entity.optJSONObject("visualIdentity")
            if (visualIdentity != null) {
                val imgArr = visualIdentity.optJSONArray("image")
                coverUri = extractCoverUri(imgArr)
            }
            if (coverUri == null) {
                val coverArt = entity.optJSONObject("coverArt")
                val sources = coverArt?.optJSONArray("sources")
                coverUri = extractCoverUri(sources)
            }
            if (coverUri == null) {
                coverUri = extractCoverUri(entity.optJSONArray("images"))
            }

            val trackList = mutableListOf<SpotifyTrack>()
            val rawTrackList = entity.optJSONArray("trackList")
                ?: entity.optJSONArray("tracks")
                ?: entity.optJSONObject("tracks")?.optJSONArray("items")

            if (rawTrackList != null) {
                for (i in 0 until rawTrackList.length()) {
                    val item = rawTrackList.optJSONObject(i) ?: continue
                    val trackObj = item.optJSONObject("track") ?: item

                    val rawId = trackObj.optString("id").takeIf { it.isNotBlank() }
                        ?: trackObj.optString("uri").removePrefix("spotify:track:").takeIf { it.isNotBlank() }
                        ?: "spotify_embed_track_$i"

                    val trackTitle = trackObj.optString("title").takeIf { it.isNotBlank() }
                        ?: trackObj.optString("name").takeIf { it.isNotBlank() }
                        ?: continue

                    val artists = mutableListOf<String>()
                    val artistsArr = trackObj.optJSONArray("artists")
                    if (artistsArr != null) {
                        for (j in 0 until artistsArr.length()) {
                            val aObj = artistsArr.optJSONObject(j)
                            val name = aObj?.optString("name")
                            if (!name.isNullOrBlank()) artists.add(name)
                        }
                    }
                    if (artists.isEmpty()) {
                        val subtitle = trackObj.optString("subtitle")
                        if (subtitle.isNotBlank()) {
                            artists.addAll(subtitle.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                        }
                    }
                    val mainArtist = artists.firstOrNull() ?: "Unknown Artist"

                    val albumObj = trackObj.optJSONObject("album")
                    val albumName = albumObj?.optString("name")?.takeIf { it.isNotBlank() }
                        ?: trackObj.optString("albumName").takeIf { it.isNotBlank() }

                    var trackCover: String? = null
                    val albumCoverArt = albumObj?.optJSONObject("coverArt")?.optJSONArray("sources")
                    if (albumCoverArt != null) {
                        trackCover = extractCoverUri(albumCoverArt)
                    }
                    if (trackCover == null) {
                        trackCover = extractCoverUri(albumObj?.optJSONArray("images"))
                    }
                    if (trackCover == null) {
                        trackCover = coverUri
                    }

                    val duration = trackObj.optLong(
                        "duration",
                        trackObj.optLong("durationMs", trackObj.optLong("duration_ms", 0L))
                    )

                    trackList.add(
                        SpotifyTrack(
                            id = rawId,
                            title = trackTitle,
                            artist = mainArtist,
                            artists = if (artists.isNotEmpty()) artists else listOf(mainArtist),
                            album = albumName,
                            durationMs = duration,
                            coverUri = trackCover
                        )
                    )
                }
            }

            return SpotifyPlaylist(
                id = id,
                title = title,
                description = description,
                author = author,
                coverUri = coverUri,
                trackCount = trackList.size,
                tracks = trackList
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Error parsing Next.js embed JSON")
            return null
        }
    }

    private fun findEntityObject(root: JSONObject): JSONObject? {
        root.optJSONObject("props")
            ?.optJSONObject("pageProps")
            ?.optJSONObject("state")
            ?.optJSONObject("data")
            ?.optJSONObject("entity")
            ?.let { return it }

        root.optJSONObject("props")
            ?.optJSONObject("pageProps")
            ?.optJSONObject("entity")
            ?.let { return it }

        root.optJSONObject("state")
            ?.optJSONObject("data")
            ?.optJSONObject("entity")
            ?.let { return it }

        root.optJSONObject("entity")?.let { return it }

        return null
    }

    private fun fetchFromOEmbed(playlistId: String): SpotifyPlaylist? {
        try {
            val oEmbedUrl = "$OEMBED_BASE_URL$playlistId"
            val request = Request.Builder()
                .url(oEmbedUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("Spotify oEmbed request failed: HTTP %d", response.code)
                    return null
                }
                val bodyString = response.body.string()
                if (bodyString.isBlank()) return null
                val json = JSONObject(bodyString)
                val title = json.optString("title").takeIf { it.isNotBlank() } ?: "Spotify Playlist"
                val thumbnailUrl = json.optString("thumbnail_url").takeIf { it.isNotBlank() }
                val author = json.optString("provider_name").takeIf { it.isNotBlank() }

                return SpotifyPlaylist(
                    id = playlistId,
                    title = title,
                    description = null,
                    author = author,
                    coverUri = thumbnailUrl,
                    trackCount = 0,
                    tracks = emptyList()
                )
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Error fetching Spotify oEmbed data")
            return null
        }
    }

    private fun extractCoverUri(imagesArray: JSONArray?): String? {
        if (imagesArray == null || imagesArray.length() == 0) return null
        var bestUrl: String? = null
        var maxWidth = -1
        for (i in 0 until imagesArray.length()) {
            val imgObj = imagesArray.optJSONObject(i) ?: continue
            val url = imgObj.optString("url").takeIf { it.isNotBlank() } ?: continue
            val width = imgObj.optInt("width", 0)
            if (width > maxWidth || bestUrl == null) {
                maxWidth = width
                bestUrl = url
            }
        }
        return bestUrl
    }
}
