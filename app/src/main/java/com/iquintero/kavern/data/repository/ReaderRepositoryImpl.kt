package com.iquintero.kavern.data.repository

import android.content.Context
import com.iquintero.kavern.core.downloadv2.DownloadPaths
import com.iquintero.kavern.core.downloadv2.DownloadStorageManager
import com.iquintero.kavern.core.network.KavitaApiFactory
import com.iquintero.kavern.core.network.NetworkMonitor
import com.iquintero.kavern.core.network.ServerReachability
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.core.storage.KavitaProfile
import com.iquintero.kavern.core.sync.ProgressSyncWorker
import com.iquintero.kavern.core.util.runCatchingCancellable
import com.iquintero.kavern.data.api.KavitaApi
import com.iquintero.kavern.data.api.dto.BookChapterItemDto
import com.iquintero.kavern.data.local.db.dao.DownloadV2Dao
import com.iquintero.kavern.data.local.db.dao.ReaderDao
import com.iquintero.kavern.data.local.db.entity.CachedPageEntity
import com.iquintero.kavern.data.local.db.entity.DownloadJobV2Entity
import com.iquintero.kavern.data.local.db.entity.DownloadedItemV2Entity
import com.iquintero.kavern.data.local.db.entity.LocalReaderProgressEntity
import com.iquintero.kavern.data.mapper.toDomain
import com.iquintero.kavern.data.mapper.toDto
import com.iquintero.kavern.domain.model.Format
import com.iquintero.kavern.domain.model.ReaderBookInfo
import com.iquintero.kavern.domain.model.ReaderChapterNav
import com.iquintero.kavern.domain.model.ReaderImageResult
import com.iquintero.kavern.domain.model.ReaderPageResult
import com.iquintero.kavern.domain.model.ReaderProgress
import com.iquintero.kavern.domain.model.ReaderTimeLeft
import com.iquintero.kavern.domain.model.ReaderTocItem
import com.iquintero.kavern.domain.repository.ReaderRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.util.Locale
import java.util.zip.ZipFile

private const val HTTP_TOO_MANY_REQUESTS = 429

internal enum class ProgressUploadResult {
    Sent,
    RetryableFailure,
    Rejected,
}

/**
 * Maps a progress upload's HTTP status to what the sync queue should do with the row.
 * Authentication failures are retryable: the row is valid, the session just needs fixing.
 * Other 4xx answers will never succeed for this row, so it is dropped rather than retried forever.
 */
internal fun classifyProgressStatus(status: Int): ProgressUploadResult =
    when {
        status in 200..299 -> ProgressUploadResult.Sent
        status == HttpURLConnection.HTTP_CLIENT_TIMEOUT ||
            status == HTTP_TOO_MANY_REQUESTS ||
            status == HttpURLConnection.HTTP_UNAUTHORIZED ||
            status == HttpURLConnection.HTTP_FORBIDDEN ||
            status in 500..599 -> ProgressUploadResult.RetryableFailure
        else -> ProgressUploadResult.Rejected
    }

/** What a stored reading position needs once the server's own copy is known. */
internal enum class ProgressSyncPlan {
    /** The server is behind: this position has to be sent. */
    SendLocal,

    /** The server already has this position, so sending it again would only add an empty reading event. */
    AlreadyOnServer,

    /** The server has a different, more recent position (read elsewhere meanwhile), which is kept. */
    ServerIsNewer,
}

internal fun planProgressSync(
    local: ReaderProgress,
    remote: ReaderProgress?,
): ProgressSyncPlan =
    when {
        remote == null -> ProgressSyncPlan.SendLocal
        remote.page == local.page && (local.bookScrollId == null || remote.bookScrollId == local.bookScrollId) ->
            ProgressSyncPlan.AlreadyOnServer
        local.lastModifiedUtcMillis > remote.lastModifiedUtcMillis -> ProgressSyncPlan.SendLocal
        else -> ProgressSyncPlan.ServerIsNewer
    }

class ReaderRepositoryImpl(
    private val context: Context,
    private val appPreferences: AppPreferences,
    private val readerDao: ReaderDao,
    private val downloadV2Dao: DownloadV2Dao? = null,
) : ReaderRepository {
    private val archiveEntryCache = mutableMapOf<String, List<String>>()
    private val tocCache = mutableMapOf<Int, List<ReaderTocItem>>()
    private val downloadStorageManager = DownloadStorageManager(context, appPreferences)

    class PageNotDownloadedException(
        message: String,
    ) : IOException(message)

    override suspend fun getPageResult(
        chapterId: Int,
        page: Int,
    ): ReaderPageResult {
        val preferOffline = appPreferences.preferOfflinePagesFlow.first()
        val downloadedV2 = downloadV2Dao?.getDownloadedPageForChapter(chapterId, page)

        suspend fun downloadedPage(): ReaderPageResult? {
            val item = downloadedV2 ?: return null
            val path = item.localPath ?: return null
            val html = readDownloadedHtml(path) ?: return null
            val assets =
                try {
                    downloadStorageManager.epubAssetPaths(item, path)
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
            return downloadedPage()
                ?: cached?.let { ReaderPageResult(it, false) }
                ?: throw PageNotDownloadedException("Page not downloaded for offline reading")
        }

        if (preferOffline) downloadedPage()?.let { return it }

        val apiHtml =
            try {
                val api = apiOrThrow()
                val resp = api.getBookPage(chapterId, page)
                if (!resp.isSuccessful) {
                    throw Exception("Error loading page: HTTP ${resp.code()} ${resp.message()}")
                }
                val body = resp.body() ?: throw Exception("Empty page body")
                readerDao.upsertPage(
                    CachedPageEntity(
                        chapterId = chapterId,
                        page = page,
                        html = body,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                body
            } catch (io: IOException) {
                if (!preferOffline) downloadedPage()?.let { return it }
                if (cached != null) return ReaderPageResult(cached, false)
                if (!isOnlineAllowed()) throw PageNotDownloadedException("Page not downloaded for offline reading")
                throw io
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (!preferOffline) downloadedPage()?.let { return it }
                if (cached != null) return ReaderPageResult(cached, false)
                throw e
            }

        return ReaderPageResult(apiHtml, false)
    }

    override suspend fun getImageResult(
        chapterId: Int,
        page: Int,
    ): ReaderImageResult {
        val downloadedV2 = downloadV2Dao?.getDownloadedPageForChapter(chapterId, page)
        val downloadedPath =
            downloadedV2?.localPath?.let { path ->
                if (isPathPresent(path)) path else null
            }
        if (downloadedPath != null) {
            return ReaderImageResult(downloadedPath, true)
        }
        val archiveItem = downloadV2Dao?.getDownloadedFileForChapter(chapterId)
        val archivePath =
            archiveItem?.localPath?.let { path ->
                if (isPathPresent(path)) path else null
            }
        if (archivePath != null) {
            extractImageFromArchive(archivePath, chapterId, page)?.let { path ->
                return ReaderImageResult(path, true)
            }
        }
        if (!isOnlineAllowed()) {
            throw PageNotDownloadedException("Page not downloaded for offline reading")
        }
        val config = appPreferences.configFlow.first()
        if (!config.isConfigured) {
            throw IOException("Server or API key not configured")
        }
        return ReaderImageResult(config.comicPageUrl(chapterId, page), false)
    }

    override suspend fun getPage(
        chapterId: Int,
        page: Int,
    ): String = getPageResult(chapterId, page).html

    override suspend fun isPageDownloaded(
        chapterId: Int,
        page: Int,
    ): Boolean {
        val v2Item = downloadV2Dao?.getDownloadedPageForChapter(chapterId, page)
        if (v2Item != null) {
            val path = v2Item.localPath
            if (!path.isNullOrBlank()) {
                val present = isPathPresent(path)
                if (!present) return false
                return present
            }
        }
        val archiveItem = downloadV2Dao?.getDownloadedFileForChapter(chapterId)
        val archivePath =
            archiveItem?.localPath?.let { path ->
                if (isPathPresent(path)) path else null
            }
        if (archivePath != null) {
            val entryNames = listArchiveImageEntries(archivePath)
            return page in entryNames.indices
        }
        return false
    }

    override suspend fun getProgress(chapterId: Int): ReaderProgress? {
        val local = readerDao.getLocalProgress(chapterId)?.toDomain()
        if (!isOnlineAllowed()) return local

        val api =
            try {
                apiOrThrow()
            } catch (_: IOException) {
                return local
            }

        val remote =
            runCatchingCancellable {
                val resp = api.getReaderProgress(chapterId)
                if (resp.isSuccessful) resp.body()?.toDomain() else null
            }.getOrNull()

        val latest =
            when {
                remote == null -> {
                    local?.let { runCatchingCancellable { api.setReaderProgress(it.toDto()) } }
                    local
                }
                local == null -> remote
                local.lastModifiedUtcMillis > remote.lastModifiedUtcMillis -> {
                    // push local to server
                    runCatchingCancellable { api.setReaderProgress(local.toDto()) }
                    local
                }
                else -> remote
            }

        latest?.let { readerDao.upsertLocalProgress(it.toEntity()) }
        return latest
    }

    override suspend fun setProgress(
        progress: ReaderProgress,
        totalPages: Int?,
    ) {
        val progressWithTs =
            if (progress.lastModifiedUtcMillis == 0L) {
                progress.copy(lastModifiedUtcMillis = System.currentTimeMillis())
            } else {
                progress
            }

        if (!isOnlineAllowed()) {
            readerDao.upsertLocalProgress(progressWithTs.toEntity())
            ProgressSyncWorker.enqueue(context, appPreferences.profile)
            return
        }

        val result = sendProgress(progressWithTs)
        if (result == ProgressUploadResult.Sent) {
            // The server has it, so no copy is kept waiting; a newer one stored meanwhile stays.
            readerDao.getLocalProgress(progressWithTs.chapterId)?.let {
                if (it.lastModifiedUtc <= progressWithTs.lastModifiedUtcMillis) readerDao.clearLocalProgress(it.chapterId)
            }
        } else {
            readerDao.upsertLocalProgress(progressWithTs.toEntity())
        }
        if (result == ProgressUploadResult.RetryableFailure) {
            // The current worker may already have read its batch.
            ProgressSyncWorker.enqueue(context, appPreferences.profile, followUp = true)
        }

        maybeDeleteAfterCompletion(progressWithTs, totalPages)
        maybeDeleteAfterSlidingWindow(progressWithTs)
    }

    override suspend fun saveProgressLocally(progress: ReaderProgress) {
        val progressWithTs =
            if (progress.lastModifiedUtcMillis == 0L) {
                progress.copy(lastModifiedUtcMillis = System.currentTimeMillis())
            } else {
                progress
            }
        readerDao.upsertLocalProgress(progressWithTs.toEntity())
        ProgressSyncWorker.enqueue(context, appPreferences.profile)
    }

    override suspend fun getTimeLeft(
        seriesId: Int,
        chapterId: Int,
    ): ReaderTimeLeft? =
        runCatchingCancellable {
            val api = apiOrThrow()
            val resp = api.getTimeLeftForChapter(seriesId, chapterId)
            if (resp.isSuccessful) resp.body()?.toDomain() else null
        }.getOrNull()

    override suspend fun getPdfFile(chapterId: Int): File? {
        val config = appPreferences.configFlow.first()
        val existing =
            downloadV2Dao
                ?.getDownloadedFileForChapter(chapterId)
        if (existing != null && downloadStorageManager.exists(existing.localPath)) {
            val path = existing.localPath ?: return null
            return downloadStorageManager.fileForReading(path, "pdf-reader", "chapter-$chapterId.pdf")
        }
        val bookInfo = getBookInfo(chapterId)
        val storedPdf =
            bookInfo
                ?.seriesId
                ?.let { DownloadPaths.pdfFile(context, appPreferences.profile, it, bookInfo.volumeId, chapterId) }
        if (storedPdf?.exists() == true) {
            recordPdfDownloadIfMissing(chapterId, storedPdf)
            return storedPdf
        }
        if (appPreferences.profile == KavitaProfile.Legacy) {
            val legacyTarget = context.getExternalFilesDir("Kavern/downloads/pdfs")?.resolve("pdf-$chapterId.pdf")
            if (legacyTarget?.isFile == true) {
                recordPdfDownloadIfMissing(chapterId, legacyTarget)
                return legacyTarget
            }
        }
        val tempTarget = DownloadPaths.pdfTempFile(context, appPreferences.profile, chapterId)
        if (tempTarget.exists()) {
            return tempTarget
        }

        val api =
            try {
                apiOrThrow()
            } catch (_: IOException) {
                return null
            }
        val pdf = api.fetchPdfForReading(chapterId, config.apiKey, tempTarget)
        if (com.iquintero.kavern.core.logging.LoggingManager
                .isDebugEnabled()
        ) {
            com.iquintero.kavern.core.logging.LoggingManager.d(
                "PdfReader",
                "Saved temp PDF: ${tempTarget.absolutePath}",
            )
        }
        return pdf
    }

    private suspend fun recordPdfDownloadIfMissing(
        chapterId: Int,
        target: File,
    ) {
        val dao = downloadV2Dao ?: return
        val now = System.currentTimeMillis()
        val existingItems = dao.getItemsForChapter(chapterId)
        val fileItem =
            existingItems.firstOrNull { it.type == DownloadedItemV2Entity.TYPE_FILE }
        val bookInfo = getBookInfo(chapterId)
        val seriesId = fileItem?.seriesId ?: bookInfo?.seriesId
        val volumeId = fileItem?.volumeId ?: bookInfo?.volumeId
        val bytes = target.length()
        if (fileItem != null) {
            dao.upsertItems(
                listOf(
                    fileItem.copy(
                        seriesId = seriesId,
                        volumeId = volumeId,
                        localPath = target.absolutePath,
                        bytes = bytes,
                        status = DownloadedItemV2Entity.STATUS_COMPLETED,
                        updatedAt = now,
                        error = null,
                    ),
                ),
            )
            val jobId = fileItem.jobId
            dao.getJob(jobId)?.let { job ->
                dao.updateJob(
                    job.copy(
                        seriesId = seriesId ?: job.seriesId,
                        volumeId = volumeId ?: job.volumeId,
                        status = DownloadJobV2Entity.STATUS_COMPLETED,
                        completedItems = 1,
                        updatedAt = now,
                        error = null,
                    ),
                )
            }
            return
        }
        val job =
            DownloadJobV2Entity(
                type = DownloadJobV2Entity.TYPE_CHAPTER,
                format = com.iquintero.kavern.core.downloadv2.strategies.PdfDownloadStrategyV2.FORMAT_PDF,
                strategy = com.iquintero.kavern.core.downloadv2.strategies.PdfDownloadStrategyV2.FORMAT_PDF,
                seriesId = seriesId,
                volumeId = volumeId,
                chapterId = chapterId,
                status = DownloadJobV2Entity.STATUS_COMPLETED,
                totalItems = 1,
                completedItems = 1,
                priority = 0,
                createdAt = now,
                updatedAt = now,
                error = null,
            )
        val jobId = dao.insertJob(job)
        val item =
            DownloadedItemV2Entity(
                jobId = jobId,
                type = DownloadedItemV2Entity.TYPE_FILE,
                seriesId = seriesId,
                volumeId = volumeId,
                chapterId = chapterId,
                page = null,
                url = null,
                localPath = target.absolutePath,
                bytes = bytes,
                checksum = null,
                status = DownloadedItemV2Entity.STATUS_COMPLETED,
                createdAt = now,
                updatedAt = now,
                format = Format.Pdf,
                error = null,
            )
        dao.upsertItems(listOf(item))
    }

    override suspend fun getNextChapter(
        seriesId: Int,
        volumeId: Int,
        currentChapterId: Int,
    ): ReaderChapterNav? {
        return runCatchingCancellable {
            val api = apiOrThrow()
            val resp = api.getNextChapter(seriesId, volumeId, currentChapterId)
            if (!resp.isSuccessful) return@runCatchingCancellable null
            val id = resp.body() ?: return@runCatchingCancellable null
            ReaderChapterNav(seriesId = seriesId, volumeId = volumeId, chapterId = id)
        }.getOrNull()
    }

    override suspend fun getPreviousChapter(
        seriesId: Int,
        volumeId: Int,
        currentChapterId: Int,
    ): ReaderChapterNav? {
        return runCatchingCancellable {
            val api = apiOrThrow()
            val resp = api.getPreviousChapter(seriesId, volumeId, currentChapterId)
            if (!resp.isSuccessful) return@runCatchingCancellable null
            val id = resp.body() ?: return@runCatchingCancellable null
            ReaderChapterNav(seriesId = seriesId, volumeId = volumeId, chapterId = id)
        }.getOrNull()
    }

    override suspend fun getContinuePoint(seriesId: Int): ReaderChapterNav? =
        runCatchingCancellable {
            val api = apiOrThrow()
            val resp = api.getContinuePoint(seriesId)
            if (!resp.isSuccessful) return@runCatchingCancellable null
            val chapter = resp.body() ?: return@runCatchingCancellable null
            ReaderChapterNav(
                seriesId = seriesId,
                volumeId = chapter.volumeId,
                chapterId = chapter.id,
                pagesRead = chapter.pagesRead,
            )
        }.getOrNull()

    override suspend fun getBookInfo(chapterId: Int): ReaderBookInfo? =
        runCatchingCancellable {
            if (!isOnlineAllowed()) return@runCatchingCancellable getOfflineBookInfo(chapterId)
            val api = apiOrThrow()
            val resp = api.getBookInfo(chapterId)
            if (!resp.isSuccessful) return@runCatchingCancellable null
            val info = resp.body()?.toDomain() ?: return@runCatchingCancellable null
            if (!info.pageTitle.isNullOrBlank()) {
                info
            } else {
                info.copy(chapterNumber = resolveChapterNumber(api, info, chapterId))
            }
        }.getOrNull() ?: getOfflineBookInfo(chapterId)

    override suspend fun getLocalProgress(chapterId: Int): ReaderProgress? = readerDao.getLocalProgress(chapterId)?.toDomain()

    override suspend fun getCachedBookInfo(chapterId: Int): ReaderBookInfo? = getOfflineBookInfo(chapterId)

    override suspend fun getToc(chapterId: Int): List<ReaderTocItem> {
        synchronized(tocCache) { tocCache[chapterId] }?.let { return it }
        if (!isOnlineAllowed()) return emptyList()
        val toc =
            try {
                val resp = apiOrThrow().getBookChapters(chapterId)
                if (resp.isSuccessful) resp.body().orEmpty().flatMap { flattenTocItem(it, 0) } else emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
        if (toc.isNotEmpty()) synchronized(tocCache) { tocCache[chapterId] = toc }
        return toc
    }

    private fun flattenTocItem(
        item: BookChapterItemDto,
        depth: Int,
    ): List<ReaderTocItem> {
        val title = item.title?.trim().orEmpty()
        val self =
            if (title.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    ReaderTocItem(
                        title = title,
                        page = item.page ?: 0,
                        anchor =
                            item.part
                                ?.trim()
                                ?.removePrefix("#")
                                ?.takeIf { it.isNotEmpty() },
                        depth = depth,
                    ),
                )
            }
        return self + item.children.orEmpty().flatMap { flattenTocItem(it, if (self.isEmpty()) depth else depth + 1) }
    }

    private suspend fun resolveChapterNumber(
        api: com.iquintero.kavern.data.api.KavitaApi,
        info: ReaderBookInfo,
        chapterId: Int,
    ): String? {
        val volume =
            info.volumeId?.let { volumeId ->
                runCatchingCancellable { api.getVolumeById(volumeId) }
                    .getOrNull()
                    ?.takeIf { it.isSuccessful }
                    ?.body()
            }
        chapterPosition(volume?.chapters, chapterId)?.let { return it }

        val detail =
            info.seriesId?.let { seriesId ->
                runCatchingCancellable { api.getSeriesDetail(seriesId) }
                    .getOrNull()
                    ?.takeIf { it.isSuccessful }
                    ?.body()
            }
        chapterPosition(detail?.chapters, chapterId)?.let { return it }
        val matchingChapter =
            buildList {
                addAll(detail?.chapters.orEmpty())
                addAll(detail?.storylineChapters.orEmpty())
                addAll(detail?.specials.orEmpty())
                detail?.volumes.orEmpty().forEach { addAll(it.chapters.orEmpty()) }
            }.firstOrNull { it.id == chapterId }
        return matchingChapter?.number?.takeIf { it.isNotBlank() }
            ?: matchingChapter?.range?.takeIf { it.isNotBlank() }
    }

    private fun chapterPosition(
        chapters: List<com.iquintero.kavern.data.api.dto.ChapterDto>?,
        chapterId: Int,
    ): String? {
        val index = chapters?.indexOfFirst { it.id == chapterId } ?: -1
        return index.takeIf { it >= 0 }?.let { (it + 1).toString() }
    }

    override suspend fun markSeriesRead(seriesId: Int) {
        runCatchingCancellable {
            val api = apiOrThrow()
            api.markSeriesRead(
                com.iquintero.kavern.data.api.dto
                    .MarkSeriesDto(seriesId),
            )
        }
        runCatchingCancellable { handleDeleteAfterRead(seriesId = seriesId, volumeIds = null) }
    }

    override suspend fun markSeriesUnread(seriesId: Int) {
        runCatchingCancellable {
            val api = apiOrThrow()
            api.markSeriesUnread(
                com.iquintero.kavern.data.api.dto
                    .MarkSeriesDto(seriesId),
            )
        }
        runCatchingCancellable { clearDeleteAfterHistory(seriesId) }
    }

    override suspend fun markVolumeRead(
        seriesId: Int,
        volumeIds: List<Int>,
    ) {
        runCatchingCancellable {
            val api = apiOrThrow()
            api.markMultipleRead(
                com.iquintero.kavern.data.api.dto
                    .MarkMultipleDto(seriesId, volumeIds = volumeIds),
            )
        }
        runCatchingCancellable { handleDeleteAfterRead(seriesId = seriesId, volumeIds = volumeIds) }
    }

    override suspend fun markVolumeUnread(
        seriesId: Int,
        volumeIds: List<Int>,
    ) {
        runCatchingCancellable {
            val api = apiOrThrow()
            api.markMultipleUnread(
                com.iquintero.kavern.data.api.dto
                    .MarkMultipleDto(seriesId, volumeIds = volumeIds),
            )
        }
        // no-op for delete-after; sliding window is handled via progress
    }

    override suspend fun syncLocalProgress(): Boolean {
        if (!isOnlineAllowed()) return false
        val locals = readerDao.getAllLocalProgress()
        if (locals.isEmpty()) return true
        val api = apiOrThrow()
        var needsRetry = false
        for (localEntity in locals) {
            if (!appPreferences.isActiveSession()) return true
            // Once the server is found unreachable (e.g. a home server on mobile data) every further row would only
            // wait for the same timeout, so stop and leave them for the next attempt.
            if (ServerReachability.isUnreachable()) return false
            if (!syncStoredProgress(api, localEntity)) needsRetry = true
        }
        return !needsRetry
    }

    /** Brings the server up to date with one stored position. False means it must be tried again later. */
    private suspend fun syncStoredProgress(
        api: KavitaApi,
        localEntity: LocalReaderProgressEntity,
    ): Boolean {
        val local = localEntity.toDomain()
        val remote =
            try {
                val resp = api.getReaderProgress(local.chapterId)
                if (resp.isSuccessful) resp.body()?.toDomain() else null
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                // Without the server's copy there is no telling whether sending would overwrite newer reading.
                return false
            }
        val plan = planProgressSync(local, remote)
        if (com.iquintero.kavern.core.logging.LoggingManager
                .isDebugEnabled()
        ) {
            com.iquintero.kavern.core.logging.LoggingManager.d(
                "KavernProgress",
                "Sync chapter=${local.chapterId} page=${local.page} remotePage=${remote?.page} plan=$plan",
            )
        }
        if (plan != ProgressSyncPlan.SendLocal) {
            readerDao.finishProgressSync(localEntity, replacement = null)
            return true
        }
        return when (sendProgress(local, api)) {
            ProgressUploadResult.Sent -> {
                readerDao.finishProgressSync(localEntity, replacement = null)
                true
            }
            ProgressUploadResult.RetryableFailure -> false
            ProgressUploadResult.Rejected -> {
                com.iquintero.kavern.core.logging.LoggingManager.w(
                    "KavernProgress",
                    "Dropping progress the server rejected chapter=${local.chapterId} page=${local.page}",
                )
                readerDao.finishProgressSync(localEntity, replacement = null)
                true
            }
        }
    }

    override suspend fun getLatestLocalProgress(seriesId: Int): ReaderProgress? {
        val locals = runCatchingCancellable { readerDao.getAllLocalProgress() }.getOrDefault(emptyList())
        val latest =
            locals
                .filter { it.seriesId == seriesId }
                .maxByOrNull { it.lastModifiedUtc }
        return latest?.toDomain()
    }

    override suspend fun getLatestLocalProgressForChapters(chapterIds: Set<Int>): ReaderProgress? {
        if (chapterIds.isEmpty()) return null
        val locals = runCatchingCancellable { readerDao.getAllLocalProgress() }.getOrDefault(emptyList())
        val latest =
            locals
                .filter { it.chapterId in chapterIds }
                .maxByOrNull { it.lastModifiedUtc }
        return latest?.toDomain()
    }

    private suspend fun sendProgress(
        progress: ReaderProgress,
        api: KavitaApi? = null,
    ): ProgressUploadResult =
        try {
            val response = (api ?: apiOrThrow()).setReaderProgress(progress.toDto())
            response.errorBody()?.close()
            classifyProgressStatus(response.code())
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            ProgressUploadResult.RetryableFailure
        } catch (_: Exception) {
            ProgressUploadResult.Rejected
        }

    private suspend fun apiOrThrow(): KavitaApi {
        val config = appPreferences.configFlow.first()
        check(config.isConfigured) { "Not authenticated" }
        if (!isOnlineAllowed()) throw IOException("Offline")

        return KavitaApiFactory.createAuthenticated(config)
    }

    private suspend fun readDownloadedHtml(path: String): String? = downloadStorageManager.readText(path)

    private suspend fun isPathPresent(path: String): Boolean = downloadStorageManager.exists(path)

    private suspend fun extractImageFromArchive(
        archivePath: String,
        chapterId: Int,
        page: Int,
    ): String? {
        val entries = listArchiveImageEntries(archivePath)
        val entryName = entries.getOrNull(page) ?: return null
        val extension =
            entryName
                .substringAfterLast('.', "jpg")
                .lowercase(Locale.US)
                .ifBlank { "jpg" }
        val target =
            DownloadPaths.imagePageCacheFile(
                context = context,
                profile = appPreferences.profile,
                chapterId = chapterId,
                page = page,
                extension = extension,
            )
        if (target.exists()) {
            return target.absolutePath
        }
        target.parentFile?.mkdirs()
        val archiveFile = archiveFileForReading(archivePath, chapterId) ?: return null
        return runCatching {
            ZipFile(archiveFile).use { zip ->
                val entry = zip.getEntry(entryName) ?: return@runCatching null
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                target.absolutePath
            }
        }.getOrNull()
    }

    private suspend fun listArchiveImageEntries(path: String): List<String> {
        archiveEntryCache[path]?.let { return it }
        val archiveFile = archiveFileForReading(path, path.hashCode()) ?: return emptyList()
        val entries =
            runCatching {
                ZipFile(archiveFile).use { zip ->
                    zip.comicImageEntries().map { it.name }
                }
            }.getOrDefault(emptyList())
        archiveEntryCache[path] = entries
        return entries
    }

    private suspend fun archiveFileForReading(
        path: String,
        chapterId: Int,
    ): File? = downloadStorageManager.fileForReading(path, "archive-reader", "chapter-$chapterId.cbz")

    // "Online" also means the server answered recently: on mobile data a home server just hangs until it times out.
    private fun isOnlineAllowed(): Boolean =
        NetworkMonitor
            .getInstance(context, appPreferences)
            .status.value.isOnlineAllowed && !ServerReachability.isUnreachable()

    private suspend fun getOfflineBookInfo(chapterId: Int): ReaderBookInfo? {
        val items = downloadV2Dao?.getItemsForChapter(chapterId).orEmpty()
        if (items.isEmpty()) return null
        val completed =
            buildList {
                items
                    .filter { it.status == DownloadedItemV2Entity.STATUS_COMPLETED && it.page != null }
                    .forEach { item ->
                        val path = item.localPath
                        if (path != null && isPathPresent(path)) {
                            add(item)
                        }
                    }
            }
        val progress = readerDao.getLocalProgress(chapterId)
        if (completed.isEmpty()) {
            var fileItem: DownloadedItemV2Entity? = null
            for (item in items) {
                if (item.status == DownloadedItemV2Entity.STATUS_COMPLETED &&
                    item.type == DownloadedItemV2Entity.TYPE_FILE &&
                    item.localPath != null &&
                    isPathPresent(item.localPath)
                ) {
                    fileItem = item
                    break
                }
            }
            val archivePath = fileItem?.localPath
            if (archivePath != null) {
                val entries = listArchiveImageEntries(archivePath)
                if (entries.isNotEmpty()) {
                    val title =
                        runCatching {
                            context.getString(
                                com.iquintero.kavern.R.string.series_detail_chapter_fallback,
                                chapterId,
                            )
                        }.getOrNull()
                    return ReaderBookInfo(
                        pages = entries.size,
                        seriesId = progress?.seriesId ?: fileItem.seriesId,
                        volumeId = progress?.volumeId ?: fileItem.volumeId,
                        libraryId = progress?.libraryId,
                        title = null,
                        pageTitle = title,
                    )
                }
            }
            return null
        }
        val maxPage = completed.maxOf { it.page ?: 0 }
        val seriesId = progress?.seriesId ?: completed.firstOrNull()?.seriesId
        val volumeId = progress?.volumeId ?: completed.firstOrNull()?.volumeId
        val libraryId = progress?.libraryId
        val title =
            runCatching {
                context.getString(
                    com.iquintero.kavern.R.string.series_detail_chapter_fallback,
                    chapterId,
                )
            }.getOrNull()
        return ReaderBookInfo(
            pages = maxPage + 1,
            seriesId = seriesId,
            volumeId = volumeId,
            libraryId = libraryId,
            title = null,
            pageTitle = title,
        )
    }

    private fun LocalReaderProgressEntity.toDomain(): ReaderProgress =
        ReaderProgress(
            chapterId = chapterId,
            page = page,
            bookScrollId = bookScrollId,
            seriesId = seriesId,
            volumeId = volumeId,
            libraryId = libraryId,
            lastModifiedUtcMillis = lastModifiedUtc,
        )

    private fun ReaderProgress.toEntity(): LocalReaderProgressEntity =
        LocalReaderProgressEntity(
            chapterId = chapterId,
            page = page,
            bookScrollId = bookScrollId,
            seriesId = seriesId,
            volumeId = volumeId,
            libraryId = libraryId,
            lastModifiedUtc = lastModifiedUtcMillis,
        )

    /**
     * Apply delete-after-read preferences: keep at most `depth` recently marked read volumes.
     * When depth is 1, delete the current read volumes immediately.
     */
    private suspend fun handleDeleteAfterRead(
        seriesId: Int?,
        volumeIds: List<Int>?,
    ) {
        val dao = downloadV2Dao ?: return
        val enabled = appPreferences.deleteAfterMarkReadFlow.first()
        if (!enabled) return
        val depth = appPreferences.deleteAfterReadDepthFlow.first().coerceIn(1, 5)

        if (volumeIds.isNullOrEmpty()) {
            if (seriesId != null) {
                deleteDownloadedSeries(seriesId)
            }
            return
        }

        if (depth == 1) {
            volumeIds.forEach { id ->
                deleteDownloadedVolume(id)
            }
            return
        }

        // For volume-level completion, just delete the completed volumes when depth is 1.
        if (depth == 1) {
            volumeIds.forEach { id -> deleteDownloadedVolume(id) }
        }
    }

    private suspend fun clearDeleteAfterHistory(seriesId: Int?) {
        if (seriesId != null) {
            deleteDownloadedSeries(seriesId)
        }
    }

    private suspend fun deleteDownloadedItems(items: List<DownloadedItemV2Entity>) {
        if (items.isEmpty()) return
        items.forEach { item ->
            runCatchingCancellable {
                val path = item.localPath ?: return@runCatchingCancellable
                downloadStorageManager.delete(path)
            }
        }
        items.forEach { item ->
            runCatchingCancellable { downloadV2Dao?.deleteItemById(item.id) }
        }
    }

    private suspend fun deleteDownloadedSeries(seriesId: Int) {
        val dao = downloadV2Dao ?: return
        deleteDownloadedItems(dao.getItemsForSeries(seriesId))
        dao.deleteItemsForSeries(seriesId)
        dao.deleteJobsForSeries(seriesId)
    }

    private suspend fun deleteDownloadedVolume(volumeId: Int) {
        val dao = downloadV2Dao ?: return
        deleteDownloadedItems(dao.getItemsForVolume(volumeId))
        dao.deleteItemsForVolume(volumeId)
        dao.deleteJobsForVolume(volumeId)
    }

    private suspend fun maybeDeleteAfterCompletion(
        progress: ReaderProgress,
        totalPages: Int?,
    ) {
        val enabled = appPreferences.deleteAfterMarkReadFlow.first()
        if (!enabled) return
        val count = totalPages ?: return
        if (count <= 0) return
        if ((progress.page ?: 0) < count - 1) return
        val volumeId = progress.volumeId ?: return
        handleDeleteAfterRead(seriesId = progress.seriesId, volumeIds = listOf(volumeId))
    }

    private suspend fun maybeDeleteAfterSlidingWindow(progress: ReaderProgress) {
        val enabled = appPreferences.deleteAfterMarkReadFlow.first()
        if (!enabled) return
        val depth = appPreferences.deleteAfterReadDepthFlow.first().coerceIn(1, 5)
        val page = progress.page ?: return
        val cutoff = page - depth + 1
        if (cutoff < 0) return
        val dao = downloadV2Dao ?: return
        val items = dao.getItemsForChapterBeforePage(progress.chapterId, cutoff)
        deleteDownloadedItems(items)
    }
}
