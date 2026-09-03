package com.quietrays.tonarc.data.network.spotify

import com.google.common.truth.Truth.assertThat
import com.quietrays.tonarc.data.preferences.UserPreferencesRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test

class SpotifyPlaylistFetcherTest {

    private lateinit var interceptor: TestInterceptor
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private val cookiesFlow = MutableStateFlow<String?>(null)
    private lateinit var fetcher: SpotifyPlaylistFetcher

    private class TestInterceptor : Interceptor {
        val recordedRequests = mutableListOf<Request>()
        var responseProvider: (Request) -> Response = { request ->
            createJsonResponse(request, 200, "{}")
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            recordedRequests.add(request)
            return responseProvider(request)
        }
    }

    companion object {
        fun createJsonResponse(request: Request, code: Int = 200, bodyJson: String = "{}"): Response {
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Error")
                .body(bodyJson.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }

        fun createHtmlResponse(request: Request, code: Int = 200, bodyHtml: String = ""): Response {
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Error")
                .body(bodyHtml.toResponseBody("text/html; charset=utf-8".toMediaType()))
                .build()
        }
    }

    @Before
    fun setUp() {
        cookiesFlow.value = null
        userPreferencesRepository = mockk(relaxed = true)
        every { userPreferencesRepository.spotifyAuthCookiesFlow } returns cookiesFlow

        interceptor = TestInterceptor()
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
        fetcher = SpotifyPlaylistFetcher(okHttpClient, userPreferencesRepository)
    }

    @Test
    fun extractPlaylistId_extractsFromVariousUrlFormats() {
        // Standard Web URL
        assertThat(fetcher.extractPlaylistId("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M")).isEqualTo("37i9dQZF1DXcBWIGoYBM5M")
        // Web URL with query parameters
        assertThat(fetcher.extractPlaylistId("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc123456789")).isEqualTo("37i9dQZF1DXcBWIGoYBM5M")
        // Internationalized URL
        assertThat(fetcher.extractPlaylistId("https://open.spotify.com/intl-en/playlist/37i9dQZF1DXcBWIGoYBM5M?si=xyz")).isEqualTo("37i9dQZF1DXcBWIGoYBM5M")
        assertThat(fetcher.extractPlaylistId("https://open.spotify.com/intl-pt-br/playlist/37i9dQZF1DXcBWIGoYBM5M")).isEqualTo("37i9dQZF1DXcBWIGoYBM5M")
        // Embed URL
        assertThat(fetcher.extractPlaylistId("https://open.spotify.com/embed/playlist/37i9dQZF1DXcBWIGoYBM5M")).isEqualTo("37i9dQZF1DXcBWIGoYBM5M")
        // URI format
        assertThat(fetcher.extractPlaylistId("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M")).isEqualTo("37i9dQZF1DXcBWIGoYBM5M")
        assertThat(fetcher.extractPlaylistId("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M?si=123")).isEqualTo("37i9dQZF1DXcBWIGoYBM5M")
        // Raw 22-char base-62 ID
        assertThat(fetcher.extractPlaylistId("37i9dQZF1DXcBWIGoYBM5M")).isEqualTo("37i9dQZF1DXcBWIGoYBM5M")
        // With trailing slash and whitespace
        assertThat(fetcher.extractPlaylistId("  https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M/  ")).isEqualTo("37i9dQZF1DXcBWIGoYBM5M")

        // Invalid formats
        assertThat(fetcher.extractPlaylistId("https://open.spotify.com/album/37i9dQZF1DXcBWIGoYBM5M")).isNull()
        assertThat(fetcher.extractPlaylistId("https://open.spotify.com/track/37i9dQZF1DXcBWIGoYBM5M")).isNull()
        assertThat(fetcher.extractPlaylistId("spotify:album:37i9dQZF1DXcBWIGoYBM5M")).isNull()
        assertThat(fetcher.extractPlaylistId("https://google.com")).isNull()
        assertThat(fetcher.extractPlaylistId("")).isNull()
        assertThat(fetcher.extractPlaylistId("   ")).isNull()
        assertThat(fetcher.extractPlaylistId("invalid_playlist_id_short")).isNull()
    }

    @Test
    fun getAnonymousToken_fetchesAndCachesToken() = runBlocking {
        interceptor.responseProvider = { request ->
            if (request.url.toString().contains("get_access_token")) {
                createJsonResponse(
                    request,
                    200,
                    """
                    {
                      "clientId": "mock_client_id",
                      "accessToken": "mock_token_abc_123",
                      "accessTokenExpirationTimestampMs": ${System.currentTimeMillis() + 3600000},
                      "isAnonymous": true
                    }
                    """.trimIndent()
                )
            } else {
                createJsonResponse(request, 404, "{}")
            }
        }

        val token1 = fetcher.getAnonymousToken()
        assertThat(token1).isEqualTo("mock_token_abc_123")
        assertThat(interceptor.recordedRequests.size).isEqualTo(1)
        assertThat(interceptor.recordedRequests[0].header("User-Agent"))
            .contains("Mozilla/5.0")

        // Second call should return cached token without issuing new network request
        val token2 = fetcher.getAnonymousToken()
        assertThat(token2).isEqualTo("mock_token_abc_123")
        assertThat(interceptor.recordedRequests.size).isEqualTo(1)
    }

    @Test
    fun getAccessToken_withCookies_attachesCookieHeader() = runBlocking {
        cookiesFlow.value = "sp_dc=mock_dc_cookie_value; sp_key=xyz"
        interceptor.responseProvider = { request ->
            if (request.url.toString().contains("get_access_token")) {
                createJsonResponse(
                    request,
                    200,
                    """
                    {
                      "accessToken": "auth_token_456",
                      "accessTokenExpirationTimestampMs": ${System.currentTimeMillis() + 3600000}
                    }
                    """.trimIndent()
                )
            } else {
                createJsonResponse(request, 404, "{}")
            }
        }

        val token = fetcher.getAccessToken()
        assertThat(token).isEqualTo("auth_token_456")
        assertThat(interceptor.recordedRequests).hasSize(1)
        val tokenReq = interceptor.recordedRequests[0]
        assertThat(tokenReq.header("Cookie")).isEqualTo("sp_dc=mock_dc_cookie_value; sp_key=xyz")
    }

    @Test
    fun getAccessToken_reFetchesWhenCookiesChangeOrForceRefresh() = runBlocking {
        interceptor.responseProvider = { request ->
            createJsonResponse(
                request,
                200,
                """
                {
                  "accessToken": "token_count_${interceptor.recordedRequests.size}",
                  "accessTokenExpirationTimestampMs": ${System.currentTimeMillis() + 3600000}
                }
                """.trimIndent()
            )
        }

        // 1. Initial call without cookies
        val token1 = fetcher.getAccessToken()
        assertThat(token1).isEqualTo("token_count_1")
        assertThat(interceptor.recordedRequests).hasSize(1)

        // 2. Cookie added -> should invalidate cache and fetch fresh token with Cookie header
        cookiesFlow.value = "sp_dc=new_cookie"
        val token2 = fetcher.getAccessToken()
        assertThat(token2).isEqualTo("token_count_2")
        assertThat(interceptor.recordedRequests).hasSize(2)
        assertThat(interceptor.recordedRequests[1].header("Cookie")).isEqualTo("sp_dc=new_cookie")

        // 3. Same cookies -> should hit cache
        val token3 = fetcher.getAccessToken()
        assertThat(token3).isEqualTo("token_count_2")
        assertThat(interceptor.recordedRequests).hasSize(2)

        // 4. Force refresh -> should bypass cache
        val token4 = fetcher.getAccessToken(forceRefresh = true)
        assertThat(token4).isEqualTo("token_count_3")
        assertThat(interceptor.recordedRequests).hasSize(3)
    }

    @Test
    fun fetchCurrentUserProfile_success_returnsIdAndDisplayName() = runBlocking {
        interceptor.responseProvider = { request ->
            if (request.url.toString() == "https://api.spotify.com/v1/me") {
                createJsonResponse(
                    request,
                    200,
                    """
                    {
                      "id": "spotify_user_123",
                      "display_name": "Antigravity Music",
                      "email": "user@example.com"
                    }
                    """.trimIndent()
                )
            } else {
                createJsonResponse(request, 404, "{}")
            }
        }

        val profile = fetcher.fetchCurrentUserProfile("valid_bearer_token")
        assertThat(profile).isEqualTo(Pair("spotify_user_123", "Antigravity Music"))
        assertThat(interceptor.recordedRequests).hasSize(1)
        assertThat(interceptor.recordedRequests[0].header("Authorization")).isEqualTo("Bearer valid_bearer_token")
    }

    @Test
    fun fetchCurrentUserProfile_fallbackToIdWhenDisplayNameBlank() = runBlocking {
        interceptor.responseProvider = { request ->
            if (request.url.toString() == "https://api.spotify.com/v1/me") {
                createJsonResponse(
                    request,
                    200,
                    """
                    {
                      "id": "spotify_user_no_name",
                      "display_name": "   "
                    }
                    """.trimIndent()
                )
            } else {
                createJsonResponse(request, 404, "{}")
            }
        }

        val profile = fetcher.fetchCurrentUserProfile("valid_token")
        assertThat(profile).isEqualTo(Pair("spotify_user_no_name", "spotify_user_no_name"))
    }

    @Test
    fun fetchCurrentUserProfile_failureReturnsNull() = runBlocking {
        interceptor.responseProvider = { request ->
            createJsonResponse(request, 401, """{"error": {"status": 401, "message": "The access token expired"}}""")
        }

        val profile = fetcher.fetchCurrentUserProfile("expired_token")
        assertThat(profile).isNull()
    }

    @Test
    fun fetchPlaylist_webApiSuccess_parsesPlaylistAndTracks() = runBlocking {
        interceptor.responseProvider = { request ->
            val url = request.url.toString()
            when {
                url.contains("get_access_token") -> {
                    createJsonResponse(
                        request,
                        200,
                        """
                        {
                          "accessToken": "valid_token",
                          "accessTokenExpirationTimestampMs": ${System.currentTimeMillis() + 3600000}
                        }
                        """.trimIndent()
                    )
                }
                url.contains("/v1/playlists/37i9dQZF1DXcBWIGoYBM5M") -> {
                    createJsonResponse(
                        request,
                        200,
                        """
                        {
                          "id": "37i9dQZF1DXcBWIGoYBM5M",
                          "name": "Today's Top Hits",
                          "description": "Jung Kook is on top of the Hottest 50!",
                          "owner": {
                            "display_name": "Spotify"
                          },
                          "images": [
                            {
                              "url": "https://i.scdn.co/image/cover_large.jpg",
                              "height": 640,
                              "width": 640
                            },
                            {
                              "url": "https://i.scdn.co/image/cover_small.jpg",
                              "height": 300,
                              "width": 300
                            }
                          ],
                          "tracks": {
                            "total": 2,
                            "next": null,
                            "items": [
                              {
                                "track": {
                                  "id": "track_1",
                                  "name": "Seven (feat. Latto)",
                                  "duration_ms": 184400,
                                  "artists": [
                                    { "name": "Jung Kook" },
                                    { "name": "Latto" }
                                  ],
                                  "album": {
                                    "name": "Seven Album",
                                    "images": [
                                      { "url": "https://i.scdn.co/image/album_art_1.jpg" }
                                    ]
                                  }
                                }
                              },
                              {
                                "track": {
                                  "id": "track_2",
                                  "name": "Cruel Summer",
                                  "duration_ms": 178000,
                                  "artists": [
                                    { "name": "Taylor Swift" }
                                  ],
                                  "album": {
                                    "name": "Lover",
                                    "images": [
                                      { "url": "https://i.scdn.co/image/album_art_2.jpg" }
                                    ]
                                  }
                                }
                              }
                            ]
                          }
                        }
                        """.trimIndent()
                    )
                }
                else -> createJsonResponse(request, 404, "{}")
            }
        }

        val result = fetcher.fetchPlaylist("37i9dQZF1DXcBWIGoYBM5M")
        assertThat(result.isSuccess).isTrue()

        val playlist = result.getOrThrow()
        assertThat(playlist.id).isEqualTo("37i9dQZF1DXcBWIGoYBM5M")
        assertThat(playlist.title).isEqualTo("Today's Top Hits")
        assertThat(playlist.description).isEqualTo("Jung Kook is on top of the Hottest 50!")
        assertThat(playlist.author).isEqualTo("Spotify")
        assertThat(playlist.coverUri).isEqualTo("https://i.scdn.co/image/cover_large.jpg")
        assertThat(playlist.trackCount).isEqualTo(2)
        assertThat(playlist.tracks).hasSize(2)

        val track1 = playlist.tracks[0]
        assertThat(track1.id).isEqualTo("track_1")
        assertThat(track1.title).isEqualTo("Seven (feat. Latto)")
        assertThat(track1.artist).isEqualTo("Jung Kook")
        assertThat(track1.artists).containsExactly("Jung Kook", "Latto").inOrder()
        assertThat(track1.album).isEqualTo("Seven Album")
        assertThat(track1.durationMs).isEqualTo(184400L)
        assertThat(track1.coverUri).isEqualTo("https://i.scdn.co/image/album_art_1.jpg")

        val track2 = playlist.tracks[1]
        assertThat(track2.id).isEqualTo("track_2")
        assertThat(track2.title).isEqualTo("Cruel Summer")
        assertThat(track2.artist).isEqualTo("Taylor Swift")
        assertThat(track2.artists).containsExactly("Taylor Swift")
        assertThat(track2.album).isEqualTo("Lover")
        assertThat(track2.durationMs).isEqualTo(178000L)
        assertThat(track2.coverUri).isEqualTo("https://i.scdn.co/image/album_art_2.jpg")
    }

    @Test
    fun fetchPlaylist_webApiPagination_fetchesMultiplePages() = runBlocking {
        interceptor.responseProvider = { request ->
            val url = request.url.toString()
            when {
                url.contains("get_access_token") -> {
                    createJsonResponse(
                        request,
                        200,
                        """{ "accessToken": "valid_token", "accessTokenExpirationTimestampMs": ${System.currentTimeMillis() + 3600000} }"""
                    )
                }
                url == "https://api.spotify.com/v1/playlists/pagination_playlist" -> {
                    createJsonResponse(
                        request,
                        200,
                        """
                        {
                          "id": "pagination_playlist",
                          "name": "Big Playlist",
                          "tracks": {
                            "total": 2,
                            "next": "https://api.spotify.com/v1/playlists/pagination_playlist/tracks?offset=1&limit=1",
                            "items": [
                              {
                                "track": {
                                  "id": "track_page1",
                                  "name": "Song Page 1",
                                  "duration_ms": 200000,
                                  "artists": [{ "name": "Artist 1" }]
                                }
                              }
                            ]
                          }
                        }
                        """.trimIndent()
                    )
                }
                url == "https://api.spotify.com/v1/playlists/pagination_playlist/tracks?offset=1&limit=1" -> {
                    createJsonResponse(
                        request,
                        200,
                        """
                        {
                          "total": 2,
                          "next": null,
                          "items": [
                            {
                              "track": {
                                "id": "track_page2",
                                "name": "Song Page 2",
                                "duration_ms": 210000,
                                "artists": [{ "name": "Artist 2" }]
                              }
                            }
                          ]
                        }
                        """.trimIndent()
                    )
                }
                else -> createJsonResponse(request, 404, "{}")
            }
        }

        val result = fetcher.fetchPlaylist("pagination_playlist")
        assertThat(result.isSuccess).isTrue()
        val playlist = result.getOrThrow()
        assertThat(playlist.tracks).hasSize(2)
        assertThat(playlist.tracks[0].id).isEqualTo("track_page1")
        assertThat(playlist.tracks[1].id).isEqualTo("track_page2")
    }

    @Test
    fun fetchPlaylist_fallbackToEmbedHtml_whenWebApiFails() = runBlocking {
        interceptor.responseProvider = { request ->
            val url = request.url.toString()
            when {
                url.contains("get_access_token") -> createJsonResponse(request, 500, "{}")
                url.contains("/embed/playlist/embed_playlist_id") -> {
                    val embedHtml = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                        <script id="__NEXT_DATA__" type="application/json">
                        {
                          "props": {
                            "pageProps": {
                              "state": {
                                "data": {
                                  "entity": {
                                    "id": "embed_playlist_id",
                                    "title": "Embed Playlist Title",
                                    "subtitle": "Curated by Spotify",
                                    "visualIdentity": {
                                      "image": [
                                        { "url": "https://i.scdn.co/image/embed_cover.jpg" }
                                      ]
                                    },
                                    "trackList": [
                                      {
                                        "id": "embed_track_1",
                                        "title": "Embed Track 1",
                                        "subtitle": "Embed Artist 1, Embed Artist 2",
                                        "duration": 195000,
                                        "album": {
                                          "name": "Embed Album 1",
                                          "coverArt": {
                                            "sources": [
                                              { "url": "https://i.scdn.co/image/embed_track_cover.jpg" }
                                            ]
                                          }
                                        }
                                      }
                                    ]
                                  }
                                }
                              }
                            }
                          }
                        }
                        </script>
                        </head>
                        <body></body>
                        </html>
                    """.trimIndent()
                    createHtmlResponse(request, 200, embedHtml)
                }
                else -> createJsonResponse(request, 404, "{}")
            }
        }

        val result = fetcher.fetchPlaylist("embed_playlist_id")
        assertThat(result.isSuccess).isTrue()

        val playlist = result.getOrThrow()
        assertThat(playlist.id).isEqualTo("embed_playlist_id")
        assertThat(playlist.title).isEqualTo("Embed Playlist Title")
        assertThat(playlist.coverUri).isEqualTo("https://i.scdn.co/image/embed_cover.jpg")
        assertThat(playlist.tracks).hasSize(1)
        assertThat(playlist.tracks[0].id).isEqualTo("embed_track_1")
        assertThat(playlist.tracks[0].title).isEqualTo("Embed Track 1")
        assertThat(playlist.tracks[0].artist).isEqualTo("Embed Artist 1")
        assertThat(playlist.tracks[0].artists).containsExactly("Embed Artist 1", "Embed Artist 2")
        assertThat(playlist.tracks[0].album).isEqualTo("Embed Album 1")
        assertThat(playlist.tracks[0].durationMs).isEqualTo(195000L)
        assertThat(playlist.tracks[0].coverUri).isEqualTo("https://i.scdn.co/image/embed_track_cover.jpg")
    }

    @Test
    fun fetchPlaylist_fallbackToOEmbed_whenEmbedHtmlHasNoNextData() = runBlocking {
        interceptor.responseProvider = { request ->
            val url = request.url.toString()
            when {
                url.contains("get_access_token") -> createJsonResponse(request, 500, "{}")
                url.contains("/embed/playlist/oembed_playlist_id") -> {
                    createHtmlResponse(request, 200, "<html><body>No Next Data Here</body></html>")
                }
                url.contains("/oembed?url=") -> {
                    createJsonResponse(
                        request,
                        200,
                        """
                        {
                          "title": "oEmbed Playlist Title",
                          "thumbnail_url": "https://mosaic.scdn.co/640/oembed_thumb.jpg",
                          "provider_name": "Spotify"
                        }
                        """.trimIndent()
                    )
                }
                else -> createJsonResponse(request, 404, "{}")
            }
        }

        val result = fetcher.fetchPlaylist("oembed_playlist_id")
        assertThat(result.isSuccess).isTrue()
        val playlist = result.getOrThrow()
        assertThat(playlist.id).isEqualTo("oembed_playlist_id")
        assertThat(playlist.title).isEqualTo("oEmbed Playlist Title")
        assertThat(playlist.coverUri).isEqualTo("https://mosaic.scdn.co/640/oembed_thumb.jpg")
    }

    @Test
    fun fetchPlaylist_returnsFailure_whenAllEndpointsFail() = runBlocking {
        interceptor.responseProvider = { request ->
            createJsonResponse(request, 500, "Internal Server Error")
        }

        val result = fetcher.fetchPlaylist("non_existent_id")
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun fetchPlaylist_throwsSpotifyPrivatePlaylistException_on404_whenUnauthenticated() = runBlocking {
        cookiesFlow.value = null
        interceptor.responseProvider = { request ->
            val url = request.url.toString()
            when {
                url.contains("get_access_token") -> {
                    createJsonResponse(
                        request,
                        200,
                        """{"accessToken": "valid_token", "accessTokenExpirationTimestampMs": ${System.currentTimeMillis() + 3600000}}"""
                    )
                }
                url.contains("/v1/playlists/private_playlist_404") -> {
                    createJsonResponse(request, 404, """{"error": {"status": 404, "message": "Not found"}}""")
                }
                url.contains("/embed/playlist/private_playlist_404") -> {
                    createHtmlResponse(request, 404, "<html><body>404 Not Found</body></html>")
                }
                else -> createJsonResponse(request, 404, "{}")
            }
        }

        val result = fetcher.fetchPlaylist("private_playlist_404")
        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull()
        assertThat(exception).isInstanceOf(SpotifyPrivatePlaylistException::class.java)
        val privateException = exception as SpotifyPrivatePlaylistException
        assertThat(privateException.playlistId).isEqualTo("private_playlist_404")
        assertThat(privateException.isUserLoggedIn).isFalse()
        assertThat(privateException.message).contains("Log in with your Spotify account")
    }

    @Test
    fun fetchPlaylist_throwsSpotifyPrivatePlaylistException_on403_whenAuthenticated() = runBlocking {
        cookiesFlow.value = "sp_dc=authenticated_user_cookie"
        interceptor.responseProvider = { request ->
            val url = request.url.toString()
            when {
                url.contains("get_access_token") -> {
                    createJsonResponse(
                        request,
                        200,
                        """{"accessToken": "valid_token", "accessTokenExpirationTimestampMs": ${System.currentTimeMillis() + 3600000}}"""
                    )
                }
                url.contains("/v1/playlists/private_playlist_403") -> {
                    createJsonResponse(request, 403, """{"error": {"status": 403, "message": "Forbidden"}}""")
                }
                url.contains("/embed/playlist/private_playlist_403") -> {
                    createHtmlResponse(request, 403, "<html><body>Forbidden</body></html>")
                }
                else -> createJsonResponse(request, 404, "{}")
            }
        }

        val result = fetcher.fetchPlaylist("private_playlist_403")
        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull()
        assertThat(exception).isInstanceOf(SpotifyPrivatePlaylistException::class.java)
        val privateException = exception as SpotifyPrivatePlaylistException
        assertThat(privateException.playlistId).isEqualTo("private_playlist_403")
        assertThat(privateException.isUserLoggedIn).isTrue()
        assertThat(privateException.message).contains("not accessible by the logged-in Spotify account")
    }

    @Test
    fun fetchPlaylist_usesEmbed_evenIfWebApi404_whenEmbedReturnsValidTracks() = runBlocking {
        interceptor.responseProvider = { request ->
            val url = request.url.toString()
            when {
                url.contains("get_access_token") -> {
                    createJsonResponse(
                        request,
                        200,
                        """{"accessToken": "valid_token", "accessTokenExpirationTimestampMs": ${System.currentTimeMillis() + 3600000}}"""
                    )
                }
                url.contains("/v1/playlists/web_api_404_embed_works") -> {
                    createJsonResponse(request, 404, "{}")
                }
                url.contains("/embed/playlist/web_api_404_embed_works") -> {
                    val embedHtml = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                        <script id="__NEXT_DATA__" type="application/json">
                        {
                          "props": {
                            "pageProps": {
                              "state": {
                                "data": {
                                  "entity": {
                                    "id": "web_api_404_embed_works",
                                    "title": "Embed Worked",
                                    "trackList": [
                                      {
                                        "id": "trk_1",
                                        "title": "Embed Track",
                                        "subtitle": "Embed Artist",
                                        "duration": 180000
                                      }
                                    ]
                                  }
                                }
                              }
                            }
                          }
                        }
                        </script>
                        </head>
                        <body></body>
                        </html>
                    """.trimIndent()
                    createHtmlResponse(request, 200, embedHtml)
                }
                else -> createJsonResponse(request, 404, "{}")
            }
        }

        val result = fetcher.fetchPlaylist("web_api_404_embed_works")
        assertThat(result.isSuccess).isTrue()
        val playlist = result.getOrThrow()
        assertThat(playlist.id).isEqualTo("web_api_404_embed_works")
        assertThat(playlist.title).isEqualTo("Embed Worked")
        assertThat(playlist.tracks).hasSize(1)
        assertThat(playlist.tracks[0].title).isEqualTo("Embed Track")
    }
}

