package com.iquintero.kavern.ui.download

import com.iquintero.kavern.data.local.db.entity.DownloadJobV2Entity
import com.iquintero.kavern.data.local.db.entity.DownloadedItemV2Entity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadGroupingTest {
    private fun item(
        id: Long,
        seriesId: Int?,
        chapterId: Int?,
        bytes: Long?,
        updatedAt: Long,
    ) = DownloadedItemV2Entity(
        id = id,
        jobId = 1,
        type = DownloadedItemV2Entity.TYPE_PAGE,
        seriesId = seriesId,
        volumeId = null,
        chapterId = chapterId,
        page = null,
        url = null,
        localPath = null,
        bytes = bytes,
        checksum = null,
        status = DownloadedItemV2Entity.STATUS_COMPLETED,
        createdAt = updatedAt,
        updatedAt = updatedAt,
        format = null,
        error = null,
    )

    private fun job(
        id: Long,
        status: String,
        createdAt: Long = id,
        total: Int? = null,
        done: Int? = null,
    ) = DownloadJobV2Entity(
        id = id,
        type = DownloadJobV2Entity.TYPE_CHAPTER,
        format = null,
        strategy = null,
        seriesId = 1,
        volumeId = null,
        chapterId = 1,
        status = status,
        totalItems = total,
        completedItems = done,
        priority = 0,
        createdAt = createdAt,
        updatedAt = createdAt,
        error = null,
    )

    @Test
    fun theManyPagesOfAnEpubBecomeOneChapterOfOneBook() {
        val items = (1L..6L).map { item(it, seriesId = 3, chapterId = 3, bytes = 1_000, updatedAt = 100 + it) }
        val books = groupDownloads(items, DownloadLookup(seriesNames = mapOf(3 to "Dune"), chapterTitles = mapOf(3 to "Chapter 1")))
        val book = books.single()
        assertEquals("Dune", book.title)
        assertEquals(1, book.chapters.size)
        assertEquals(6_000L, book.bytes)
        assertEquals(106L, book.updatedAt)
        assertEquals((1L..6L).toList(), book.itemIds)
    }

    @Test
    fun aSeriesKeepsItsChaptersInOrderAndSumsTheirSizes() {
        val items =
            listOf(
                item(1, 5, chapterId = 20, bytes = 300, updatedAt = 10),
                item(2, 5, chapterId = 10, bytes = 200, updatedAt = 20),
                item(3, 5, chapterId = 10, bytes = 100, updatedAt = 30),
            )
        val book = groupDownloads(items, DownloadLookup()).single()
        assertEquals(listOf(10, 20), book.chapters.map { it.chapterId })
        assertEquals(300L, book.chapters[0].bytes)
        assertEquals(listOf(2L, 3L), book.chapters[0].itemIds)
        assertEquals(600L, book.bytes)
    }

    @Test
    fun mostRecentlyDownloadedBookComesFirst() {
        val items =
            listOf(
                item(1, seriesId = 1, chapterId = 1, bytes = 1, updatedAt = 10),
                item(2, seriesId = 2, chapterId = 2, bytes = 1, updatedAt = 50),
            )
        assertEquals(listOf(2, 1), groupDownloads(items, DownloadLookup()).map { it.seriesId })
    }

    @Test
    fun unknownTitlesAndSizesAreHandled() {
        val book = groupDownloads(listOf(item(1, seriesId = 9, chapterId = null, bytes = null, updatedAt = 5)), DownloadLookup()).single()
        assertNull(book.title)
        assertNull(book.chapters.single().label)
        assertEquals(0L, book.bytes)
    }

    @Test
    fun chaptersAreLabelledByVolumeAndSkipKavitasPlaceholder() {
        val lookup =
            DownloadLookup(
                volumeNames = mapOf(7 to "Volume 2"),
                chapterTitles = mapOf(1 to "Issue #-100000", 2 to "The Wall", 3 to "Chapter 3"),
            )
        assertEquals("Volume 2", chapterLabel(7, 1, lookup))
        assertEquals("Volume 2 · The Wall", chapterLabel(7, 2, lookup))
        assertEquals("Chapter 3", chapterLabel(null, 3, lookup))
        assertNull(chapterLabel(null, 1, lookup))
    }

    @Test
    fun itemsWithoutASeriesAreLeftOut() {
        assertEquals(emptyList<DownloadedBook>(), groupDownloads(listOf(item(1, seriesId = null, chapterId = 1, bytes = 1, updatedAt = 1)), DownloadLookup()))
    }

    @Test
    fun activeDownloadsHideFinishedAndCanceledAndPutRunningFirst() {
        val jobs =
            listOf(
                job(1, DownloadJobV2Entity.STATUS_FAILED),
                job(2, DownloadJobV2Entity.STATUS_COMPLETED),
                job(3, DownloadJobV2Entity.STATUS_PENDING),
                job(4, DownloadJobV2Entity.STATUS_CANCELED),
                job(5, DownloadJobV2Entity.STATUS_RUNNING),
                job(6, DownloadJobV2Entity.STATUS_PAUSED),
            )
        assertEquals(listOf(5L, 3L, 6L, 1L), activeDownloads(jobs).map { it.id })
    }

    @Test
    fun percentIsUnknownUntilTheSizeIsKnownAndNeverExceeds100() {
        assertNull(downloadPercent(job(1, DownloadJobV2Entity.STATUS_RUNNING)))
        assertNull(downloadPercent(job(1, DownloadJobV2Entity.STATUS_RUNNING, total = 0)))
        assertEquals(33, downloadPercent(job(1, DownloadJobV2Entity.STATUS_RUNNING, total = 6, done = 2)))
        assertEquals(100, downloadPercent(job(1, DownloadJobV2Entity.STATUS_RUNNING, total = 6, done = 9)))
    }
}
