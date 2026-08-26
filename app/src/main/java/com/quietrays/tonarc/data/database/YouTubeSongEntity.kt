package com.quietrays.tonarc.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.quietrays.tonarc.data.model.Song

/**
 * Represents a cached or synced YouTube Music track.
 */
@Entity(
    tableName = "youtube_songs",
    indices = [
        Index(value = ["video_id"]),
        Index(value = ["playlist_id"]),
        Index(value = ["playlist_id", "date_added"])
    ]
)
data class YouTubeSongEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "video_id") val videoId: String,
    @ColumnInfo(name = "playlist_id") val playlistId: String = "__library__",
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Long = 0L,
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String? = null,
    val year: Int = 0,
    @ColumnInfo(name = "date_added") val dateAdded: Long = System.currentTimeMillis()
) {
    fun toSong(): Song {
        return Song(
            id = "youtube_$videoId",
            title = title,
            artist = artist,
            artistId = 0L,
            album = album ?: "YouTube Music",
            albumId = 0L,
            albumArtist = artist,
            path = "youtube://$videoId",
            contentUriString = "youtube://$videoId",
            albumArtUriString = thumbnailUrl,
            duration = duration,
            year = year,
            dateAdded = dateAdded,
            dateModified = dateAdded,
            mimeType = "audio/webm",
            bitrate = 160000,
            sampleRate = 48000,
            youtubeId = videoId
        )
    }
}
