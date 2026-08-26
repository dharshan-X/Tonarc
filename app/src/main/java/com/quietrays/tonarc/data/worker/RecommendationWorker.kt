package com.quietrays.tonarc.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.quietrays.tonarc.data.database.EngagementDao
import com.quietrays.tonarc.data.database.ItemCooccurrenceDao
import com.quietrays.tonarc.data.listenbrainz.ListenBrainzRepository
import com.quietrays.tonarc.data.recommendation.ItemEmbeddingStore
import com.quietrays.tonarc.data.repository.MusicRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Background worker to periodically maintain recommendation graphs:
 * - Prunes stale item co-occurrence counts (>30 days).
 * - Pre-fetches and caches ListenBrainz Labs recordings for top played artists.
 * - Ingests session co-occurrences in chunked Room transactions to prevent DB lock contention during playback.
 */
@HiltWorker
class RecommendationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val itemEmbeddingStore: ItemEmbeddingStore,
    private val itemCooccurrenceDao: ItemCooccurrenceDao,
    private val engagementDao: EngagementDao,
    private val musicRepository: MusicRepository,
    private val listenBrainzRepository: ListenBrainzRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val PERIODIC_WORK_NAME = "RecommendationMaintenanceWork"

        fun periodicWork(): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            return PeriodicWorkRequestBuilder<RecommendationWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Timber.tag("RecommendationWorker").i("Starting periodic recommendation maintenance")
        return@withContext try {
            // 1. Prune stale co-occurrences older than 30 days
            val thirtyDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
            val pruned = itemEmbeddingStore.pruneStaleEntries(thirtyDaysAgo)
            Timber.tag("RecommendationWorker").d("Pruned %d stale co-occurrence entries", pruned)

            // 2. Ingest top engagement session co-occurrences in chunked transactions
            val topEngagements = engagementDao.getTopPlayedSongs(30)
            if (topEngagements.size >= 2) {
                val pairs = mutableListOf<Pair<String, String>>()
                for (i in 0 until topEngagements.size - 1) {
                    val a = topEngagements[i].songId.trim()
                    val b = topEngagements[i + 1].songId.trim()
                    if (a.isNotEmpty() && b.isNotEmpty() && a != b) {
                        pairs.add(Pair(a, b))
                    }
                }
                if (pairs.isNotEmpty()) {
                    itemCooccurrenceDao.batchIncrementCooccurrences(pairs, System.currentTimeMillis())
                    Timber.tag("RecommendationWorker").d("Batch updated %d co-occurrence pairs in chunked transactions", pairs.size)
                }
            }

            // 3. Pre-fetch LB radio for top 10 played songs
            val topSongIds = topEngagements.take(10).map { it.songId }.toSet()
            val allSongs = runCatching { musicRepository.getAudioFiles().first() }.getOrDefault(emptyList())
            val seedSongs = allSongs.filter { it.id in topSongIds }

            for (song in seedSongs) {
                val artist = song.artist.trim().takeIf { it.isNotBlank() } ?: continue
                listenBrainzRepository.getLbRadioTracks(artist)
            }

            Timber.tag("RecommendationWorker").i("Completed recommendation maintenance successfully")
            Result.success()
        } catch (e: Exception) {
            Timber.tag("RecommendationWorker").e(e, "Recommendation maintenance failed")
            Result.retry()
        }
    }
}
