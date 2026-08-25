package com.quietrays.tonarc.data.stream

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * High-performance, LRU-evicting disk cache for cloud and YouTube Music audio streams.
 *
 * Provides:
 * - Instant chunk caching during playback.
 * - Zero-latency, zero-network serving of previously played tracks.
 * - Safe byte-range seeking via RandomAccessFile.
 * - Thread-safe access with ReentrantReadWriteLock.
 * - Automatic LRU trimming to respect disk budget.
 */
@Singleton
class StreamDiskCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val DEFAULT_MAX_CACHE_SIZE_BYTES = 1024L * 1024L * 1024L // 1 GB
        private const val CACHE_DIR_NAME = "stream_audio_cache"
        private const val TAG = "StreamDiskCache"
    }

    private val cacheDir: File by lazy {
        File(context.cacheDir, CACHE_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    private val lock = ReentrantReadWriteLock()
    private var maxCacheSizeBytes: Long = DEFAULT_MAX_CACHE_SIZE_BYTES

    fun setMaxCacheSizeBytes(maxBytes: Long) {
        maxCacheSizeBytes = maxBytes.coerceAtLeast(50L * 1024 * 1024)
        trimToSize(maxCacheSizeBytes)
    }

    private fun hashKey(key: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(key.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            key.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        }
    }

    /**
     * Checks if a completed cache file exists for [key].
     * Touches the file's last modified timestamp to maintain LRU order.
     */
    fun getCachedFile(key: String): File? = lock.read {
        val file = File(cacheDir, "${hashKey(key)}.audio")
        if (file.exists() && file.length() > 0) {
            file.setLastModified(System.currentTimeMillis())
            file
        } else {
            null
        }
    }

    /**
     * Creates a temporary file for writing a stream in progress.
     */
    fun createTempFile(key: String): File {
        return File(cacheDir, "${hashKey(key)}_${System.currentTimeMillis()}.tmp")
    }

    /**
     * Commits a temporary file as the authoritative cache file for [key].
     */
    fun commitTempFile(tempFile: File, key: String): File? = lock.write {
        if (!tempFile.exists() || tempFile.length() <= 0) {
            tempFile.delete()
            return@write null
        }
        val targetFile = File(cacheDir, "${hashKey(key)}.audio")
        if (targetFile.exists()) {
            targetFile.delete()
        }
        val success = tempFile.renameTo(targetFile)
        if (success) {
            targetFile.setLastModified(System.currentTimeMillis())
            trimToSize(maxCacheSizeBytes)
            Timber.tag(TAG).d("Committed stream cache for key=%s, size=%d bytes", key, targetFile.length())
            targetFile
        } else {
            tempFile.delete()
            null
        }
    }

    /**
     * Writes raw bytes directly into the cache for [key].
     */
    fun putBytes(key: String, bytes: ByteArray): File? = lock.write {
        val temp = createTempFile(key)
        return try {
            FileOutputStream(temp).use { it.write(bytes) }
            commitTempFile(temp, key)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to write bytes to cache for key: %s", key)
            temp.delete()
            null
        }
    }

    /**
     * Opens a slice of the cached file for range requests.
     */
    fun openRangeInputStream(file: File, startOffset: Long, length: Long?): InputStream {
        val raf = RandomAccessFile(file, "r")
        raf.seek(startOffset)
        val availableLength = if (length != null) {
            length.coerceAtMost(file.length() - startOffset)
        } else {
            file.length() - startOffset
        }

        return object : InputStream() {
            private var bytesRemaining = availableLength

            override fun read(): Int {
                if (bytesRemaining <= 0) return -1
                val b = raf.read()
                if (b != -1) bytesRemaining--
                return b
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (bytesRemaining <= 0) return -1
                val toRead = len.coerceAtMost(bytesRemaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                val readCount = raf.read(b, off, toRead)
                if (readCount != -1) bytesRemaining -= readCount
                return readCount
            }

            override fun available(): Int = bytesRemaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

            override fun close() {
                raf.close()
            }
        }
    }

    /**
     * Calculates the total size of all cached files in bytes.
     */
    fun getCacheSizeBytes(): Long = lock.read {
        cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * Clears all cached stream files.
     */
    fun clearCache() = lock.write {
        cacheDir.listFiles()?.forEach { it.delete() }
        Timber.tag(TAG).d("Stream cache cleared")
    }

    /**
     * Trims cache to [maxBytes] using LRU eviction.
     */
    private fun trimToSize(maxBytes: Long) {
        val files = cacheDir.listFiles()?.toList() ?: return
        var currentSize = files.sumOf { it.length() }
        if (currentSize <= maxBytes) return

        val sortedFiles = files.sortedBy { it.lastModified() }
        for (f in sortedFiles) {
            if (currentSize <= maxBytes) break
            val len = f.length()
            if (f.delete()) {
                currentSize -= len
                Timber.tag(TAG).d("Evicted cache file %s (%d bytes)", f.name, len)
            }
        }
    }
}
