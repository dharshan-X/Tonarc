package com.quietrays.tonarc.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Room entity tracking pairwise co-occurrence frequencies of songs played in proximity
 * during user listening sessions (on-device prod2vec sparse graph).
 */
@Entity(
    tableName = "item_cooccurrences",
    primaryKeys = ["song_id_a", "song_id_b"],
    indices = [
        Index(value = ["song_id_a"]),
        Index(value = ["song_id_b"]),
        Index(value = ["cooccurrence_count"])
    ]
)
data class ItemCooccurrenceEntity(
    @ColumnInfo(name = "song_id_a")
    val songIdA: String,

    @ColumnInfo(name = "song_id_b")
    val songIdB: String,

    @ColumnInfo(name = "cooccurrence_count")
    val cooccurrenceCount: Int = 1,

    @ColumnInfo(name = "last_updated_timestamp")
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)
