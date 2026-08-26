package com.quietrays.tonarc.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a cached or synced YouTube Music playlist.
 */
@Entity(tableName = "youtube_playlists")
data class YouTubePlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val author: String? = null,
    @ColumnInfo(name = "song_count") val songCount: Int = 0,
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String? = null,
    @ColumnInfo(name = "date_added") val dateAdded: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "date_modified") val dateModified: Long = System.currentTimeMillis()
)
