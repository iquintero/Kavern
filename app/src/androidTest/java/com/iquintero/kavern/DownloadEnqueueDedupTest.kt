package com.iquintero.kavern

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iquintero.kavern.core.downloadv2.DownloadRequestV2
import com.iquintero.kavern.core.downloadv2.strategies.PdfDownloadStrategyV2
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.data.local.db.KavernDatabase
import com.iquintero.kavern.data.local.db.entity.DownloadJobV2Entity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadEnqueueDedupTest {
    private fun request(chapterId: Int) =
        DownloadRequestV2(
            type = DownloadJobV2Entity.TYPE_CHAPTER,
            format = PdfDownloadStrategyV2.FORMAT_PDF,
            seriesId = 1,
            volumeId = 2,
            chapterId = chapterId,
        )

    private fun withStrategy(block: suspend (PdfDownloadStrategyV2, KavernDatabase) -> Unit) =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val database = Room.inMemoryDatabaseBuilder(context, KavernDatabase::class.java).build()
            try {
                block(PdfDownloadStrategyV2(context, database.downloadV2Dao(), AppPreferences(context)), database)
            } finally {
                database.close()
            }
        }

    @Test
    fun enqueueingTheSameDownloadTwiceReturnsTheJobAlreadyQueued() =
        withStrategy { strategy, database ->
            val first = strategy.enqueue(request(chapterId = 10))
            val second = strategy.enqueue(request(chapterId = 10))

            assertEquals(first, second)
            assertEquals(1, database.downloadV2Dao().getJobsByStatus(DownloadJobV2Entity.STATUS_PENDING).size)
        }

    @Test
    fun differentChaptersGetTheirOwnJobs() =
        withStrategy { strategy, database ->
            val first = strategy.enqueue(request(chapterId = 10))
            val second = strategy.enqueue(request(chapterId = 11))

            assertNotEquals(first, second)
            assertEquals(2, database.downloadV2Dao().getJobsByStatus(DownloadJobV2Entity.STATUS_PENDING).size)
        }

    @Test
    fun aFinishedJobDoesNotBlockANewDownload() =
        withStrategy { strategy, database ->
            val dao = database.downloadV2Dao()
            val first = strategy.enqueue(request(chapterId = 10))
            dao.transitionJob(first, listOf(DownloadJobV2Entity.STATUS_PENDING), DownloadJobV2Entity.STATUS_FAILED)

            assertNotEquals(first, strategy.enqueue(request(chapterId = 10)))
        }
}
