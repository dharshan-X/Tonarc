package com.quietrays.tonarc.data.stream

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class StreamDiskCacheTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var context: Context
    private lateinit var cache: StreamDiskCache

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.cacheDir } returns tempDir
        cache = StreamDiskCache(context)
    }

    @Test
    fun `putBytes caches data and getCachedFile retrieves it`() {
        val testData = "Audio stream payload bytes 123456789".toByteArray()
        val key = "youtube_testVideo123"

        assertNull(cache.getCachedFile(key))

        val savedFile = cache.putBytes(key, testData)
        assertNotNull(savedFile)
        assertTrue(savedFile!!.exists())

        val retrievedFile = cache.getCachedFile(key)
        assertNotNull(retrievedFile)
        assertEquals(testData.size.toLong(), retrievedFile!!.length())
    }

    @Test
    fun `openRangeInputStream reads partial slices correctly`() {
        val testData = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toByteArray()
        val key = "jellyfin_sliceTrack"

        val file = cache.putBytes(key, testData)!!
        
        // Read slice from index 10 for length 10 ("ABCDEFGHIJ")
        val stream = cache.openRangeInputStream(file, 10L, 10L)
        val buffer = ByteArray(10)
        val readCount = stream.read(buffer)

        assertEquals(10, readCount)
        assertEquals("ABCDEFGHIJ", String(buffer))
    }

    @Test
    fun `clearCache removes all cached audio files`() {
        cache.putBytes("key1", "sample1".toByteArray())
        cache.putBytes("key2", "sample2".toByteArray())

        assertTrue(cache.getCacheSizeBytes() > 0)

        cache.clearCache()
        assertEquals(0L, cache.getCacheSizeBytes())
        assertNull(cache.getCachedFile("key1"))
        assertNull(cache.getCachedFile("key2"))
    }
}
