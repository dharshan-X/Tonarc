package com.quietrays.tonarc.data.analytics

import com.quietrays.tonarc.data.database.EngagementDao
import com.quietrays.tonarc.data.database.SongEngagementEntity
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.preferences.UserPreferencesRepository
import com.quietrays.tonarc.data.repository.MusicRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Calendar

class TasteProfileManagerTest {

    private val engagementDao: EngagementDao = mockk(relaxed = true)
    private val musicRepository: MusicRepository = mockk(relaxed = true)
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)

    private lateinit var tasteProfileManager: TasteProfileManager

    @BeforeEach
    fun setUp() {
        tasteProfileManager = TasteProfileManager(
            engagementDao = engagementDao,
            musicRepository = musicRepository,
            userPreferencesRepository = userPreferencesRepository
        )
    }

    private fun createSong(
        id: String,
        title: String = "Song $id",
        artist: String = "Artist $id",
        artistId: Long = id.hashCode().toLong(),
        genre: String? = null,
        duration: Long = 180_000L
    ): Song {
        return Song(
            id = id,
            title = title,
            artist = artist,
            artistId = artistId,
            album = "Album $id",
            albumId = 1L,
            path = "/storage/emulated/0/Music/$id.mp3",
            contentUriString = "content://media/external/audio/media/$id",
            albumArtUriString = null,
            duration = duration,
            genre = genre,
            mimeType = "audio/mpeg",
            bitrate = 320,
            sampleRate = 44100
        )
    }

    private fun timestampForHour(hour: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, 30)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    @Test
    fun `computeTasteProfile with empty engagements returns welcoming baseline profile`() = runTest {
        coEvery { engagementDao.getAllEngagements() } returns emptyList()
        coEvery { musicRepository.getAllSongsOnce() } returns emptyList()

        val profile = tasteProfileManager.computeTasteProfile()

        assertEquals("Melody Connoisseur", profile.archetypeTitle)
        assertEquals("🎵", profile.archetypeEmoji)
        assertEquals(0L, profile.totalListeningDurationMs)
        assertEquals(0, profile.totalPlays)
        assertTrue(profile.topGenres.isEmpty())
        assertTrue(profile.topArtists.isEmpty())
        assertTrue(profile.topSongs.isEmpty())
    }

    @Test
    fun `computeTasteProfile aggregates total plays, listening duration, and genre percentage ratios`() = runTest {
        val song1 = createSong("1", genre = "Rock", artist = "Queen")
        val song2 = createSong("2", genre = "Rock", artist = "Queen")
        val song3 = createSong("3", genre = "Jazz", artist = "Miles Davis")

        val engagements = listOf(
            SongEngagementEntity(songId = "1", playCount = 10, totalPlayDurationMs = 100_000L, lastPlayedTimestamp = timestampForHour(14)),
            SongEngagementEntity(songId = "2", playCount = 10, totalPlayDurationMs = 100_000L, lastPlayedTimestamp = timestampForHour(15)),
            SongEngagementEntity(songId = "3", playCount = 5, totalPlayDurationMs = 50_000L, lastPlayedTimestamp = timestampForHour(16))
        )

        coEvery { musicRepository.getAllSongsOnce() } returns listOf(song1, song2, song3)
        coEvery { engagementDao.getAllEngagements() } returns engagements

        val profile = tasteProfileManager.computeTasteProfile()

        assertEquals(25, profile.totalPlays)
        assertEquals(250_000L, profile.totalListeningDurationMs)
        assertEquals(2, profile.topGenres.size)

        val rockGenre = profile.topGenres.find { it.genre == "Rock" }
        val jazzGenre = profile.topGenres.find { it.genre == "Jazz" }

        assertTrue(rockGenre != null)
        assertTrue(jazzGenre != null)
        assertEquals(2, rockGenre?.songCount)
        assertEquals(1, jazzGenre?.songCount)
        assertEquals(80.0f, rockGenre?.percentage ?: 0f, 0.5f)
        assertEquals(20.0f, jazzGenre?.percentage ?: 0f, 0.5f)
    }

    @Test
    fun `computeTasteProfile aggregates top 5 artists sorted by engagement`() = runTest {
        val songs = (1..6).map { i ->
            createSong(id = "$i", artist = "Artist $i", genre = "Pop")
        }
        val engagements = (1..6).map { i ->
            SongEngagementEntity(
                songId = "$i",
                playCount = i * 10,
                totalPlayDurationMs = i * 60_000L,
                lastPlayedTimestamp = timestampForHour(14)
            )
        }

        coEvery { musicRepository.getAllSongsOnce() } returns songs
        coEvery { engagementDao.getAllEngagements() } returns engagements

        val profile = tasteProfileManager.computeTasteProfile()

        assertEquals(5, profile.topArtists.size)
        assertEquals("Artist 6", profile.topArtists[0].artistName)
        assertEquals(60, profile.topArtists[0].playCount)
        assertEquals(360_000L, profile.topArtists[0].durationMs)
        assertEquals("Artist 5", profile.topArtists[1].artistName)
        assertEquals("Artist 2", profile.topArtists[4].artistName)
    }

    @Test
    fun `computeTasteProfile ranks top 20 most engaged songs`() = runTest {
        val songs = (1..25).map { i -> createSong(id = "$i", title = "Track $i") }
        val engagements = (1..25).map { i ->
            SongEngagementEntity(
                songId = "$i",
                playCount = i,
                totalPlayDurationMs = i * 10_000L,
                lastPlayedTimestamp = timestampForHour(15)
            )
        }

        coEvery { musicRepository.getAllSongsOnce() } returns songs
        coEvery { engagementDao.getAllEngagements() } returns engagements

        val profile = tasteProfileManager.computeTasteProfile()

        assertEquals(20, profile.topSongs.size)
        assertEquals("25", profile.topSongs[0].id)
        assertEquals("6", profile.topSongs[19].id)
    }

    @Test
    fun `computeTasteProfile classifies Late-Night Audiophile when night activity exceeds 35 percent`() = runTest {
        val song1 = createSong("1", genre = "Ambient")
        val song2 = createSong("2", genre = "Ambient")
        val song3 = createSong("3", genre = "Ambient")

        val engagements = listOf(
            SongEngagementEntity(songId = "1", playCount = 4, totalPlayDurationMs = 40_000L, lastPlayedTimestamp = timestampForHour(23)),
            SongEngagementEntity(songId = "2", playCount = 4, totalPlayDurationMs = 40_000L, lastPlayedTimestamp = timestampForHour(2)),
            SongEngagementEntity(songId = "3", playCount = 2, totalPlayDurationMs = 20_000L, lastPlayedTimestamp = timestampForHour(15))
        )

        coEvery { musicRepository.getAllSongsOnce() } returns listOf(song1, song2, song3)
        coEvery { engagementDao.getAllEngagements() } returns engagements

        val profile = tasteProfileManager.computeTasteProfile()

        assertEquals("Late-Night Audiophile", profile.archetypeTitle)
        assertEquals("🌌", profile.archetypeEmoji)
        assertEquals("Finds magic in midnight frequencies and ambient solitude", profile.archetypeSubtitle)
    }

    @Test
    fun `computeTasteProfile classifies Acoustic Explorer when morning activity exceeds 35 percent`() = runTest {
        val song1 = createSong("1", genre = "Indie")
        val song2 = createSong("2", genre = "Indie")
        val song3 = createSong("3", genre = "Indie")

        val engagements = listOf(
            SongEngagementEntity(songId = "1", playCount = 5, totalPlayDurationMs = 50_000L, lastPlayedTimestamp = timestampForHour(7)),
            SongEngagementEntity(songId = "2", playCount = 5, totalPlayDurationMs = 50_000L, lastPlayedTimestamp = timestampForHour(9)),
            SongEngagementEntity(songId = "3", playCount = 2, totalPlayDurationMs = 20_000L, lastPlayedTimestamp = timestampForHour(15))
        )

        coEvery { musicRepository.getAllSongsOnce() } returns listOf(song1, song2, song3)
        coEvery { engagementDao.getAllEngagements() } returns engagements

        val profile = tasteProfileManager.computeTasteProfile()

        assertEquals("Acoustic Explorer", profile.archetypeTitle)
        assertEquals("🌅", profile.archetypeEmoji)
        assertEquals("Energized by morning melodies and organic rhythms", profile.archetypeSubtitle)
    }

    @Test
    fun `computeTasteProfile classifies Acoustic Explorer when top genre is Acoustic or Folk`() = runTest {
        val song1 = createSong("1", genre = "Acoustic / Folk")
        val song2 = createSong("2", genre = "Acoustic")

        val engagements = listOf(
            SongEngagementEntity(songId = "1", playCount = 10, totalPlayDurationMs = 100_000L, lastPlayedTimestamp = timestampForHour(14)),
            SongEngagementEntity(songId = "2", playCount = 5, totalPlayDurationMs = 50_000L, lastPlayedTimestamp = timestampForHour(15))
        )

        coEvery { musicRepository.getAllSongsOnce() } returns listOf(song1, song2)
        coEvery { engagementDao.getAllEngagements() } returns engagements

        val profile = tasteProfileManager.computeTasteProfile()

        assertEquals("Acoustic Explorer", profile.archetypeTitle)
        assertEquals("🌅", profile.archetypeEmoji)
    }

    @Test
    fun `computeTasteProfile classifies High-Energy Motivator for Dance Pop Electronic Rock genres`() = runTest {
        val song1 = createSong("1", genre = "Electronic")
        val song2 = createSong("2", genre = "Dance")

        val engagements = listOf(
            SongEngagementEntity(songId = "1", playCount = 10, totalPlayDurationMs = 100_000L, lastPlayedTimestamp = timestampForHour(14)),
            SongEngagementEntity(songId = "2", playCount = 10, totalPlayDurationMs = 100_000L, lastPlayedTimestamp = timestampForHour(15))
        )

        coEvery { musicRepository.getAllSongsOnce() } returns listOf(song1, song2)
        coEvery { engagementDao.getAllEngagements() } returns engagements

        val profile = tasteProfileManager.computeTasteProfile()

        assertEquals("High-Energy Motivator", profile.archetypeTitle)
        assertEquals("⚡", profile.archetypeEmoji)
        assertEquals("Fueled by high-tempo anthems and pulse-pounding beats", profile.archetypeSubtitle)
    }

    @Test
    fun `computeTasteProfile classifies Eclectic Dreamer for 3 or more evenly distributed genres`() = runTest {
        val song1 = createSong("1", genre = "Classical")
        val song2 = createSong("2", genre = "Jazz")
        val song3 = createSong("3", genre = "World")

        val engagements = listOf(
            SongEngagementEntity(songId = "1", playCount = 5, totalPlayDurationMs = 50_000L, lastPlayedTimestamp = timestampForHour(14)),
            SongEngagementEntity(songId = "2", playCount = 5, totalPlayDurationMs = 50_000L, lastPlayedTimestamp = timestampForHour(15)),
            SongEngagementEntity(songId = "3", playCount = 5, totalPlayDurationMs = 50_000L, lastPlayedTimestamp = timestampForHour(16))
        )

        coEvery { musicRepository.getAllSongsOnce() } returns listOf(song1, song2, song3)
        coEvery { engagementDao.getAllEngagements() } returns engagements

        val profile = tasteProfileManager.computeTasteProfile()

        assertEquals("Eclectic Dreamer", profile.archetypeTitle)
        assertEquals("🎧", profile.archetypeEmoji)
        assertEquals("Effortlessly flows across borders and contrasting sounds", profile.archetypeSubtitle)
    }

    @Test
    fun `computeTasteProfile classifies Melody Connoisseur when no other archetype condition matches`() = runTest {
        val song1 = createSong("1", genre = "Soundtrack")
        val song2 = createSong("2", genre = "Classical")

        val engagements = listOf(
            SongEngagementEntity(songId = "1", playCount = 20, totalPlayDurationMs = 200_000L, lastPlayedTimestamp = timestampForHour(14)),
            SongEngagementEntity(songId = "2", playCount = 2, totalPlayDurationMs = 20_000L, lastPlayedTimestamp = timestampForHour(15))
        )

        coEvery { musicRepository.getAllSongsOnce() } returns listOf(song1, song2)
        coEvery { engagementDao.getAllEngagements() } returns engagements

        val profile = tasteProfileManager.computeTasteProfile()

        assertEquals("Melody Connoisseur", profile.archetypeTitle)
        assertEquals("🎵", profile.archetypeEmoji)
        assertEquals("Guided by timeless songwriting and deep harmonies", profile.archetypeSubtitle)
    }
}
