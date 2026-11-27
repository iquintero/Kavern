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

    /** The download state of each book that has one, by book id. */
    val states: Flow<Map<Int, DownloadState>> =
        combine(dao.observeJobs(), dao.observeItemsByStatus(DownloadedItemV2Entity.STATUS_COMPLETED)) { jobs, done ->
            val jobsByBook = jobs.filter { it.chapterId != null }.groupBy { it.chapterId!! }
            val doneBooks = done.mapNotNull { it.chapterId }.toSet()
            buildMap {
                (jobsByBook.keys + doneBooks).forEach { id ->
                    val mine = jobsByBook[id].orEmpty()
                    val finished = mine.isNotEmpty() && mine.all { it.status == DownloadJobV2Entity.STATUS_COMPLETED }
                    put(id, if (finished || (mine.isEmpty() && id in doneBooks)) DownloadState.Complete else DownloadState.Partial)
                }
            }
        }

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
