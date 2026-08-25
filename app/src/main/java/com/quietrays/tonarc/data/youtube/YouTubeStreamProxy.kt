package com.quietrays.tonarc.data.youtube

import android.net.Uri
import com.quietrays.tonarc.data.network.youtube.InnertubeApiService
import com.quietrays.tonarc.data.stream.CloudStreamProxy
import com.quietrays.tonarc.data.stream.CloudStreamSecurity
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

import com.quietrays.tonarc.data.network.youtube.YouTubeExtractorManager

import com.quietrays.tonarc.data.stream.StreamDiskCache

/**
 * Local HTTP proxy server for streaming YouTube Music audio.
 *
 * Resolves `youtube://{videoId}` URIs by generating dynamic streaming URLs
 * via [YouTubeExtractorManager] (with [InnertubeApiService] fallback) and proxying chunked audio data to ExoPlayer.
 */
@Singleton
class YouTubeStreamProxy @Inject constructor(
    private val innertubeApiService: InnertubeApiService,
    private val youTubeExtractorManager: YouTubeExtractorManager,
    diskCache: StreamDiskCache,
    okHttpClient: OkHttpClient
) : CloudStreamProxy<String>(okHttpClient, diskCache) {

    override val allowedHostSuffixes: Set<String> = setOf(
        "googlevideo.com",
        "youtube.com",
        "ytimg.com"
    )

    override val cacheExpirationMs: Long = 4L * 3600 * 1000L // 4 hours fallback

    override fun extractExpirationMs(id: String, url: String, defaultExpirationMs: Long): Long {
        return try {
            val uri = Uri.parse(url)
            val expireParam = uri.getQueryParameter("expire")?.toLongOrNull()
            if (expireParam != null && expireParam > 0) {
                val expireEpochMs = expireParam * 1000L
                val remainingMs = expireEpochMs - System.currentTimeMillis()
                // Leave a 5-minute safety margin before true upstream expiration
                val safeRemainingMs = remainingMs - 5 * 60 * 1000L
                if (safeRemainingMs > 0) {
                    minOf(safeRemainingMs, defaultExpirationMs)
                } else {
                    60_000L // Minimum 1 minute
                }
            } else {
                defaultExpirationMs
            }
        } catch (_: Exception) {
            defaultExpirationMs
        }
    }

    override fun upstreamHeaders(): Map<String, String> {
        val headers = mutableMapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            "Origin" to "https://music.youtube.com",
            "Referer" to "https://music.youtube.com/"
        )
        innertubeApiService.authCookies?.takeIf { it.isNotBlank() }?.let {
            headers["Cookie"] = it
        }
        return headers
    }

    override val proxyTag: String = "YouTubeStreamProxy"
    override val routePath: String = "/youtube/{videoId}"
    override val routeParamName: String = "videoId"
    override val uriScheme: String = "youtube"
    override val routePrefix: String = "/youtube"

    override fun parseRouteParam(value: String): String? =
        value.takeIf { it.isNotBlank() }

    override fun validateId(id: String): Boolean =
        CloudStreamSecurity.validateYouTubeVideoId(id)

    override fun formatIdForUrl(id: String): String = id

    override suspend fun resolveStreamUrl(id: String): String? {
        android.util.Log.d("YouTubeMusic", "YouTubeStreamProxy resolving stream URL for videoId=$id")
        return try {
            // 1. Primary: Use NewPipeExtractor (handles n-sig, cipher deobfuscation, full formats)
            val extractedUrl = youTubeExtractorManager.extractAudioStreamUrl(id)
            if (!extractedUrl.isNullOrBlank()) {
                android.util.Log.d("YouTubeMusic", "YouTubeStreamProxy resolved url via NewPipeExtractor for videoId=$id")
                return extractedUrl
            }

            // 2. Fallback: Use Innertube API
            val info = innertubeApiService.getStreamInfo(id)
            val streamUrl = info?.selectedFormatUrl ?: info?.highestBitrateOpusUrl ?: info?.highestBitrateAacUrl
            android.util.Log.d("YouTubeMusic", "YouTubeStreamProxy resolved url via Innertube for videoId=$id: found=${streamUrl != null}")
            streamUrl
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.e("YouTubeMusic", "Failed to resolve stream URL for videoId: $id", e)
            Timber.tag(proxyTag).w(e, "Failed to resolve stream URL for videoId: $id")
            null
        }
    }

    override fun extractIdFromUri(uri: Uri): String? =
        uri.host ?: uri.path?.removePrefix("/")

    fun resolveYouTubeUri(uriString: String): String? {
        val proxyUri = resolveUri(uriString)
        android.util.Log.d("YouTubeMusic", "resolveYouTubeUri: in='$uriString' -> out='$proxyUri'")
        return proxyUri
    }

    /**
     * Pre-fetches and caches the real stream URL for a track so the proxy can
     * serve it instantly when ExoPlayer makes its HTTP request.
     */
    suspend fun warmUpStreamUrl(uriString: String) {
        val uri = Uri.parse(uriString)
        if (uri.scheme != "youtube") return
        val videoId = uri.host ?: uri.path?.removePrefix("/") ?: return
        if (!CloudStreamSecurity.validateYouTubeVideoId(videoId)) return
        try {
            android.util.Log.d("YouTubeMusic", "Warming up stream URL for $videoId")
            getOrFetchStreamUrl(videoId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            android.util.Log.w("YouTubeMusic", "warmUpStreamUrl failed for $videoId", e)
            Timber.tag(proxyTag).w(e, "warmUpStreamUrl failed for $videoId")
        }
    }
}
