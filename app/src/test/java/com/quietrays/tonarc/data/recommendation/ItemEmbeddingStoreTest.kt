package com.quietrays.tonarc.data.recommendation

import com.google.common.truth.Truth.assertThat
import com.quietrays.tonarc.data.database.ItemCooccurrenceDao
import com.quietrays.tonarc.data.database.ItemCooccurrenceEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ItemEmbeddingStoreTest {

    private val dao: ItemCooccurrenceDao = mockk(relaxed = true)
    private val store = ItemEmbeddingStore(dao)

    @Test
    fun `recordPairwisePlay normalizes key order before DAO increment`() = runTest {
        store.recordPairwisePlay("song_z", "song_a", 1000L)
        coVerify { dao.incrementCooccurrence("song_a", "song_z", 1000L) }
    }

    @Test
    fun `getSimilarSongs computes normalized edge scores`() = runTest {
        val rows = listOf(
            ItemCooccurrenceEntity("song_1", "song_2", cooccurrenceCount = 10),
            ItemCooccurrenceEntity("song_1", "song_3", cooccurrenceCount = 5)
        )
        coEvery { dao.getCooccurrencesForSong("song_1", 20) } returns rows

        val similar = store.getSimilarSongs("song_1", 10)
        assertThat(similar).hasSize(2)
        assertThat(similar[0].first).isEqualTo("song_2")
        assertThat(similar[0].second).isEqualTo(1.0)
        assertThat(similar[1].first).isEqualTo("song_3")
        assertThat(similar[1].second).isEqualTo(0.5)
    }
}
