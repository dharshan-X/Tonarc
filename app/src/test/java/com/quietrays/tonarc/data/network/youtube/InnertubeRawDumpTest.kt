package com.quietrays.tonarc.data.network.youtube

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Ignore
import org.junit.Test
import java.io.File

@Ignore("Manual live dump test")
class InnertubeRawDumpTest {

    @Test
    fun dumpRawResponses() = runBlocking {
        val client = OkHttpClient()
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()

        // 1. Search Request with WEB_REMIX
        val searchContext = JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "WEB_REMIX")
                put("clientVersion", "1.20240301.01.00")
                put("hl", "en")
                put("gl", "US")
            })
        }
        val searchBody = JSONObject().apply {
            put("context", searchContext)
            put("query", "Adele")
        }

        val searchReq = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
            .post(searchBody.toString().toRequestBody(jsonMediaType))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .header("Referer", "https://music.youtube.com/")
            .header("Origin", "https://music.youtube.com")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", "1.20240301.01.00")
            .build()

        val searchResp = client.newCall(searchReq).execute()
        val searchJson = searchResp.body?.string() ?: ""
        File("/home/dharshan/PixelPlayerOSS/search_dump.json").writeText(searchJson)
        println("Search Dump size: ${searchJson.length} bytes")
    }
}
