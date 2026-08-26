package com.quietrays.tonarc.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class EngagementDaoTest {

    private lateinit var database: TonarcDatabase
    private lateinit var engagementDao: EngagementDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TonarcDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        engagementDao = database.engagementDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun recordPlay_upsertsPlayCountAndDuration() = runTest {
        engagementDao.recordPlay("song_1", 30000L, 1000L)
        engagementDao.recordPlay("song_1", 45000L, 2000L)

        val engagement = engagementDao.getEngagement("song_1")
        assertThat(engagement).isNotNull()
        assertThat(engagement?.playCount).isEqualTo(2)
        assertThat(engagement?.totalPlayDurationMs).isEqualTo(75000L)
        assertThat(engagement?.lastPlayedTimestamp).isEqualTo(2000L)
    }

    @Test
    fun recordSkip_incrementsSkipCount() = runTest {
        engagementDao.recordSkip("song_1", 1000L)
        engagementDao.recordSkip("song_1", 2000L)

        val engagement = engagementDao.getEngagement("song_1")
        assertThat(engagement).isNotNull()
        assertThat(engagement?.skipBefore30sCount).isEqualTo(2)
        assertThat(engagement?.lastPlayedTimestamp).isEqualTo(2000L)
    }

    @Test
    fun recordCompletion_incrementsCompletionCount() = runTest {
        engagementDao.recordCompletion("song_1", 1500L)
        engagementDao.recordCompletion("song_1", 3000L)

        val engagement = engagementDao.getEngagement("song_1")
        assertThat(engagement).isNotNull()
        assertThat(engagement?.completionCount).isEqualTo(2)
        assertThat(engagement?.lastPlayedTimestamp).isEqualTo(3000L)
    }

    @Test
    fun recordSessionRepeat_updatesRepeatCountAndSessionId() = runTest {
        engagementDao.recordSessionRepeat("song_1", "session_abc", 5000L)
        engagementDao.recordSessionRepeat("song_1", "session_abc", 6000L)

        val engagement = engagementDao.getEngagement("song_1")
        assertThat(engagement).isNotNull()
        assertThat(engagement?.sessionRepeatCount).isEqualTo(2)
        assertThat(engagement?.lastSessionId).isEqualTo("session_abc")
        assertThat(engagement?.lastPlayedTimestamp).isEqualTo(6000L)
    }
}
