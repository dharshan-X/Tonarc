package com.quietrays.tonarc.data.worker

import android.content.Context
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.quietrays.tonarc.data.database.OfflineTrackDao
import com.quietrays.tonarc.data.database.LyricsDao
import com.quietrays.tonarc.data.database.LyricsEntity
import com.quietrays.tonarc.data.database.YouTubeDao
import com.quietrays.tonarc.data.database.YouTubeSongEntity
import com.quietrays.tonarc.data.jellyfin.JellyfinRepository
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.navidrome.NavidromeRepository
import com.quietrays.tonarc.data.offline.CloudOfflineRepository
import com.quietrays.tonarc.data.offline.OfflineDownloadStatus
import com.quietrays.tonarc.data.repository.LyricsRepository
import com.quietrays.tonarc.data.stream.CloudStreamSecurity
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.TimeUnit
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber
import kotlin.coroutines.coroutineContext

@HiltWorker
class CloudTrackDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dao: OfflineTrackDao,
    private val navidromeRepository: NavidromeRepository,
    private val jellyfinRepository: JellyfinRepository,
    private val youTubeRepository: com.quietrays.tonarc.data.youtube.YouTubeRepository,
    private val lyricsRepository: LyricsRepository,
    private val lyricsDao: LyricsDao,
    private val youTubeDao: YouTubeDao,
    baseOkHttpClient: OkHttpClient
) : CoroutineWorker(appContext, workerParams) {
    private val client = baseOkHttpClient.newBuilder()
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 32
        })
        .connectionPool(okhttp3.ConnectionPool(32, 5, TimeUnit.MINUTES))
        .readTimeout(2, TimeUnit.MINUTES)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID)
            ?: return@withContext Result.failure()
        val attemptId = inputData.getString(KEY_ATTEMPT_ID)
            ?: return@withContext Result.failure()
        val sourceUri = inputData.getString(KEY_SOURCE_URI)
            ?: return@withContext Result.failure()
        val entity = dao.getByDownloadId(downloadId)
            ?: return@withContext Result.success()
        if (entity.attemptId != attemptId || entity.sourceUri != sourceUri) {
            return@withContext Result.success()
        }

        val now = System.currentTimeMillis()
        if (dao.updateState(
            downloadId = downloadId,
            attemptId = attemptId,
            state = OfflineDownloadStatus.DOWNLOADING.storageValue,
            bytesDownloaded = 0L,
            totalBytes = null,
            localPath = null,
            errorMessage = null,
            updatedAt = now
        ) == 0) return@withContext Result.success()

        val tempFile = CloudOfflineRepository.downloadDirectory(applicationContext)
            .resolve("${CloudOfflineRepository.attemptFileStem(downloadId, attemptId)}.part")
        tempFile.delete()
        var finalizedFile: File? = null

        try {
            val source = resolveSource(sourceUri)
            val requestBuilder = Request.Builder().url(source.url)
            source.headers.forEach { (name, value) -> requestBuilder.header(name, value) }

            client.newCall(requestBuilder.get().build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw DownloadHttpException(response.code)
                }
                if (!CloudStreamSecurity.isSupportedAudioContentType(response.header("Content-Type"))) {
                    throw IOException("Server returned a non-audio response")
                }
                if (!CloudStreamSecurity.isAcceptableContentLength(response.header("Content-Length"))) {
                    throw IOException("Audio file is too large")
                }

                val body = response.body
                val total = body.contentLength().takeIf { it >= 0L }
                val extension = extensionFor(response.header("Content-Type"), entity.mimeType)
                val finalFile = CloudOfflineRepository.downloadDirectory(applicationContext)
                    .resolve(
                        "${CloudOfflineRepository.attemptFileStem(downloadId, attemptId)}.$extension"
                    )
                var copied = 0L
                var lastPublishedTime = 0L

                body.byteStream().use { input ->
                    tempFile.outputStream().buffered(128 * 1024).use { output ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            copied += count
                            if (copied > CloudStreamSecurity.MAX_STREAM_CONTENT_LENGTH_BYTES) {
                                throw IOException("Audio file is too large")
                            }
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastPublishedTime >= 500L) {
                                publishProgress(downloadId, attemptId, copied, total)
                                lastPublishedTime = currentTime
                            }
                        }
                    }
                }

                if (copied <= 0L) throw IOException("Downloaded file is empty")
                if (total != null && copied < total) {
                    throw IOException("Download ended early ($copied/$total bytes)")
                }
                coroutineContext.ensureActive()
                if (!dao.isCurrentAttempt(downloadId, attemptId)) {
                    throw StaleDownloadAttemptException()
                }
                finalFile.delete()
                if (!tempFile.renameTo(finalFile)) {
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                }
                finalizedFile = finalFile
                coroutineContext.ensureActive()
                val completed = dao.updateState(
                    downloadId = downloadId,
                    attemptId = attemptId,
                    state = OfflineDownloadStatus.COMPLETE.storageValue,
                    bytesDownloaded = copied,
                    totalBytes = total ?: copied,
                    localPath = finalFile.absolutePath,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis()
                )
                if (completed == 0) {
                    finalFile.delete()
                } else {
                    // Post-process metadata, artwork image, and lyrics
                    val title = inputData.getString(KEY_TITLE)?.takeIf { it.isNotBlank() } ?: entity.title
                    val artist = inputData.getString(KEY_ARTIST)?.takeIf { it.isNotBlank() } ?: ""
                    val album = inputData.getString(KEY_ALBUM)?.takeIf { it.isNotBlank() } ?: "Offline Downloads"
                    val artworkUri = inputData.getString(KEY_ARTWORK_URI)?.takeIf { it.isNotBlank() }
                    val youtubeId = inputData.getString(KEY_YOUTUBE_ID)?.takeIf { it.isNotBlank() }
                        ?: if (sourceUri.startsWith("youtube://")) sourceUri.removePrefix("youtube://") else null

                    postProcessDownloadedTrack(
                        file = finalFile,
                        downloadId = downloadId,
                        attemptId = attemptId,
                        sourceUri = sourceUri,
                        title = title,
                        artist = artist,
                        album = album,
                        artworkUri = artworkUri,
                        youtubeId = youtubeId,
                        mimeType = entity.mimeType
                    )
                }
                Result.success()
            }
        } catch (cancelled: CancellationException) {
            tempFile.delete()
            finalizedFile?.delete()
            throw cancelled
        } catch (_: StaleDownloadAttemptException) {
            tempFile.delete()
            finalizedFile?.delete()
            Result.success()
        } catch (error: Throwable) {
            tempFile.delete()
            finalizedFile?.delete()
            val shouldRetry = runAttemptCount < MAX_RETRIES &&
                (error is IOException || (error is DownloadHttpException && error.code >= 500))
            Timber.tag(TAG).w(error, "Cloud track download failed for %s", sourceUri)
            val updated = dao.updateState(
                downloadId = downloadId,
                attemptId = attemptId,
                state = if (shouldRetry) {
                    OfflineDownloadStatus.QUEUED.storageValue
                } else {
                    OfflineDownloadStatus.FAILED.storageValue
                },
                bytesDownloaded = 0L,
                totalBytes = null,
                localPath = null,
                errorMessage = error.message ?: error.javaClass.simpleName,
                updatedAt = System.currentTimeMillis()
            )
            when {
                updated == 0 -> Result.success()
                shouldRetry -> Result.retry()
                else -> Result.failure(
                    workDataOf(KEY_ERROR to (error.message ?: "Download failed"))
                )
            }
        }
    }

    private suspend fun postProcessDownloadedTrack(
        file: File,
        downloadId: String,
        attemptId: String,
        sourceUri: String,
        title: String,
        artist: String,
        album: String,
        artworkUri: String?,
        youtubeId: String?,
        mimeType: String?
    ) {
        val fileStem = CloudOfflineRepository.attemptFileStem(downloadId, attemptId)
        val downloadDir = CloudOfflineRepository.downloadDirectory(applicationContext)

        // 1. Download and save cover image
        var pictureBytes: ByteArray? = null
        var pictureMime: String = "image/jpeg"
        val effectiveArtUrl = artworkUri ?: (youtubeId?.let { "https://i.ytimg.com/vi/$it/maxresdefault.jpg" })
        if (!effectiveArtUrl.isNullOrBlank()) {
            try {
                val artReq = Request.Builder().url(effectiveArtUrl).build()
                client.newCall(artReq).execute().use { artResp ->
                    if (artResp.isSuccessful) {
                        val bytes = artResp.body.bytes()
                        if (bytes != null && bytes.isNotEmpty()) {
                            pictureBytes = bytes
                            pictureMime = artResp.header("Content-Type")?.substringBefore(';') ?: "image/jpeg"
                            val imageFile = downloadDir.resolve("$fileStem.jpg")
                            imageFile.writeBytes(bytes)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to download cover art for $sourceUri")
            }
        }

        // 2. Download and save lyrics (synced LRC format)
        var lyricsContent: String? = null
        var isLyricsSynced = false
        try {
            val tempSong = Song(
                id = if (!youtubeId.isNullOrBlank()) "youtube_$youtubeId" else downloadId,
                title = title,
                artist = artist,
                artistId = 0L,
                album = album,
                albumId = 0L,
                albumArtist = artist,
                path = file.absolutePath,
                contentUriString = sourceUri,
                albumArtUriString = effectiveArtUrl,
                duration = 0L,
                youtubeId = youtubeId,
                mimeType = mimeType,
                bitrate = null,
                sampleRate = null
            )
            val lyricsResult = lyricsRepository.getLyrics(tempSong, forceRefresh = true)
            if (lyricsResult != null) {
                val lrcBuilder = StringBuilder()
                if (!lyricsResult.synced.isNullOrEmpty()) {
                    isLyricsSynced = true
                    lyricsResult.synced.forEach { syncedLine ->
                        val startMs = syncedLine.time
                        val min = (startMs / 60000)
                        val sec = ((startMs % 60000) / 1000)
                        val ms = ((startMs % 1000) / 10)
                        lrcBuilder.append(String.format(Locale.US, "[%02d:%02d.%02d]%s\n", min, sec, ms, syncedLine.line))
                    }
                } else if (!lyricsResult.plain.isNullOrEmpty()) {
                    lrcBuilder.append(lyricsResult.plain.joinToString("\n"))
                }

                val formattedLyrics = lrcBuilder.toString().trim()
                if (formattedLyrics.isNotBlank()) {
                    lyricsContent = formattedLyrics
                    val lrcFile = downloadDir.resolve("$fileStem.lrc")
                    lrcFile.writeText(formattedLyrics)

                    try {
                        val numericSongId = Math.abs(tempSong.id.hashCode().toLong().takeIf { it != 0L } ?: 1L)
                        lyricsDao.insert(
                            LyricsEntity(
                                songId = numericSongId,
                                content = formattedLyrics,
                                isSynced = isLyricsSynced,
                                source = "download"
                            )
                        )
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to download lyrics for $sourceUri")
        }

        // 3. Embed metadata tags, lyrics, and artwork picture using TagLib
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).use { fd ->
                val metadata = TagLib.getMetadata(fd.dup().detachFd(), readPictures = false)
                val propertyMap = HashMap(metadata?.propertyMap ?: emptyMap())

                if (title.isNotBlank()) propertyMap["TITLE"] = arrayOf(title)
                if (artist.isNotBlank()) propertyMap["ARTIST"] = arrayOf(artist)
                if (album.isNotBlank()) propertyMap["ALBUM"] = arrayOf(album)
                lyricsContent?.let {
                    propertyMap["LYRICS"] = arrayOf(it)
                    if (isLyricsSynced) {
                        propertyMap["SYNCEDLYRICS"] = arrayOf(it)
                    }
                }

                TagLib.savePropertyMap(fd.dup().detachFd(), propertyMap)

                pictureBytes?.let { bytes ->
                    val pic = Picture(
                        data = bytes,
                        description = "Front Cover",
                        pictureType = "Front Cover",
                        mimeType = pictureMime
                    )
                    TagLib.savePictures(fd.dup().detachFd(), arrayOf(pic))
                }
            }
            RandomAccessFile(file, "rw").use { it.fd.sync() }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to embed TagLib metadata for $file")
        }

        // 4. Save/Update in YouTubeDao if YouTube track
        if (!youtubeId.isNullOrBlank()) {
            try {
                youTubeDao.insertSong(
                    YouTubeSongEntity(
                        id = "youtube_$youtubeId",
                        videoId = youtubeId,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = 0L,
                        thumbnailUrl = effectiveArtUrl
                    )
                )
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to save YouTube song entity")
            }
        }
    }

    private suspend fun publishProgress(
        downloadId: String,
        attemptId: String,
        copied: Long,
        total: Long?
    ) {
        val updated = dao.updateState(
            downloadId = downloadId,
            attemptId = attemptId,
            state = OfflineDownloadStatus.DOWNLOADING.storageValue,
            bytesDownloaded = copied,
            totalBytes = total,
            localPath = null,
            errorMessage = null,
            updatedAt = System.currentTimeMillis()
        )
        if (updated == 0) throw StaleDownloadAttemptException()
        setProgress(workDataOf(KEY_BYTES to copied, KEY_TOTAL_BYTES to (total ?: -1L)))
    }

    private suspend fun resolveSource(sourceUri: String): DownloadSource {
        val parsed = sourceUri.toUri()
        val id = parsed.host ?: parsed.path?.removePrefix("/")
            ?: throw IOException("Cloud track identifier is missing")
        return when (parsed.scheme?.lowercase()) {
            "navidrome" -> {
                if (!CloudStreamSecurity.validateNavidromeSongId(id)) {
                    throw IOException("Invalid Navidrome track identifier")
                }
                DownloadSource(
                    url = navidromeRepository.getStreamUrl(id),
                    allowedHost = navidromeRepository.serverUrl
                )
            }
            "jellyfin" -> {
                if (!CloudStreamSecurity.validateJellyfinItemId(id)) {
                    throw IOException("Invalid Jellyfin track identifier")
                }
                DownloadSource(
                    url = jellyfinRepository.getStreamUrl(id),
                    headers = jellyfinRepository.getAuthorizationHeader()
                        ?.let { mapOf("Authorization" to it) }
                        .orEmpty(),
                    allowedHost = jellyfinRepository.serverUrl
                )
            }
            "youtube" -> {
                if (!CloudStreamSecurity.validateYouTubeVideoId(id)) {
                    throw IOException("Invalid YouTube video identifier")
                }
                val streamUrl = youTubeRepository.getStreamUrl(id)
                    ?: throw IOException("Failed to resolve YouTube stream URL")
                val headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                )
                DownloadSource(
                    url = streamUrl,
                    headers = headers,
                    allowedHost = "https://googlevideo.com"
                )
            }
            else -> throw IOException("Unsupported cloud provider")
        }.also { source ->
            val host = source.allowedHost
                ?.toHttpUrlOrNull()
                ?.host
                ?: throw IOException("Cloud account is not connected")
            if (!CloudStreamSecurity.isSafeRemoteStreamUrl(
                    url = source.url,
                    allowedHostSuffixes = setOf(host, "googlevideo.com", "youtube.com"),
                    allowHttpForAllowedHosts = true
                )
            ) {
                throw IOException("Unsafe cloud download URL")
            }
        }
    }

    private fun extensionFor(responseType: String?, fallbackType: String?): String {
        val type = responseType?.substringBefore(';')?.lowercase()
            ?: fallbackType?.substringBefore(';')?.lowercase()
        return when (type) {
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/ogg", "application/ogg" -> "ogg"
            "audio/opus" -> "opus"
            "audio/mp4", "audio/m4a", "audio/x-m4a", "application/mp4", "video/mp4" -> "m4a"
            "audio/aac", "audio/aacp" -> "aac"
            "audio/wav", "audio/x-wav" -> "wav"
            "audio/webm" -> "webm"
            else -> "mp3"
        }
    }

    private data class DownloadSource(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val allowedHost: String?
    )

    private class DownloadHttpException(val code: Int) : IOException("Server returned HTTP $code")
    private class StaleDownloadAttemptException : Exception()

    companion object {
        const val TAG = "cloud_track_download"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_ATTEMPT_ID = "attempt_id"
        const val KEY_SOURCE_URI = "source_uri"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_ALBUM = "album"
        const val KEY_ARTWORK_URI = "artwork_uri"
        const val KEY_YOUTUBE_ID = "youtube_id"
        const val KEY_BYTES = "bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_ERROR = "error"
        private const val PROGRESS_STEP_BYTES = 512L * 1024L
        private const val MAX_RETRIES = 3
    }
}
