package com.quietrays.tonarc.data.analytics

import com.quietrays.tonarc.data.database.EngagementDao
import com.quietrays.tonarc.data.database.SongEngagementEntity
import com.quietrays.tonarc.data.model.Song
import com.quietrays.tonarc.data.preferences.UserPreferencesRepository
import com.quietrays.tonarc.data.repository.MusicRepository
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

data class GenreRatio(
    val genre: String,
    val percentage: Float, // 0.0f to 100.0f
    val songCount: Int
)

data class ArtistAffinity(
    val artistName: String,
    val playCount: Int,
    val durationMs: Long
)

data class TasteProfile(
    val archetypeTitle: String,
    val archetypeSubtitle: String,
    val archetypeEmoji: String,
    val totalListeningDurationMs: Long,
    val totalPlays: Int,
    val topGenres: List<GenreRatio>,
    val topArtists: List<ArtistAffinity>,
    val topSongs: List<Song>
)

@Singleton
class TasteProfileManager @Inject constructor(
    private val engagementDao: EngagementDao,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    suspend fun computeTasteProfile(): TasteProfile {
        val engagements = engagementDao.getAllEngagements()
        val allSongs = musicRepository.getAllSongsOnce()

        if (allSongs.isEmpty() || engagements.isEmpty()) {
            return baselineProfile()
        }

        val totalListeningDurationMs = engagements.sumOf { it.totalPlayDurationMs.coerceAtLeast(0L) }
        val totalPlays = engagements.sumOf { it.playCount.coerceAtLeast(0) }

        if (totalPlays == 0 && totalListeningDurationMs == 0L) {
            return baselineProfile()
        }

        val songsById = allSongs.associateBy { it.id }

        // Top songs ranking (top 20 most engaged)
        val topSongs = engagements
            .mapNotNull { eng ->
                val song = songsById[eng.songId]
                if (song != null && (eng.playCount > 0 || eng.totalPlayDurationMs > 0L)) {
                    Pair(song, eng)
                } else null
            }
            .sortedWith(
                compareByDescending<Pair<Song, SongEngagementEntity>> { it.second.playCount }
                    .thenByDescending { it.second.totalPlayDurationMs }
            )
            .map { it.first }
            .distinctBy { it.id }
            .take(20)

        // Top genres breakdown
        val genreWeights = mutableMapOf<String, Double>()
        val genreSongs = mutableMapOf<String, MutableSet<String>>()

        for (eng in engagements) {
            val song = songsById[eng.songId] ?: continue
            val genre = normalizeGenre(song.genre) ?: continue
            val weight = eng.playCount.coerceAtLeast(0).toDouble() +
                (eng.totalPlayDurationMs.coerceAtLeast(0L).toDouble() / 60_000.0)
            genreWeights[genre] = (genreWeights[genre] ?: 0.0) + weight
            genreSongs.getOrPut(genre) { mutableSetOf() }.add(song.id)
        }

        val totalGenreWeight = genreWeights.values.sum()
        val topGenres = if (totalGenreWeight > 0.0) {
            genreWeights.entries.map { (genre, weight) ->
                val rawPercentage = ((weight / totalGenreWeight) * 100.0).toFloat()
                GenreRatio(
                    genre = genre,
                    percentage = (Math.round(rawPercentage * 10.0f) / 10.0f),
                    songCount = genreSongs[genre]?.size ?: 0
                )
            }.sortedByDescending { it.percentage }
        } else {
            emptyList()
        }

        // Top artists breakdown
        val artistPlays = mutableMapOf<String, Int>()
        val artistDurations = mutableMapOf<String, Long>()

        for (eng in engagements) {
            val song = songsById[eng.songId] ?: continue
            val artistName = song.displayArtist.takeIf { it.isNotBlank() }
                ?: song.artist.takeIf { it.isNotBlank() }
                ?: continue
            if (artistName.equals("unknown", ignoreCase = true) || artistName.equals("unknown artist", ignoreCase = true)) {
                continue
            }
            artistPlays[artistName] = (artistPlays[artistName] ?: 0) + eng.playCount.coerceAtLeast(0)
            artistDurations[artistName] = (artistDurations[artistName] ?: 0L) + eng.totalPlayDurationMs.coerceAtLeast(0L)
        }

        val topArtists = artistPlays.keys.map { name ->
            ArtistAffinity(
                artistName = name,
                playCount = artistPlays[name] ?: 0,
                durationMs = artistDurations[name] ?: 0L
            )
        }
            .filter { it.playCount > 0 || it.durationMs > 0L }
            .sortedWith(
                compareByDescending<ArtistAffinity> { it.playCount }
                    .thenByDescending { it.durationMs }
            )
            .take(5)

        // Archetype classification
        val archetype = determineArchetype(engagements, topGenres)

        return TasteProfile(
            archetypeTitle = archetype.title,
            archetypeSubtitle = archetype.subtitle,
            archetypeEmoji = archetype.emoji,
            totalListeningDurationMs = totalListeningDurationMs,
            totalPlays = totalPlays,
            topGenres = topGenres,
            topArtists = topArtists,
            topSongs = topSongs
        )
    }

    private fun determineArchetype(
        engagements: List<SongEngagementEntity>,
        topGenres: List<GenreRatio>
    ): ArchetypeInfo {
        val timestampEngagements = engagements.filter { it.lastPlayedTimestamp > 0L }
        val totalTimestampActivity = timestampEngagements.sumOf { it.playCount.coerceAtLeast(1) }

        if (totalTimestampActivity > 0) {
            val lateNightActivity = timestampEngagements
                .filter {
                    val hour = getHourOfDay(it.lastPlayedTimestamp)
                    hour >= 23 || hour < 5
                }
                .sumOf { it.playCount.coerceAtLeast(1) }

            if ((lateNightActivity.toDouble() / totalTimestampActivity) >= 0.35) {
                return ArchetypeInfo(
                    title = "Late-Night Audiophile",
                    subtitle = "Finds magic in midnight frequencies and ambient solitude",
                    emoji = "🌌"
                )
            }

            val morningActivity = timestampEngagements
                .filter {
                    val hour = getHourOfDay(it.lastPlayedTimestamp)
                    hour in 5..11
                }
                .sumOf { it.playCount.coerceAtLeast(1) }

            if ((morningActivity.toDouble() / totalTimestampActivity) >= 0.35) {
                return ArchetypeInfo(
                    title = "Acoustic Explorer",
                    subtitle = "Energized by morning melodies and organic rhythms",
                    emoji = "🌅"
                )
            }
        }

        val topGenre = topGenres.firstOrNull()?.genre?.lowercase() ?: ""
        if (topGenre.contains("acoustic") || topGenre.contains("folk")) {
            return ArchetypeInfo(
                title = "Acoustic Explorer",
                subtitle = "Energized by morning melodies and organic rhythms",
                emoji = "🌅"
            )
        }

        val highEnergyKeywords = listOf(
            "dance", "pop", "rock", "electronic", "edm", "house",
            "techno", "metal", "hip hop", "hip-hop", "rap", "synthpop", "disco", "club", "punk"
        )
        val isHighEnergy = topGenres.isNotEmpty() && (
            highEnergyKeywords.any { topGenre.contains(it) } ||
            topGenres.filter { g -> highEnergyKeywords.any { g.genre.lowercase().contains(it) } }
                .sumOf { it.percentage.toDouble() } >= 40.0
        )
        if (isHighEnergy) {
            return ArchetypeInfo(
                title = "High-Energy Motivator",
                subtitle = "Fueled by high-tempo anthems and pulse-pounding beats",
                emoji = "⚡"
            )
        }

        val isEclectic = topGenres.size >= 3 &&
            topGenres[0].percentage <= 55f &&
            topGenres[2].percentage >= 10f
        if (isEclectic) {
            return ArchetypeInfo(
                title = "Eclectic Dreamer",
                subtitle = "Effortlessly flows across borders and contrasting sounds",
                emoji = "🎧"
            )
        }

        return ArchetypeInfo(
            title = "Melody Connoisseur",
            subtitle = "Guided by timeless songwriting and deep harmonies",
            emoji = "🎵"
        )
    }

    private fun getHourOfDay(timestamp: Long): Int {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        return calendar.get(Calendar.HOUR_OF_DAY)
    }

    private fun normalizeGenre(rawGenre: String?): String? {
        val trimmed = rawGenre?.trim() ?: return null
        if (trimmed.isEmpty() || trimmed.equals("unknown", ignoreCase = true)) return null
        return trimmed.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }

    private fun baselineProfile(): TasteProfile {
        return TasteProfile(
            archetypeTitle = "Melody Connoisseur",
            archetypeSubtitle = "Guided by timeless songwriting and deep harmonies",
            archetypeEmoji = "🎵",
            totalListeningDurationMs = 0L,
            totalPlays = 0,
            topGenres = emptyList(),
            topArtists = emptyList(),
            topSongs = emptyList()
        )
    }

    private data class ArchetypeInfo(
        val title: String,
        val subtitle: String,
        val emoji: String
    )
}
