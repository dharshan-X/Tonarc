package com.quietrays.tonarc.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Room entity for storing song engagement statistics.
 * This replaces the JSON-based storage in DailyMixManager for better performance
 * and structured querying.
 */
@Entity(
    tableName = "song_engagements",
    indices = [
        Index(value = ["play_count"], unique = false)
    ]
)
data class SongEngagementEntity(
    @PrimaryKey
    @ColumnInfo(name = "song_id")
    @SerializedName(value = "songId", alternate = ["song_id"])
    val songId: String,
    
    @ColumnInfo(name = "play_count")
    @SerializedName(value = "playCount", alternate = ["play_count", "score", "plays"])
    val playCount: Int = 0,
    
    @ColumnInfo(name = "total_play_duration_ms")
    @SerializedName(
        value = "totalPlayDurationMs",
        alternate = ["total_play_duration_ms", "totalDuration", "total_duration", "durationMs", "duration_ms"]
    )
    val totalPlayDurationMs: Long = 0L,
    
    @ColumnInfo(name = "last_played_timestamp")
    @SerializedName(
        value = "lastPlayedTimestamp",
        alternate = ["last_played_timestamp", "lastPlayedAt", "last_played_at", "timestamp"]
    )
    val lastPlayedTimestamp: Long = 0L,

    @ColumnInfo(name = "skip_before_30s_count")
    @SerializedName(value = "skipBefore30sCount", alternate = ["skip_before_30s_count", "skips"])
    val skipBefore30sCount: Int = 0,

    @ColumnInfo(name = "completion_count")
    @SerializedName(value = "completionCount", alternate = ["completion_count", "completions"])
    val completionCount: Int = 0,

    @ColumnInfo(name = "session_repeat_count")
    @SerializedName(value = "sessionRepeatCount", alternate = ["session_repeat_count", "repeats"])
    val sessionRepeatCount: Int = 0,

    @ColumnInfo(name = "last_session_id")
    @SerializedName(value = "lastSessionId", alternate = ["last_session_id"])
    val lastSessionId: String? = null
)
