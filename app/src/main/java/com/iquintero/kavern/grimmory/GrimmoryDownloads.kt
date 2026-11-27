package com.iquintero.kavern.grimmory

import com.iquintero.kavern.core.downloadv2.DownloadManagerV2
import com.iquintero.kavern.core.downloadv2.DownloadRequestV2
import com.iquintero.kavern.core.downloadv2.DownloadStorageManager
import com.iquintero.kavern.core.downloadv2.strategies.EpubDownloadStrategyV2
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.data.local.db.KavernDatabase
import com.iquintero.kavern.data.local.db.entity.DownloadJobV2Entity
import com.iquintero.kavern.data.local.db.entity.DownloadedItemV2Entity
import com.iquintero.kavern.ui.common.DownloadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** What is known of one book's download. */
internal data class DownloadEntry(
    val bookId: Int,
    val state: DownloadState,
    val bytes: Long,
    /** How much of the download is done, in the engine's own parts. */
    val done: Int,
    val total: Int,
    val failed: Boolean,
    val updatedAt: Long,
)

/**
 * Downloading books to read without the server. A book is downloaded as a whole: the download engine keeps it page by
 * page, but nothing here or in the screens counts them.
 */
internal class GrimmoryDownloads(
    private val appPreferences: AppPreferences,
) {
    private val db = KavernDatabase.getInstance(appPreferences.appContext, appPreferences.profile)
    private val dao = db.downloadV2Dao()
    private val storage = DownloadStorageManager(appPreferences.appContext, appPreferences)
    private val manager =
        DownloadManagerV2(
            appPreferences,
            mapOf(EpubDownloadStrategyV2.FORMAT_EPUB to EpubDownloadStrategyV2(appPreferences.appContext, dao, appPreferences)),
        )

    /** Every book that is downloaded or being downloaded, the most recently touched first. */
    val entries: Flow<List<DownloadEntry>> =
        combine(dao.observeJobs(), dao.observeItemsByStatus(DownloadedItemV2Entity.STATUS_COMPLETED)) { jobs, done ->
            val jobsByBook = jobs.filter { it.chapterId != null }.groupBy { it.chapterId!! }
            val doneByBook = done.filter { it.chapterId != null }.groupBy { it.chapterId!! }
            (jobsByBook.keys + doneByBook.keys)
                .map { id ->
                    val mine = jobsByBook[id].orEmpty()
                    val parts = doneByBook[id].orEmpty()
                    val finished = (mine.isNotEmpty() && mine.all { it.status == DownloadJobV2Entity.STATUS_COMPLETED }) || (mine.isEmpty() && parts.isNotEmpty())
                    DownloadEntry(
                        bookId = id,
                        state = if (finished) DownloadState.Complete else DownloadState.Partial,
                        bytes = parts.sumOf { it.bytes ?: 0L },
                        done = mine.sumOf { it.completedItems ?: 0 },
                        total = mine.sumOf { it.totalItems ?: 0 },
                        failed = mine.any { it.status == DownloadJobV2Entity.STATUS_FAILED },
                        updatedAt = maxOf(mine.maxOfOrNull { it.updatedAt } ?: 0L, parts.maxOfOrNull { it.updatedAt } ?: 0L),
                    )
                }.sortedByDescending { it.updatedAt }
        }

    /** The download state of each book that has one, by book id. */
    val states: Flow<Map<Int, DownloadState>> = entries.map { list -> list.associate { it.bookId to it.state } }

    /** How many parts of [bookId] are on the device, counted as far as the last one, or null when none is. */
    suspend fun downloadedParts(bookId: Int): Int? =
        dao
            .getItemsForChapter(bookId)
            .filter { it.status == DownloadedItemV2Entity.STATUS_COMPLETED && it.page != null }
            .maxOfOrNull { it.page!! }
            ?.plus(1)

    /** Queues [bookId] for download; [pages] is the number of parts the book is fetched in. */
    suspend fun download(
        bookId: Int,
        pages: Int,
    ) {
        manager.enqueue(
            DownloadRequestV2(
                type = DownloadJobV2Entity.TYPE_CHAPTER,
                format = EpubDownloadStrategyV2.FORMAT_EPUB,
                seriesId = bookId,
                volumeId = bookId,
                chapterId = bookId,
                pageCount = pages,
            ),
        )
    }

    /** Deletes what was downloaded of [bookId], and stops any download of it. */
    suspend fun remove(bookId: Int) {
        dao.deleteJobsForChapter(bookId)
        dao.getItemsForChapter(bookId).forEach { storage.deleteDownload(db, it.id) }
        dao.deleteItemsForChapter(bookId)
    }
}
