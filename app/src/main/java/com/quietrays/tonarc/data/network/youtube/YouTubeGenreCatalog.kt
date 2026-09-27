package com.quietrays.tonarc.data.network.youtube

import com.quietrays.tonarc.data.model.Playlist
import com.quietrays.tonarc.data.model.Song

data class YouTubeGenre(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val colorHex: Long = 0xFF6200EE,
    val iconEmoji: String = "🎵",
    val category: String = "Genre" // "Genre" or "Mood"
)

data class YouTubeGenreExploreResult(
    val genre: YouTubeGenre,
    val topSongs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList()
)

object YouTubeGenreCatalog {
    val genres = listOf(
        YouTubeGenre("pop", "Pop", "Chart-toppers, hooks & global hits", 0xFFE91E63, "🎤", "Genre"),
        YouTubeGenre("hiphop", "Hip-Hop & Rap", "Beats, rhymes & street flow", 0xFFFF5722, "🔥", "Genre"),
        YouTubeGenre("rock", "Rock & Alt", "Classic riffs, indie & alternative", 0xFF9C27B0, "🎸", "Genre"),
        YouTubeGenre("rnb", "R&B & Soul", "Smooth grooves, neo-soul & vocals", 0xFF673AB7, "🎷", "Genre"),
        YouTubeGenre("electronic", "Electronic & Dance", "EDM, house, techno & synthwave", 0xFF00BCD4, "⚡", "Genre"),
        YouTubeGenre("indie", "Indie & Alternative", "Bedroom pop, dream pop & post-punk", 0xFF4CAF50, "🌿", "Genre"),
        YouTubeGenre("jazz", "Jazz & Blues", "Bebop, modern jazz & soulful brass", 0xFFFF9800, "🎺", "Genre"),
        YouTubeGenre("classical", "Classical", "Symphonic, piano & orchestral masterpieces", 0xFF3F51B5, "🎻", "Genre"),
        YouTubeGenre("metal", "Metal & Heavy", "Heavy distortion, thrash & core", 0xFF37474F, "⚡", "Genre"),
        YouTubeGenre("acoustic", "Acoustic & Folk", "Unplugged strings & organic harmony", 0xFF8D6E63, "🪕", "Genre"),
        YouTubeGenre("kpop", "K-Pop", "Korean pop, idols & vibrant dance", 0xFFFF4081, "✨", "Genre"),
        YouTubeGenre("latin", "Latin & Reggaeton", "Bachata, salsa, urbano & rhythm", 0xFFFF1744, "💃", "Genre"),
        YouTubeGenre("desi", "Bollywood & Desi", "Indian cinematic & indie tracks", 0xFFFF6D00, "🪘", "Genre"),
        YouTubeGenre("lofi", "Lofi & Chillhop", "Study beats, vinyl warmth & calm", 0xFF009688, "☕", "Genre")
    )

    val moods = listOf(
        YouTubeGenre("chill", "Chill & Relax", "Downtempo, ambient & unwinding", 0xFF00838F, "🌙", "Mood"),
        YouTubeGenre("workout", "Workout & Cardio", "High-BPM motivation & pump anthems", 0xFFD50000, "🏃", "Mood"),
        YouTubeGenre("focus", "Focus & Study", "Instrumental concentration & flow states", 0xFF2E7D32, "💡", "Mood"),
        YouTubeGenre("energy", "Energy Boost", "Feel-good pop, dance & euphoria", 0xFFFFAB00, "⚡", "Mood"),
        YouTubeGenre("sleep", "Sleep & Night", "Drones, soft piano & atmospheric calm", 0xFF1A237E, "🌌", "Mood"),
        YouTubeGenre("party", "Party & Club", "Floor-fillers, basslines & festival hits", 0xFFAA00FF, "🎉", "Mood"),
        YouTubeGenre("romance", "Romance & Love", "Heartfelt ballads & intimate slow jams", 0xFFC2185B, "❤️", "Mood")
    )

    val all: List<YouTubeGenre> = genres + moods

    fun findGenreOrMood(name: String): YouTubeGenre {
        val lower = name.lowercase().trim()
        return all.firstOrNull { 
            it.id == lower || 
            it.title.lowercase() == lower || 
            lower.contains(it.id) || 
            it.title.lowercase().contains(lower) 
        } ?: YouTubeGenre(
            id = lower.replace(" ", "_"),
            title = name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
            subtitle = "YouTube Music Explore",
            colorHex = 0xFF6200EE,
            iconEmoji = "🎵"
        )
    }
}
