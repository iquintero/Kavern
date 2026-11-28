package com.iquintero.kavern.grimmory

import android.content.Context
import com.iquintero.kavern.core.downloadv2.DownloadStorageManager
import com.iquintero.kavern.core.network.NetworkMonitor
import com.iquintero.kavern.core.network.ServerReachability
import com.iquintero.kavern.core.network.grimmory.GrimmoryHttpException
import com.iquintero.kavern.core.network.grimmory.GrimmoryReading
import com.iquintero.kavern.core.network.grimmory.GrimmoryServer
import com.iquintero.kavern.core.sessions.ReadingPoint
import com.iquintero.kavern.core.sessions.ReadingSessionRecorder
import com.iquintero.kavern.core.sessions.ReadingSessionUploader
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.core.sync.ProgressSyncWorker
import com.iquintero.kavern.core.util.runCatchingCancellable
import com.iquintero.kavern.data.local.db.dao.DownloadV2Dao
import com.iquintero.kavern.data.local.db.dao.ReaderDao
import com.iquintero.kavern.data.local.db.entity.CachedPageEntity
import com.iquintero.kavern.data.local.db.entity.DownloadedItemV2Entity
import com.iquintero.kavern.data.mapper.toDomain
import com.iquintero.kavern.data.mapper.toEntity
import com.iquintero.kavern.data.repository.ProgressUploadResult
import com.iquintero.kavern.data.repository.classifyProgressStatus
import com.iquintero.kavern.domain.model.ReaderBookInfo
import com.iquintero.kavern.domain.model.ReaderChapterNav
import com.iquintero.kavern.domain.model.ReaderImageResult
import com.iquintero.kavern.domain.model.ReaderPageResult
import com.iquintero.kavern.domain.model.ReaderProgress
import com.iquintero.kavern.domain.model.ReaderTimeLeft
import com.iquintero.kavern.domain.model.ReaderTocItem
import com.iquintero.kavern.domain.repository.ReaderRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * The reader's view of a Grimmory server. A book is its own unit here: the ids the reader passes around are all the
 * book's id, and a "page" is one part of the book, as it is for the download engine that keeps books for offline.
 */
internal class GrimmoryReaderRepository(
    private val context: Context,
    private val appPreferences: AppPreferences,
    private val readerDao: ReaderDao,
    private val downloadDao: DownloadV2Dao,
    private val server: GrimmoryServer,
) : ReaderRepository {
    private val reading: GrimmoryReading get() = server.reading
    private val storage = DownloadStorageManager(context, appPreferences)
    private val recorder = ReadingSessionRecorder(readerDao)
    private val tocCache = mutableMapOf<Int, List<ReaderTocItem>>()

    override suspend fun getPageResult(
        chapterId: Int,
        page: Int,
    ): ReaderPageResult {
        val preferOffline = appPreferences.preferOfflinePagesFlow.first()

        suspend fun downloaded(): ReaderPageResult? {
            val item = downloadDao.getDownloadedPageForChapter(chapterId, page) ?: return null
            val path = item.localPath ?: return null
            val html = storage.readText(path) ?: return null
            val assets =
                try {
                    storage.epubAssetPaths(item, path)
                } catch (_: IOException) {
                    emptySet()
                } catch (_: SecurityException) {
                    emptySet()
                }
            return ReaderPageResult(html, true, assets)
        }
        val cached = readerDao.getPage(chapterId, page)?.html
        if (!isOnlineAllowed()) {
            // Without the server: a downloaded copy, else the text of a page that was read before (images need the server).
            return downloaded() ?: cached?.let { ReaderPageResult(it, false) } ?: throw PageNotDownloadedException()
        }
        if (preferOffline) downloaded()?.let { return it }
        return try {
            val html = io { reading.page(chapterId, page) }
            readerDao.upsertPage(CachedPageEntity(chapterId = chapterId, page = page, html = html, updatedAt = System.currentTimeMillis()))
            ReaderPageResult(html, false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            if (!preferOffline) downloaded()?.let { return it }
            cached?.let { return ReaderPageResult(it, false) }
            throw e
        }
    }

    override suspend fun getImageResult(
        chapterId: Int,
        page: Int,
    ): ReaderImageResult = throw UnsupportedOperationException("Grimmory books are read as EPUBs")

    override suspend fun isPageDownloaded(
        chapterId: Int,
        page: Int,
    ): Boolean {
        val item = downloadDao.getDownloadedPageForChapter(chapterId, page) ?: return false
        return item.localPath?.let { storage.exists(it) } == true
    }

    override suspend fun getProgress(chapterId: Int): ReaderProgress? {
        val local = readerDao.getLocalProgress(chapterId)?.toDomain()
        if (!isOnlineAllowed()) return local
        val remote =
            runCatchingCancellable { io { reading.place(chapterId) } }
                .getOrNull()
                ?.let { ReaderProgress(chapterId, it.page, it.scrollId, chapterId, chapterId, null, it.updatedAtMs) }
        // What was read on this device and not yet sent wins when it is newer than what the server has.
        return if (local != null && (remote == null || local.lastModifiedUtcMillis > remote.lastModifiedUtcMillis)) local else remote
    }

    override suspend fun setProgress(
        progress: ReaderProgress,
        totalPages: Int?,
    ) {
        val stamped = progress.stamped()
        record(stamped, totalPages)
        if (!isOnlineAllowed()) {
            readerDao.upsertLocalProgress(stamped.toEntity())
            ProgressSyncWorker.enqueue(context, appPreferences.profile)
            return
        }
        when (send(stamped)) {
            ProgressUploadResult.Sent -> readerDao.clearLocalProgress(stamped.chapterId)
            ProgressUploadResult.RetryableFailure -> {
                readerDao.upsertLocalProgress(stamped.toEntity())
                // The current worker may already have read its batch.
                ProgressSyncWorker.enqueue(context, appPreferences.profile, followUp = true)
            }
            ProgressUploadResult.Rejected -> Unit
        }
        uploadReadingSessions()
    }

    override suspend fun saveProgressLocally(progress: ReaderProgress) {
        val stamped = progress.stamped()
        record(stamped, totalPages = null)
        readerDao.upsertLocalProgress(stamped.toEntity())
        ProgressSyncWorker.enqueue(context, appPreferences.profile)
    }

    override suspend fun getTimeLeft(
        seriesId: Int,
        chapterId: Int,
    ): ReaderTimeLeft? = null

    override suspend fun getPdfFile(chapterId: Int): File? = null

    override suspend fun getNextChapter(
        seriesId: Int,
        volumeId: Int,
        currentChapterId: Int,
    ): ReaderChapterNav? = neighbour(currentChapterId, 1)

    override suspend fun getPreviousChapter(
        seriesId: Int,
        volumeId: Int,
        currentChapterId: Int,
    ): ReaderChapterNav? = neighbour(currentChapterId, -1)

    override suspend fun getContinuePoint(seriesId: Int): ReaderChapterNav? = ReaderChapterNav(seriesId, seriesId, seriesId)

    override suspend fun getBookInfo(chapterId: Int): ReaderBookInfo? =
        runCatchingCancellable {
            if (!isOnlineAllowed()) return@runCatchingCancellable offlineInfo(chapterId)
            io {
                val book = server.catalog.book(chapterId) ?: return@io null
                ReaderBookInfo(
                    pages = reading.parts(book),
                    seriesId = book.id,
                    volumeId = book.id,
                    libraryId = book.libraryId,
                    title = book.title,
                )
            }
        }.getOrNull() ?: offlineInfo(chapterId)

    override suspend fun getLocalProgress(chapterId: Int): ReaderProgress? = readerDao.getLocalProgress(chapterId)?.toDomain()

    override suspend fun getCachedBookInfo(chapterId: Int): ReaderBookInfo? = offlineInfo(chapterId)

    override suspend fun getToc(chapterId: Int): List<ReaderTocItem> {
        synchronized(tocCache) { tocCache[chapterId] }?.let { return it }
        if (!isOnlineAllowed()) return emptyList()
        val toc = runCatchingCancellable { io { reading.contents(chapterId) } }.getOrDefault(emptyList())
        if (toc.isNotEmpty()) synchronized(tocCache) { tocCache[chapterId] = toc }
        return toc
    }

    override suspend fun markSeriesRead(seriesId: Int) = setStatus(listOf(seriesId), "READ")

    override suspend fun markSeriesUnread(seriesId: Int) = setStatus(listOf(seriesId), "UNREAD")

    override suspend fun markVolumeRead(
        seriesId: Int,
        volumeIds: List<Int>,
    ) = setStatus(volumeIds, "READ")

    override suspend fun markVolumeUnread(
        seriesId: Int,
        volumeIds: List<Int>,
    ) = setStatus(volumeIds, "UNREAD")

    override suspend fun uploadReadingSessions() {
        if (!isOnlineAllowed()) return
        runCatchingCancellable { ReadingSessionUploader(readerDao).upload { session -> io { server.sessions.record(session) }.let { true } } }
    }

    override suspend fun syncLocalProgress(): Boolean {
        if (!isOnlineAllowed()) return false
        uploadReadingSessions()
        var needsRetry = false
        for (entity in readerDao.getAllLocalProgress()) {
            if (!appPreferences.isActiveSession()) return true
            when (send(entity.toDomain())) {
                ProgressUploadResult.Sent -> readerDao.finishProgressSync(entity, replacement = null)
                ProgressUploadResult.RetryableFailure -> needsRetry = true
                // Progress the server will never take is dropped rather than retried for ever.
                ProgressUploadResult.Rejected -> readerDao.finishProgressSync(entity, replacement = null)
            }
        }
        return !needsRetry
    }

    override suspend fun getLatestLocalProgress(seriesId: Int): ReaderProgress? =
        runCatchingCancellable { readerDao.getAllLocalProgress() }
            .getOrDefault(emptyList())
            .filter { it.seriesId == seriesId }
            .maxByOrNull { it.lastModifiedUtc }
            ?.toDomain()

    override suspend fun getLatestLocalProgressForChapters(chapterIds: Set<Int>): ReaderProgress? {
        if (chapterIds.isEmpty()) return null
        return runCatchingCancellable { readerDao.getAllLocalProgress() }
            .getOrDefault(emptyList())
            .filter { it.chapterId in chapterIds }
            .maxByOrNull { it.lastModifiedUtc }
            ?.toDomain()
    }

    private suspend fun neighbour(
        bookId: Int,
        step: Int,
    ): ReaderChapterNav? =
        runCatchingCancellable { io { reading.neighbour(bookId, step) } }
            .getOrNull()
            ?.let { ReaderChapterNav(it, it, it) }

    private suspend fun setStatus(
        ids: List<Int>,
        status: String,
    ) {
        runCatchingCancellable { io { reading.setStatus(ids, status) } }
    }

    private suspend fun send(progress: ReaderProgress): ProgressUploadResult =
        try {
            io { reading.save(progress.chapterId, progress.page ?: 0, progress.bookScrollId) }
            ProgressUploadResult.Sent
        } catch (e: CancellationException) {
            throw e
        } catch (e: GrimmoryHttpException) {
            classifyProgressStatus(e.code)
        } catch (_: IOException) {
            ProgressUploadResult.RetryableFailure
        } catch (_: RuntimeException) {
            ProgressUploadResult.Rejected
        }

    private var sessionUploadScheduledAt = 0L

    /**
     * A session is only over once the reading has been quiet for a while, and the app may not be open by then, so a job is set to
     * send it just after. Reading on pushes the job back; it is asked for at most once a minute.
     */
    private fun scheduleSessionUpload() {
        val now = System.currentTimeMillis()
        if (now - sessionUploadScheduledAt < SESSION_UPLOAD_THROTTLE_MS) return
        sessionUploadScheduledAt = now
        ProgressSyncWorker.enqueueAfterSessionGap(context, appPreferences.profile)
    }

    /** Keeps what the reader did as a reading session, with the time it happened. */
    private suspend fun record(
        progress: ReaderProgress,
        totalPages: Int?,
    ) {
        scheduleSessionUpload()
        recorder.record(
            ReadingPoint(
                bookId = progress.chapterId,
                seriesId = progress.seriesId,
                page = progress.page,
                pages = totalPages,
                atMs = progress.lastModifiedUtcMillis,
            ),
        )
    }

    private fun ReaderProgress.stamped() = if (lastModifiedUtcMillis == 0L) copy(lastModifiedUtcMillis = System.currentTimeMillis()) else this

    /** Book info from what was downloaded, for when the server cannot be asked. */
    private suspend fun offlineInfo(bookId: Int): ReaderBookInfo? {
        val parts =
            downloadDao
                .getItemsForChapter(bookId)
                .filter { it.status == DownloadedItemV2Entity.STATUS_COMPLETED && it.page != null }
                .filter { it.localPath?.let { path -> storage.exists(path) } == true }
        if (parts.isEmpty()) return null
        // Without the server the title comes from the list already held, which asking for it would not get.
        val book = server.catalog.heldBook(bookId)
        return ReaderBookInfo(
            pages = (parts.maxOf { it.page ?: 0 }) + 1,
            seriesId = bookId,
            volumeId = bookId,
            libraryId = book?.libraryId,
            title = book?.title,
        )
    }

    private fun isOnlineAllowed(): Boolean =
        NetworkMonitor
            .getInstance(context, appPreferences)
            .status.value.isOnlineAllowed && !ServerReachability.isUnreachable()

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private companion object {
        const val SESSION_UPLOAD_THROTTLE_MS = 60_000L
    }

    class PageNotDownloadedException : IOException("Page not downloaded for offline reading")
}
