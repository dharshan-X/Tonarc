package com.quietrays.tonarc.data.repository

import com.quietrays.tonarc.data.database.EngagementDao
import com.quietrays.tonarc.data.database.MusicDao
import com.quietrays.tonarc.data.database.toSong
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.model.SmartPlaylistType
import com.quietrays.tonarc.data.youtube.YouTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates dynamic smart playlists based on listening history, engagement statistics,
 * library recency, and algorithmic recommendations.
 */
@Singleton
class SmartPlaylistGenerator @Inject constructor(
    private val musicDao: MusicDao,
    private val engagementDao: EngagementDao,
    private val youTubeRepository: YouTubeRepository
) {
    suspend fun generateSmartPlaylist(type: SmartPlaylistType, limit: Int = 50): List<Song> = withContext(Dispatchers.IO) {
        when (type) {
            SmartPlaylistType.TOP_PLAYED -> {
                val topEngagements = engagementDao.getTopPlayedSongs(limit)
                val allSongs = musicDao.getAllSongsList().associateBy { it.id.toString() }
                val songs = topEngagements.mapNotNull { allSongs[it.songId]?.toSong() }
                if (songs.isNotEmpty()) songs else musicDao.getAllSongsList().take(limit).map { it.toSong() }
            }
            SmartPlaylistType.RECENTLY_ADDED -> {
                val allSongs = musicDao.getAllSongsList()
                allSongs.sortedByDescending { it.dateAdded }
                    .take(limit)
                    .map { it.toSong() }
            }
            SmartPlaylistType.FORGOTTEN_FAVORITES -> {
                val now = System.currentTimeMillis()
                val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000L)
                val allSongs = musicDao.getAllSongsList().associateBy { it.id.toString() }
                val allEngagements = engagementDao.getAllEngagements()
                val oldEngagements = allEngagements
                    .filter { it.lastPlayedTimestamp in 1 until thirtyDaysAgo && it.playCount >= 2 }
                    .sortedByDescending { it.playCount }

                val forgottenFavs = oldEngagements.mapNotNull { allSongs[it.songId]?.toSong() }
                if (forgottenFavs.isNotEmpty()) {
                    forgottenFavs.take(limit)
                } else {
                    val favorites = musicDao.getAllSongsList().filter { it.isFavorite }
                    if (favorites.isNotEmpty()) favorites.take(limit).map { it.toSong() }
                    else musicDao.getAllSongsList().shuffled().take(limit).map { it.toSong() }
                }
            }
            SmartPlaylistType.TIME_CAPSULE -> {
                val now = System.currentTimeMillis()
                val oneYearAgo = now - (365L * 24 * 60 * 60 * 1000L)
                val songs = musicDao.getAllSongsList()
                    .filter { it.dateAdded < oneYearAgo || it.year in 1950..2020 }
                    .shuffled()
                    .take(limit)
                    .map { it.toSong() }
                if (songs.isNotEmpty()) songs else musicDao.getAllSongsList().shuffled().take(limit).map { it.toSong() }
            }
            SmartPlaylistType.DISCOVERY_MIX -> {
                val topEngagements = engagementDao.getTopPlayedSongs(5)
                val allSongs = musicDao.getAllSongsList().associateBy { it.id.toString() }
                val seedSong = topEngagements.firstNotNullOfOrNull { allSongs[it.songId]?.toSong() }
                    ?: musicDao.getAllSongsList().shuffled().firstOrNull()?.toSong()

                if (seedSong != null) {
                    val radioTracks = youTubeRepository.getRadioTracksForSong(seedSong)
                    if (radioTracks.isNotEmpty()) {
                        (listOf(seedSong) + radioTracks).take(limit)
                    } else {
                        musicDao.getAllSongsList().shuffled().take(limit).map { it.toSong() }
                    }
                } else {
                    emptyList()
                }
            }
        }
    }

    /**
     * Generates a smart, highly-relevant queue of upcoming tracks seeded by [seedSong].
     * Tries online/radio suggestions first, and seamlessly blends or falls back to
     * artist/genre/affinity matching from the local library.
     */
    suspend fun getSmartQueueForSong(seedSong: Song, limit: Int = 50): List<Song> = withContext(Dispatchers.IO) {
        val radioTracks = runCatching { youTubeRepository.getRadioTracksForSong(seedSong) }
            .getOrDefault(emptyList())
            .filter { it.id != seedSong.id && it.contentUriString != seedSong.contentUriString }

        if (radioTracks.isNotEmpty()) {
            return@withContext radioTracks.take(limit)
        }

        // Local Smart Relevance Strategy
        val allLocalSongs = musicDao.getAllSongsList()
            .map { it.toSong() }
            .filter { it.id != seedSong.id && it.contentUriString != seedSong.contentUriString }

        if (allLocalSongs.isEmpty()) {
            return@withContext emptyList()
        }

        val sameArtist = allLocalSongs.filter {
            it.artist.isNotBlank() && it.artist.equals(seedSong.artist, ignoreCase = true)
        }.shuffled()

        val sameGenre = if (!seedSong.genre.isNullOrBlank()) {
            allLocalSongs.filter {
                it.genre?.equals(seedSong.genre, ignoreCase = true) == true && !sameArtist.contains(it)
            }.shuffled()
        } else {
            emptyList()
        }

        val topEngagements = engagementDao.getTopPlayedSongs(limit).map { it.songId }.toSet()
        val highAffinity = allLocalSongs.filter {
            it.id in topEngagements && !sameArtist.contains(it) && !sameGenre.contains(it)
        }.shuffled()

        val remainder = allLocalSongs.filter {
            !sameArtist.contains(it) && !sameGenre.contains(it) && !highAffinity.contains(it)
        }.shuffled()

        (sameArtist + sameGenre + highAffinity + remainder).take(limit)
    }
}
