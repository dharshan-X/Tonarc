package com.quietrays.tonarc.data

import android.content.Context
import com.quietrays.tonarc.data.database.EngagementDao
import com.quietrays.tonarc.data.database.SongEngagementEntity
import com.quietrays.tonarc.data.model.Song
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Calendar

class DailyMixManagerTest {

    @TempDir
    lateinit var tempDir: File

    private val mockContext: Context = mockk(relaxed = true)
    private val mockEngagementDao: EngagementDao = mockk(relaxed = true)

    private lateinit var dailyMixManager: DailyMixManager

    @BeforeEach
    fun setUp() {
        every { mockContext.filesDir } returns tempDir
        coEvery { mockEngagementDao.getAllEngagements() } returns emptyList()
        dailyMixManager = DailyMixManager(mockContext, mockEngagementDao)
    }

    private fun createSong(
        id: String,
        title: String = "Title $id",
        artist: String = "Artist $id",
        artistId: Long = id.hashCode().toLong(),
        genre: String? = null,
        dateAdded: Long = System.currentTimeMillis() - 100_000L
    ): Song {
        return Song(
            id = id,
            title = title,
            artist = artist,
            artistId = artistId,
            album = "Album $id",
            albumId = 1L,
            path = "/music/$id.mp3",
            contentUriString = "content://media/$id",
            albumArtUriString = null,
            duration = 200L,
            genre = genre,
            dateAdded = dateAdded,
            mimeType = "audio/mp3",
            bitrate = 320,
            sampleRate = 44100
        )
    }

    @Test
    fun `getCurrentTimeMood correctly maps time of day`() {
        val morningCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 8) }
        assertEquals(MixMood.MORNING_FOCUS, dailyMixManager.getCurrentTimeMood(morningCal))

        val energyCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 14) }
        assertEquals(MixMood.ENERGY_BOOST, dailyMixManager.getCurrentTimeMood(energyCal))

        val eveningCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 20) }
        assertEquals(MixMood.EVENING_CHILL, dailyMixManager.getCurrentTimeMood(eveningCal))

        val midnightCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 2) }
        assertEquals(MixMood.MIDNIGHT_LOFI, dailyMixManager.getCurrentTimeMood(midnightCal))
    }

    @Test
    fun `getCurrentTimeMood boundary hours map to expected moods`() {
        // Morning: 5..11
        val cal5 = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 5) }
        assertEquals(MixMood.MORNING_FOCUS, dailyMixManager.getCurrentTimeMood(cal5))

        val cal11 = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 11) }
        assertEquals(MixMood.MORNING_FOCUS, dailyMixManager.getCurrentTimeMood(cal11))

        // Energy: 12..17
        val cal12 = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 12) }
        assertEquals(MixMood.ENERGY_BOOST, dailyMixManager.getCurrentTimeMood(cal12))

        val cal17 = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 17) }
        assertEquals(MixMood.ENERGY_BOOST, dailyMixManager.getCurrentTimeMood(cal17))

        // Evening: 18..22
        val cal18 = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 18) }
        assertEquals(MixMood.EVENING_CHILL, dailyMixManager.getCurrentTimeMood(cal18))

        val cal22 = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 22) }
        assertEquals(MixMood.EVENING_CHILL, dailyMixManager.getCurrentTimeMood(cal22))

        // Midnight / Late Night: 23, 0..4
        val cal23 = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23) }
        assertEquals(MixMood.MIDNIGHT_LOFI, dailyMixManager.getCurrentTimeMood(cal23))

        val cal0 = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0) }
        assertEquals(MixMood.MIDNIGHT_LOFI, dailyMixManager.getCurrentTimeMood(cal0))

        val cal4 = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 4) }
        assertEquals(MixMood.MIDNIGHT_LOFI, dailyMixManager.getCurrentTimeMood(cal4))
    }

    @Test
    fun `generateContextualMix generates MORNING_FOCUS mix prioritizing acoustic and soft genres`() = runTest {
        val morningSong1 = createSong("m1", title = "Acoustic Sunrise", genre = "Acoustic")
        val morningSong2 = createSong("m2", title = "Folk Morning", genre = "Folk")
        val rockSong = createSong("r1", title = "Hard Rock", genre = "Rock")
        val edmSong = createSong("e1", title = "EDM Drop", genre = "EDM")

        val allSongs = listOf(morningSong1, morningSong2, rockSong, edmSong)
        val mix = dailyMixManager.generateContextualMix(MixMood.MORNING_FOCUS, allSongs, limit = 2)

        assertEquals(MixMood.MORNING_FOCUS, mix.mood)
        assertEquals(MixMood.MORNING_FOCUS.displayName, mix.title)
        assertEquals(MixMood.MORNING_FOCUS.subtitle, mix.subtitle)
        assertEquals(2, mix.songs.size)
        assertTrue(mix.songs.any { it.id == "m1" })
        assertTrue(mix.songs.any { it.id == "m2" })
    }

    @Test
    fun `generateContextualMix generates ENERGY_BOOST mix prioritizing upbeat and dance genres`() = runTest {
        val energySong1 = createSong("e1", title = "Pop Anthem", genre = "Pop")
        val energySong2 = createSong("e2", title = "Dance Workout", genre = "Dance")
        val chillSong = createSong("c1", title = "Sleep Lofi", genre = "Lofi")
        val ambientSong = createSong("a1", title = "Calm Ambient", genre = "Ambient")

        val allSongs = listOf(energySong1, energySong2, chillSong, ambientSong)
        val mix = dailyMixManager.generateContextualMix(MixMood.ENERGY_BOOST, allSongs, limit = 2)

        assertEquals(MixMood.ENERGY_BOOST, mix.mood)
        assertEquals(2, mix.songs.size)
        assertTrue(mix.songs.any { it.id == "e1" })
        assertTrue(mix.songs.any { it.id == "e2" })
    }

    @Test
    fun `generateContextualMix generates EVENING_CHILL mix prioritizing rnb and jazz genres`() = runTest {
        val chillSong1 = createSong("c1", title = "Smooth Jazz", genre = "Jazz")
        val chillSong2 = createSong("c2", title = "Evening R&B", genre = "R&B")
        val heavySong = createSong("h1", title = "Heavy Metal", genre = "Metal")

        val allSongs = listOf(chillSong1, chillSong2, heavySong)
        val mix = dailyMixManager.generateContextualMix(MixMood.EVENING_CHILL, allSongs, limit = 2)

        assertEquals(MixMood.EVENING_CHILL, mix.mood)
        assertEquals(2, mix.songs.size)
        assertTrue(mix.songs.any { it.id == "c1" })
        assertTrue(mix.songs.any { it.id == "c2" })
    }

    @Test
    fun `generateContextualMix generates MIDNIGHT_LOFI mix prioritizing lofi and synthwave genres`() = runTest {
        val lofiSong1 = createSong("l1", title = "Lofi Study", genre = "Lofi")
        val lofiSong2 = createSong("l2", title = "Synthwave Nights", genre = "Synthwave")
        val popSong = createSong("p1", title = "Upbeat Pop", genre = "Pop")

        val allSongs = listOf(lofiSong1, lofiSong2, popSong)
        val mix = dailyMixManager.generateContextualMix(MixMood.MIDNIGHT_LOFI, allSongs, limit = 2)

        assertEquals(MixMood.MIDNIGHT_LOFI, mix.mood)
        assertEquals(2, mix.songs.size)
        assertTrue(mix.songs.any { it.id == "l1" })
        assertTrue(mix.songs.any { it.id == "l2" })
    }

    @Test
    fun `generateContextualMix gracefully backfills when matching songs are fewer than limit`() = runTest {
        val acousticSong = createSong("m1", title = "Single Acoustic", genre = "Acoustic")
        val otherSong1 = createSong("o1", title = "Other 1", genre = "Pop")
        val otherSong2 = createSong("o2", title = "Other 2", genre = "Rock")
        val otherSong3 = createSong("o3", title = "Other 3", genre = "Jazz")

        val allSongs = listOf(acousticSong, otherSong1, otherSong2, otherSong3)
        val mix = dailyMixManager.generateContextualMix(MixMood.MORNING_FOCUS, allSongs, limit = 3)

        assertEquals(3, mix.songs.size)
        assertTrue(mix.songs.any { it.id == "m1" })
    }

    @Test
    fun `generateContextualMix for DISCOVERY_RADAR strictly excludes songs played in last 7 days`() = runTest {
        val now = System.currentTimeMillis()
        val oneDayAgo = now - 1 * 86_400_000L
        val threeDaysAgo = now - 3 * 86_400_000L
        val tenDaysAgo = now - 10 * 86_400_000L

        // s1: Played 1 day ago, 5 plays -> EXCLUDED (recent play & count >= 2)
        val s1 = createSong("s1", title = "Recent Hit")
        // s2: Played 3 days ago, 3 plays -> EXCLUDED (recent play & count >= 2)
        val s2 = createSong("s2", title = "Another Recent Hit")
        // s3: Played 10 days ago, 8 plays -> INCLUDED (played > 7 days ago)
        val s3 = createSong("s3", title = "Old Favorite")
        // s4: Played 2 days ago, 1 play -> INCLUDED (play count < 2)
        val s4 = createSong("s4", title = "Sampled Track")
        // s5: Unplayed gem -> INCLUDED
        val s5 = createSong("s5", title = "Hidden Gem")
        // s6: Unplayed from favorite artist -> INCLUDED
        val s6 = createSong("s6", title = "Fav Artist Gem", artist = "Beloved Artist", artistId = 99L)

        val engagements = listOf(
            SongEngagementEntity(songId = "s1", playCount = 5, totalPlayDurationMs = 5000L, lastPlayedTimestamp = oneDayAgo),
            SongEngagementEntity(songId = "s2", playCount = 3, totalPlayDurationMs = 3000L, lastPlayedTimestamp = threeDaysAgo),
            SongEngagementEntity(songId = "s3", playCount = 8, totalPlayDurationMs = 8000L, lastPlayedTimestamp = tenDaysAgo),
            SongEngagementEntity(songId = "s4", playCount = 1, totalPlayDurationMs = 1000L, lastPlayedTimestamp = threeDaysAgo)
        )
        coEvery { mockEngagementDao.getAllEngagements() } returns engagements

        val allSongs = listOf(s1, s2, s3, s4, s5, s6)
        val mix = dailyMixManager.generateContextualMix(MixMood.DISCOVERY_RADAR, allSongs, favoriteSongIds = setOf("s6"), limit = 10)

        assertEquals(MixMood.DISCOVERY_RADAR, mix.mood)
        val resultIds = mix.songs.map { it.id }

        // Strictly verify excluded songs are not in the mix
        assertFalse(resultIds.contains("s1"), "s1 should be excluded because it was played within 7 days with >=2 plays")
        assertFalse(resultIds.contains("s2"), "s2 should be excluded because it was played within 7 days with >=2 plays")

        // Eligible songs should be present
        assertTrue(resultIds.contains("s3"), "s3 should be included because last played > 7 days ago")
        assertTrue(resultIds.contains("s4"), "s4 should be included because play count < 2")
        assertTrue(resultIds.contains("s5"), "s5 should be included as an unplayed gem")
        assertTrue(resultIds.contains("s6"), "s6 should be included as an unplayed favorite artist track")
    }

    @Test
    fun `generateAllContextualMixes returns all 5 mood mixes`() = runTest {
        val songs = listOf(
            createSong("1", genre = "Acoustic"),
            createSong("2", genre = "Pop"),
            createSong("3", genre = "Jazz"),
            createSong("4", genre = "Lofi"),
            createSong("5", genre = "Classical")
        )

        val mixes = dailyMixManager.generateAllContextualMixes(songs, limit = 5)

        assertEquals(5, mixes.size)
        val moods = mixes.map { it.mood }
        assertEquals(
            listOf(
                MixMood.MORNING_FOCUS,
                MixMood.ENERGY_BOOST,
                MixMood.EVENING_CHILL,
                MixMood.MIDNIGHT_LOFI,
                MixMood.DISCOVERY_RADAR
            ),
            moods
        )
    }

    @Test
    fun `generateContextualMix handles empty song list gracefully`() = runTest {
        val mix = dailyMixManager.generateContextualMix(MixMood.MORNING_FOCUS, emptyList())
        assertEquals(MixMood.MORNING_FOCUS, mix.mood)
        assertTrue(mix.songs.isEmpty())
    }
}
