package com.quietrays.tonarc.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for recording and querying on-device pairwise item co-occurrence graphs.
 */
@Dao
interface ItemCooccurrenceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cooccurrence: ItemCooccurrenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cooccurrences: List<ItemCooccurrenceEntity>)

    @androidx.room.Transaction
    suspend fun batchIncrementCooccurrences(pairs: List<Pair<String, String>>, timestamp: Long) {
        pairs.chunked(50).forEach { chunk ->
            for ((a, b) in chunk) {
                val (first, second) = if (a < b) Pair(a, b) else Pair(b, a)
                incrementCooccurrence(first, second, timestamp)
            }
        }
    }

    @Query("""
        INSERT INTO item_cooccurrences (song_id_a, song_id_b, cooccurrence_count, last_updated_timestamp)
        VALUES (:songA, :songB, 1, :timestamp)
        ON CONFLICT(song_id_a, song_id_b) DO UPDATE SET
            cooccurrence_count = cooccurrence_count + 1,
            last_updated_timestamp = :timestamp
    """)
    suspend fun incrementCooccurrence(songA: String, songB: String, timestamp: Long)

    @Query("""
        SELECT * FROM item_cooccurrences
        WHERE song_id_a = :songId OR song_id_b = :songId
        ORDER BY cooccurrence_count DESC
        LIMIT :limit
    """)
    suspend fun getCooccurrencesForSong(songId: String, limit: Int): List<ItemCooccurrenceEntity>

    @Query("""
        SELECT SUM(cooccurrence_count) FROM item_cooccurrences
        WHERE song_id_a = :songId OR song_id_b = :songId
    """)
    suspend fun getTotalCooccurrenceCountForSong(songId: String): Long?

    @Query("DELETE FROM item_cooccurrences WHERE cooccurrence_count <= :minCount AND last_updated_timestamp < :staleBeforeTimestamp")
    suspend fun pruneStale(minCount: Int, staleBeforeTimestamp: Long): Int

    @Query("SELECT COUNT(*) FROM item_cooccurrences")
    suspend fun getEdgeCount(): Int

    @Query("SELECT * FROM item_cooccurrences ORDER BY cooccurrence_count DESC LIMIT :limit")
    suspend fun getTopCooccurrences(limit: Int): List<ItemCooccurrenceEntity>

    @Query("DELETE FROM item_cooccurrences")
    suspend fun clearAll()
}
