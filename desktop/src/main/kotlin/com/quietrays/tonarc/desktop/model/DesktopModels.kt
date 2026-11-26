package com.quietrays.tonarc.desktop.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

enum class TrackSource {
    LOCAL,
    YOUTUBE
}

@Immutable
@Serializable
data class DesktopTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "Unknown Album",
    val albumArtist: String? = null,
    val durationMs: Long = 0L,
    val source: TrackSource,
    val filePath: String? = null,
    val videoId: String? = null,
    val artworkPath: String? = null,
    val artworkUrl: String? = null,
    val mimeType: String? = null,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val genre: String? = null,
    val embeddedLyrics: String? = null,
    val dateAddedMs: Long = 0L
) {
    val displayArtModel: Any?
        get() = artworkPath ?: artworkUrl

    val codecBadge: String
        get() = buildList {
            mimeType?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
            if (sampleRateHz != null && sampleRateHz > 48000) {
                add("${"%.1f".format(sampleRateHz / 1000.0)}kHz")
            }
            if (bitrateKbps != null && bitrateKbps > 0) {
                add("${bitrateKbps}kbps")
            }
            if (source == TrackSource.YOUTUBE && isEmpty()) {
                add("YTM")
            }
        }.joinToString(" • ")
}

@Immutable
@Serializable
data class DesktopPlaylist(
    val id: String,
    val name: String,
    val description: String = "",
    val trackIds: List<String> = emptyList(),
    val artworkUrl: String? = null,
    val isYouTube: Boolean = false,
    val browseId: String? = null
)

@Immutable
data class DesktopAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val year: Int? = null,
    val artworkModel: Any? = null,
    val trackCount: Int = 0,
    val source: TrackSource = TrackSource.LOCAL,
    val browseId: String? = null,
    val tracks: List<DesktopTrack> = emptyList()
)

@Immutable
data class DesktopArtist(
    val id: String,
    val name: String,
    val artworkModel: Any? = null,
    val subtitle: String? = null,
    val source: TrackSource = TrackSource.LOCAL,
    val browseId: String? = null,
    val tracks: List<DesktopTrack> = emptyList()
)

@Immutable
data class HomeShelf(
    val title: String,
    val subtitle: String? = null,
    val tracks: List<DesktopTrack> = emptyList(),
    val albums: List<DesktopAlbum> = emptyList(),
    val playlists: List<DesktopPlaylist> = emptyList()
)

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

enum class ReplayGainMode(val mpvFlag: String, val label: String) {
    OFF("no", "Off"),
    TRACK("track", "Track"),
    ALBUM("album", "Album")
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light")
}

enum class LibrarySortOption(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    DURATION("Duration"),
    DATE_ADDED("Recently Added")
}

enum class DesktopNavDestination(val label: String) {
    HOME("Home"),
    SEARCH("Search"),
    LIBRARY("Library"),
    PLAYLISTS("Playlists"),
    EQUALIZER("Equalizer"),
    SETTINGS("Settings")
}

enum class LibraryTab(val label: String) {
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    FAVORITES("Favorites"),
    FOLDERS("Folders")
}

enum class SearchFilter(val label: String) {
    ALL("All"),
    LOCAL("Local"),
    YOUTUBE_SONGS("YT Songs"),
    YOUTUBE_ALBUMS("YT Albums"),
    YOUTUBE_ARTISTS("YT Artists"),
    YOUTUBE_PLAYLISTS("YT Playlists")
}

enum class InspectorTab(val label: String) {
    NOW_PLAYING("Now Playing"),
    QUEUE("Queue"),
    LYRICS("Lyrics")
}

data class EqualizerPreset(
    val name: String,
    val bands: List<Float>
) {
    companion object {
        val BAND_FREQUENCIES = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        val BAND_LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

        val PRESETS = listOf(
            EqualizerPreset("Flat", List(10) { 0f }),
            EqualizerPreset("Bass Boost", listOf(6.5f, 5.5f, 4.0f, 2.0f, 0.5f, 0f, 0f, 0f, 0f, 0f)),
            EqualizerPreset("Rock", listOf(4.5f, 3.0f, 1.5f, -1.0f, -1.5f, 0.5f, 2.5f, 4.0f, 4.5f, 4.0f)),
            EqualizerPreset("Pop", listOf(-1.5f, 1.0f, 3.5f, 4.0f, 2.5f, 0f, -1.5f, -1.5f, 1.0f, 2.0f)),
            EqualizerPreset("Jazz", listOf(3.0f, 2.0f, 1.0f, 1.5f, -1.5f, -1.5f, 0f, 1.5f, 2.5f, 3.5f)),
            EqualizerPreset("Electronic", listOf(5.0f, 4.0f, 1.5f, 0f, -2.0f, 1.5f, 1.0f, 2.5f, 4.5f, 5.0f)),
            EqualizerPreset("Vocal", listOf(-2.0f, -2.0f, -1.0f, 1.5f, 4.0f, 4.5f, 3.5f, 1.5f, 0f, -1.0f))
        )
    }
}

@Serializable
data class PersistedDesktopSettings(
    val musicFolders: List<String> = emptyList(),
    val favoriteTrackIds: Set<String> = emptySet(),
    val playlists: List<DesktopPlaylist> = emptyList(),
    val recentTrackIds: List<String> = emptyList(),
    val themeMode: String = ThemeMode.DARK.name,
    val volume: Int = 85,
    val replayGain: String = ReplayGainMode.TRACK.name,
    val playbackSpeed: Float = 1.0f,
    val eqPresetName: String = "Flat",
    val eqBands: List<Float> = List(10) { 0f },
    val youtubeVisitorData: String? = null,
    val youtubeCookies: String? = null,
    val windowWidthDp: Float = 1280f,
    val windowHeightDp: Float = 820f
)
