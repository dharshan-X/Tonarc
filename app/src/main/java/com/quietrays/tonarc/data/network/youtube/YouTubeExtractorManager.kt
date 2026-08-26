package com.quietrays.tonarc.data.network.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages audio stream extraction using NewPipeExtractor with automated n-sig & cipher deobfuscation.
 */
@Singleton
class YouTubeExtractorManager @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val isInitialized = AtomicBoolean(false)

    private fun ensureInitialized() {
        if (isInitialized.compareAndSet(false, true)) {
            try {
                NewPipe.init(NewPipeDownloader(okHttpClient))
                android.util.Log.d("YouTubeMusic", "NewPipeExtractor initialized successfully")
            } catch (e: Exception) {
                android.util.Log.e("YouTubeMusic", "Failed to initialize NewPipeExtractor", e)
            }
        }
    }

    /**
     * Resolves the direct audio stream URL for a given YouTube video ID.
     */
    suspend fun extractAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        ensureInitialized()
        try {
            android.util.Log.d("YouTubeMusic", "Extracting stream with NewPipeExtractor for videoId: $videoId")
            val watchUrl = "https://www.youtube.com/watch?v=$videoId"
            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, watchUrl)
            val audioStreams = streamInfo.audioStreams

            android.util.Log.d("YouTubeMusic", "NewPipeExtractor found ${audioStreams.size} audio streams for $videoId")

            // Select maximum quality stream (prefer highest effective bitrate Opus 160k / AAC 256k)
            val selectedStream = audioStreams
                .filter { !it.content.isNullOrBlank() }
                .maxByOrNull { stream ->
                    val rawBitrate = maxOf(stream.bitrate, stream.averageBitrate, 0)
                    val isOpus = stream.format == org.schabi.newpipe.extractor.MediaFormat.WEBMA_OPUS ||
                        stream.codec?.contains("opus", ignoreCase = true) == true
                    val isHighQualityAac = rawBitrate >= 256000
                    val isStandardAac = stream.format == org.schabi.newpipe.extractor.MediaFormat.M4A ||
                        stream.codec?.contains("mp4a", ignoreCase = true) == true ||
                        stream.codec?.contains("aac", ignoreCase = true) == true

                    val fidelityScore = when {
                        isHighQualityAac -> rawBitrate * 1.05
                        isOpus -> rawBitrate * 1.25 // 160k Opus achieves higher perceptual fidelity than 128k AAC
                        isStandardAac -> rawBitrate * 1.0
                        else -> rawBitrate * 0.9
                    }
                    fidelityScore.toInt()
                } ?: audioStreams.firstOrNull { !it.content.isNullOrBlank() }

            val resolvedUrl = selectedStream?.content
            val effectiveBitrate = selectedStream?.let { maxOf(it.bitrate, it.averageBitrate, 0) } ?: 0
            android.util.Log.d(
                "YouTubeMusic",
                "NewPipeExtractor selected max quality stream: format=${selectedStream?.format?.name}, codec=${selectedStream?.codec}, bitrate=${effectiveBitrate}bps"
            )
            resolvedUrl
        } catch (e: Exception) {
            android.util.Log.e("YouTubeMusic", "NewPipeExtractor failed for videoId: $videoId", e)
            null
        }
    }
}
