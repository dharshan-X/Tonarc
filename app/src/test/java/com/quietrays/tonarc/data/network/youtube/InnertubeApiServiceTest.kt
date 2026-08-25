package com.quietrays.tonarc.data.network.youtube

import com.google.common.truth.Truth.assertThat
import com.quietrays.tonarc.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import java.io.IOException

class InnertubeApiServiceTest {

    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private val cookiesFlow = MutableStateFlow<String?>(null)
    private val visitorDataFlow = MutableStateFlow<String?>(null)
    private lateinit var interceptor: TestInterceptor
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var apiService: InnertubeApiService

    private class TestInterceptor : Interceptor {
        val recordedRequests = mutableListOf<Request>()
        var responseProvider: (Request) -> Response = { request ->
            createSuccessResponse(request, "{}")
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            recordedRequests.add(request)
            return responseProvider(request)
        }
    }

    companion object {
        private fun createSuccessResponse(request: Request, bodyJson: String): Response {
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(bodyJson.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }

        private fun createErrorResponse(request: Request, code: Int, bodyJson: String = "{}"): Response {
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("Error")
                .body(bodyJson.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }

        private fun Request.readBodyJson(): JSONObject {
            val buffer = Buffer()
            this.body?.writeTo(buffer)
            return JSONObject(buffer.readUtf8())
        }
    }

    @Before
    fun setUp() {
        cookiesFlow.value = null
        visitorDataFlow.value = null

        userPreferencesRepository = mockk(relaxed = true)
        every { userPreferencesRepository.youTubeAuthCookiesFlow } returns cookiesFlow
        every { userPreferencesRepository.youTubeVisitorDataFlow } returns visitorDataFlow

        interceptor = TestInterceptor()
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        apiService = InnertubeApiService(
            baseOkHttpClient = okHttpClient,
            userPreferencesRepository = userPreferencesRepository,
            scope = CoroutineScope(Dispatchers.Unconfined)
        )
    }

    @Test
    fun `setLikeStatus when isLiked is true posts to like_like endpoint with target videoId and base context`() = runBlocking {
        interceptor.responseProvider = { request ->
            createSuccessResponse(request, """{"status": "STATUS_SUCCEEDED"}""")
        }

        val success = apiService.setLikeStatus("video_123", isLiked = true)

        assertThat(success).isTrue()
        assertThat(interceptor.recordedRequests).hasSize(1)

        val request = interceptor.recordedRequests.first()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.url.toString()).isEqualTo("https://music.youtube.com/youtubei/v1/like/like?prettyPrint=false")
        assertThat(request.header("X-YouTube-Client-Name")).isEqualTo("67")
        assertThat(request.header("X-YouTube-Client-Version")).isEqualTo("1.20240301.01.00")
        assertThat(request.header("User-Agent")).isNotEmpty()
        assertThat(request.header("Referer")).isEqualTo("https://music.youtube.com/")
        assertThat(request.header("Origin")).isEqualTo("https://music.youtube.com")

        val bodyJson = request.readBodyJson()
        assertThat(bodyJson.has("target")).isTrue()
        val targetObj = bodyJson.getJSONObject("target")
        assertThat(targetObj.getString("videoId")).isEqualTo("video_123")

        assertThat(bodyJson.has("context")).isTrue()
        val clientObj = bodyJson.getJSONObject("context").getJSONObject("client")
        assertThat(clientObj.getString("clientName")).isEqualTo("WEB_REMIX")
        assertThat(clientObj.getString("clientVersion")).isEqualTo("1.20240301.01.00")
        assertThat(clientObj.getString("hl")).isEqualTo("en")
        assertThat(clientObj.getString("gl")).isEqualTo("US")
    }

    @Test
    fun `setLikeStatus when isLiked is false posts to like_removelike endpoint with target videoId and base context`() = runBlocking {
        interceptor.responseProvider = { request ->
            createSuccessResponse(request, """{"status": "STATUS_SUCCEEDED"}""")
        }

        val success = apiService.setLikeStatus("video_456", isLiked = false)

        assertThat(success).isTrue()
        assertThat(interceptor.recordedRequests).hasSize(1)

        val request = interceptor.recordedRequests.first()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.url.toString()).isEqualTo("https://music.youtube.com/youtubei/v1/like/removelike?prettyPrint=false")

        val bodyJson = request.readBodyJson()
        val targetObj = bodyJson.getJSONObject("target")
        assertThat(targetObj.getString("videoId")).isEqualTo("video_456")

        val clientObj = bodyJson.getJSONObject("context").getJSONObject("client")
        assertThat(clientObj.getString("clientName")).isEqualTo("WEB_REMIX")
    }

    @Test
    fun `setLikeStatus includes Cookie and Authorization headers when authCookies are set`() = runBlocking {
        cookiesFlow.value = "SAPISID=sample_sapisid_value_123; SID=sample_sid; HSID=sample_hsid"
        apiService.authCookies = cookiesFlow.value

        interceptor.responseProvider = { request ->
            createSuccessResponse(request, "{}")
        }

        val success = apiService.setLikeStatus("video_auth_test", isLiked = true)

        assertThat(success).isTrue()
        val request = interceptor.recordedRequests.first()
        assertThat(request.header("Cookie")).isEqualTo("SAPISID=sample_sapisid_value_123; SID=sample_sid; HSID=sample_hsid")

        val authHeader = request.header("Authorization")
        assertThat(authHeader).isNotNull()
        assertThat(authHeader).startsWith("SAPISIDHASH ")
        assertThat(authHeader).matches("SAPISIDHASH \\d+_[a-f0-9]{40}")
    }

    @Test
    fun `setLikeStatus includes visitorData header and payload when visitorData is set`() = runBlocking {
        visitorDataFlow.value = "visitor_token_abc_xyz"
        apiService.visitorData = visitorDataFlow.value

        interceptor.responseProvider = { request ->
            createSuccessResponse(request, "{}")
        }

        val success = apiService.setLikeStatus("video_visitor_test", isLiked = true)

        assertThat(success).isTrue()
        val request = interceptor.recordedRequests.first()
        assertThat(request.header("X-YouTube-Visitor-Data")).isEqualTo("visitor_token_abc_xyz")

        val bodyJson = request.readBodyJson()
        val clientObj = bodyJson.getJSONObject("context").getJSONObject("client")
        assertThat(clientObj.optString("visitorData")).isEqualTo("visitor_token_abc_xyz")
    }

    @Test
    fun `setLikeStatus extracts and updates visitorData from response`() = runBlocking {
        val responseWithVisitorData = """
            {
                "responseContext": {
                    "visitorData": "new_extracted_visitor_token_999"
                }
            }
        """.trimIndent()

        interceptor.responseProvider = { request ->
            createSuccessResponse(request, responseWithVisitorData)
        }

        val success = apiService.setLikeStatus("video_extract_visitor", isLiked = true)

        assertThat(success).isTrue()
        assertThat(apiService.visitorData).isEqualTo("new_extracted_visitor_token_999")
    }

    @Test
    fun `setLikeStatus returns false when server returns HTTP error status`() = runBlocking {
        interceptor.responseProvider = { request ->
            createErrorResponse(request, 401, """{"error": {"code": 401, "message": "Unauthorized"}}""")
        }

        val success = apiService.setLikeStatus("video_unauthorized", isLiked = true)

        assertThat(success).isFalse()
    }

    @Test
    fun `setLikeStatus returns false when network throws exception`() = runBlocking {
        interceptor.responseProvider = { _ ->
            throw IOException("Network connection aborted")
        }

        val success = apiService.setLikeStatus("video_network_err", isLiked = false)

        assertThat(success).isFalse()
    }
}
