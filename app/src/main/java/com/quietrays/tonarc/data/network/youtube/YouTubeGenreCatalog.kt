package com.quietrays.tonarc.data.network.youtube

import com.quietrays.tonarc.data.model.Playlist
import com.quietrays.tonarc.data.model.Song

data class YouTubeGenre(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val colorHex: Long = 0xFF6200EE,
    val category: String = "Genre", // "Genre" or "Mood"
    val searchQuery: String = "$title hits"
)

data class YouTubeGenreExploreResult(
    val genre: YouTubeGenre,
    val topSongs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val continuationToken: String? = null
)

object YouTubeGenreCatalog {
    val genres = listOf(
        YouTubeGenre("pop", "Pop", "Chart-toppers, hooks & global hits", 0xFFE91E63, "Genre", "Pop hits"),
        YouTubeGenre("hiphop", "Hip-Hop & Rap", "Beats, rhymes & street flow", 0xFFFF5722, "Genre", "Hip Hop rap hits"),
        YouTubeGenre("rock", "Rock & Alt", "Classic riffs, indie & alternative", 0xFF9C27B0, "Genre", "Rock alternative hits"),
        YouTubeGenre("rnb", "R&B & Soul", "Smooth grooves, neo-soul & vocals", 0xFF673AB7, "Genre", "R&B soul hits"),
        YouTubeGenre("electronic", "Electronic & Dance", "EDM, house, techno & synthwave", 0xFF00BCD4, "Genre", "EDM electronic dance hits"),
        YouTubeGenre("indie", "Indie & Alternative", "Bedroom pop, dream pop & post-punk", 0xFF4CAF50, "Genre", "Indie alternative hits"),
        YouTubeGenre("jazz", "Jazz & Blues", "Bebop, modern jazz & soulful brass", 0xFFFF9800, "Genre", "Jazz music classics"),
        YouTubeGenre("classical", "Classical", "Symphonic, piano & orchestral masterpieces", 0xFF3F51B5, "Genre", "Classical music"),
        YouTubeGenre("metal", "Metal & Heavy", "Heavy distortion, thrash & core", 0xFF37474F, "Genre", "Heavy metal rock hits"),
        YouTubeGenre("acoustic", "Acoustic & Folk", "Unplugged strings & organic harmony", 0xFF8D6E63, "Genre", "Acoustic folk songs"),
        YouTubeGenre("kpop", "K-Pop", "Korean pop, idols & vibrant dance", 0xFFFF4081, "Genre", "K-Pop hits"),
        YouTubeGenre("latin", "Latin & Reggaeton", "Bachata, salsa, urbano & rhythm", 0xFFFF1744, "Genre", "Latin reggaeton hits"),
        YouTubeGenre("desi", "Bollywood & Desi", "Indian cinematic & indie tracks", 0xFFFF6D00, "Genre", "Bollywood hindi hits"),
        YouTubeGenre("lofi", "Lofi & Chillhop", "Study beats, vinyl warmth & calm", 0xFF009688, "Genre", "Lofi chillhop beats")
    )

    val moods = listOf(
        YouTubeGenre("chill", "Chill & Relax", "Downtempo, ambient & unwinding", 0xFF00838F, "Mood", "Chill relaxing music"),
        YouTubeGenre("workout", "Workout & Cardio", "High-BPM motivation & pump anthems", 0xFFD50000, "Mood", "Workout motivation music"),
        YouTubeGenre("focus", "Focus & Study", "Instrumental concentration & flow states", 0xFF2E7D32, "Mood", "Focus study instrumental music"),
        YouTubeGenre("energy", "Energy Boost", "Feel-good pop, dance & euphoria", 0xFFFFAB00, "Mood", "High energy upbeat music"),
        YouTubeGenre("sleep", "Sleep & Night", "Drones, soft piano & atmospheric calm", 0xFF1A237E, "Mood", "Sleep relaxing ambient music"),
        YouTubeGenre("party", "Party & Club", "Floor-fillers, basslines & festival hits", 0xFFAA00FF, "Mood", "Party club dance hits"),
        YouTubeGenre("romance", "Romance & Love", "Heartfelt ballads & intimate slow jams", 0xFFC2185B, "Mood", "Romantic love songs")
    )

    val all: List<YouTubeGenre> = genres + moods

    fun findGenreOrMood(name: String): YouTubeGenre {
        val lower = name.lowercase().trim()
        val normalized = lower.replace("-", " ").replace("&", " ").replace("/", " ").replace("_", " ")
        val tokens = normalized.split("\\s+".toRegex()).filter { it.isNotBlank() }

        // 1. Exact ID or Title match
        all.firstOrNull { it.id == lower || it.title.lowercase() == lower }?.let { return it }

        // 2. Normalized Title match
        all.firstOrNull { it.title.lowercase().replace("-", " ").replace("&", " ") == normalized }?.let { return it }

        // 3. Exact word/token matching with priority to longer IDs (e.g. kpop before pop)
        all.sortedByDescending { it.id.length }.firstOrNull { genre ->
            val genreTokens = genre.title.lowercase().replace("-", " ").replace("&", " ").split("\\s+".toRegex())
            tokens.any { token -> token == genre.id || genreTokens.contains(token) }
        }?.let { return it }

        // 4. Fallback for custom or unknown genres
        return YouTubeGenre(
            id = lower.replace(" ", "_"),
            title = name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
            subtitle = "YouTube Music Explore",
            colorHex = 0xFF6200EE,
            category = "Genre",
            searchQuery = "$name songs"
        )
    }
}
