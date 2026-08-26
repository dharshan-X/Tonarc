package com.quietrays.tonarc.data.network.youtube

/**
 * Data models for YouTube Music Innertube API communication.
 */
data class InnertubeTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val durationSeconds: Long = 0L,
    val thumbnailUri: String? = null,
    val isExplicit: Boolean = false
)

data class InnertubeAlbum(
    val browseId: String,
    val title: String,
    val artist: String,
    val year: Int? = null,
    val thumbnailUri: String? = null,
    val trackCount: Int = 0
)

data class InnertubeArtist(
    val browseId: String,
    val name: String,
    val thumbnailUri: String? = null,
    val subscribers: String? = null
)

data class InnertubePlaylist(
    val playlistId: String,
    val title: String,
    val author: String? = null,
    val trackCount: Int = 0,
    val thumbnailUri: String? = null
)

data class InnertubeStreamFormat(
    val itag: Int,
    val mimeType: String,
    val bitrate: Int,
    val sampleRate: Int? = null,
    val contentLength: Long? = null,
    val url: String? = null,
    val audioQuality: String? = null,
    val approxDurationMs: Long? = null
) {
    val isOpus: Boolean get() = mimeType.contains("opus") || mimeType.contains("webm")
    val isAac: Boolean get() = mimeType.contains("mp4a") || mimeType.contains("m4a") || mimeType.contains("aac")
}

data class InnertubeStreamInfo(
    val videoId: String,
    val title: String,
    val artist: String,
    val durationSeconds: Long,
    val formats: List<InnertubeStreamFormat>,
    val selectedFormatUrl: String? = null,
    val highestBitrateOpusUrl: String? = null,
    val highestBitrateAacUrl: String? = null,
    val expireAtTimestampMs: Long = System.currentTimeMillis() + (4 * 3600 * 1000L)
)

data class InnertubeSearchResult(
    val query: String,
    val songs: List<InnertubeTrack> = emptyList(),
    val albums: List<InnertubeAlbum> = emptyList(),
    val artists: List<InnertubeArtist> = emptyList(),
    val playlists: List<InnertubePlaylist> = emptyList(),
    val continuationToken: String? = null
)

data class InnertubeBrowseSection(
    val title: String,
    val subtitle: String? = null,
    val tracks: List<InnertubeTrack> = emptyList(),
    val albums: List<InnertubeAlbum> = emptyList(),
    val playlists: List<InnertubePlaylist> = emptyList()
)
