package com.quietrays.tonarc.data.repository

import com.quietrays.tonarc.data.database.EngagementDao
import com.quietrays.tonarc.data.database.MusicDao
import com.quietrays.tonarc.data.database.SongEngagementEntity
import com.quietrays.tonarc.data.database.SongEntity
import com.quietrays.tonarc.data.database.toSong
import com.quietrays.tonarc.data.model.SmartPlaylistType
import com.quietrays.tonarc.data.youtube.YouTubeRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SmartPlaylistGeneratorTest {

    private val musicDao: MusicDao = mockk()
    private val engagementDao: EngagementDao = mockk()
    private val youTubeRepository: YouTubeRepository = mockk()
    private lateinit var generator: SmartPlaylistGenerator

    @BeforeEach
    fun setUp() {
        generator = SmartPlaylistGenerator(musicDao, engagementDao, youTubeRepository)
    }

    private fun createSongEntity(id: Long, title: String, dateAdded: Long = 1000L, isFav: Boolean = false): SongEntity {
        return SongEntity(
            id = id,
            title = title,
            artistName = "Test Artist",
            artistId = 1L,
            albumArtist = null,
            albumArtistId = 0L,
            albumName = "Test Album",
            albumId = 1L,
            contentUriString = "content://media/$id",
            albumArtUriString = null,
            duration = 180000L,
            genre = "Rock",
            filePath = "/music/song$id.mp3",
            parentDirectoryPath = "/music",
            isFavorite = isFav,
            lyrics = null,
            trackNumber = 1,
            discNumber = 1,
            year = 2023,
            dateAdded = dateAdded,
            mimeType = "audio/mp3",
            bitrate = 320,
            sampleRate = 44100,
            artistsJson = null,
            sourceType = 0,
            mediaStoreDateAdded = dateAdded,
            mediaStoreDateModified = dateAdded,
            titleUserEdited = false,
            artistUserEdited = false,
            albumUserEdited = false,
            genreUserEdited = false,
            mbRecordingId = null,
            mbReleaseId = null,
            mbArtistId = null
        )
    }

    @Test
    fun `generateSmartPlaylist TOP_PLAYED returns top engagement songs`() = runTest {
        val song1 = createSongEntity(1L, "Song 1")
        val song2 = createSongEntity(2L, "Song 2")

        coEvery { engagementDao.getTopPlayedSongs(any()) } returns listOf(
            SongEngagementEntity(songId = "2", playCount = 10, totalPlayDurationMs = 1800000L, lastPlayedTimestamp = 5000L),
            SongEngagementEntity(songId = "1", playCount = 5, totalPlayDurationMs = 900000L, lastPlayedTimestamp = 4000L)
        )
        coEvery { musicDao.getAllSongsList() } returns listOf(song1, song2)

        val result = generator.generateSmartPlaylist(SmartPlaylistType.TOP_PLAYED, limit = 10)

        assertEquals(2, result.size)
        assertEquals("Song 2", result[0].title)
        assertEquals("Song 1", result[1].title)
    }

    @Test
    fun `generateSmartPlaylist RECENTLY_ADDED returns newest songs first`() = runTest {
        val oldSong = createSongEntity(1L, "Old Song", dateAdded = 1000L)
        val newSong = createSongEntity(2L, "New Song", dateAdded = 9000L)

        coEvery { musicDao.getAllSongsList() } returns listOf(oldSong, newSong)

        val result = generator.generateSmartPlaylist(SmartPlaylistType.RECENTLY_ADDED, limit = 10)

        assertEquals(2, result.size)
        assertEquals("New Song", result[0].title)
        assertEquals("Old Song", result[1].title)
    }

    @Test
    fun `getSmartQueueForSong with online radio returns radio recommendations`() = runTest {
        val seedSong = createSongEntity(1L, "Seed Track").toSong()
        val radioSong1 = createSongEntity(2L, "Radio Track 1").toSong()
        val radioSong2 = createSongEntity(3L, "Radio Track 2").toSong()

        coEvery { youTubeRepository.getRadioTracksForSong(seedSong) } returns listOf(radioSong1, radioSong2)

        val result = generator.getSmartQueueForSong(seedSong, limit = 10)

        assertEquals(2, result.size)
        assertEquals("Radio Track 1", result[0].title)
        assertEquals("Radio Track 2", result[1].title)
    }

    @Test
    fun `getSmartQueueForSong fallback returns same artist and genre songs`() = runTest {
        val seedSong = createSongEntity(1L, "Seed Track").toSong()
        val sameArtistSong = createSongEntity(2L, "Artist Song")
        val sameGenreSong = createSongEntity(3L, "Genre Song").copy(artistName = "Other Artist", genre = "Rock")

        coEvery { youTubeRepository.getRadioTracksForSong(seedSong) } returns emptyList()
        coEvery { musicDao.getAllSongsList() } returns listOf(createSongEntity(1L, "Seed Track"), sameArtistSong, sameGenreSong)
        coEvery { engagementDao.getTopPlayedSongs(any()) } returns emptyList()

        val result = generator.getSmartQueueForSong(seedSong, limit = 10)

        assertTrue(result.isNotEmpty())
        assertTrue(result.any { it.title == "Artist Song" })
    }
}
