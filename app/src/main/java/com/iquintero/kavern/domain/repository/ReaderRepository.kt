package com.iquintero.kavern.domain.repository

import com.iquintero.kavern.domain.model.ReaderBookInfo
import com.iquintero.kavern.domain.model.ReaderChapterNav
import com.iquintero.kavern.domain.model.ReaderImageResult
import com.iquintero.kavern.domain.model.ReaderPageResult
import com.iquintero.kavern.domain.model.ReaderProgress
import com.iquintero.kavern.domain.model.ReaderTimeLeft
import com.iquintero.kavern.domain.model.ReaderTocItem

interface ReaderRepository {
    suspend fun getPageResult(
        chapterId: Int,
        page: Int,
    ): ReaderPageResult

    suspend fun getImageResult(
        chapterId: Int,
        page: Int,
    ): ReaderImageResult

    suspend fun isPageDownloaded(
        chapterId: Int,
        page: Int,
    ): Boolean

    suspend fun getPage(
        chapterId: Int,
        page: Int,
    ): String = getPageResult(chapterId, page).html

    suspend fun getProgress(chapterId: Int): ReaderProgress?

    suspend fun setProgress(
        progress: ReaderProgress,
        totalPages: Int? = null,
    )

    suspend fun saveProgressLocally(progress: ReaderProgress)

    suspend fun getTimeLeft(
        seriesId: Int,
        chapterId: Int,
    ): ReaderTimeLeft?

    suspend fun getPdfFile(chapterId: Int): java.io.File?

    suspend fun getNextChapter(
        seriesId: Int,
        volumeId: Int,
        currentChapterId: Int,
    ): ReaderChapterNav?

    suspend fun getPreviousChapter(
        seriesId: Int,
        volumeId: Int,
        currentChapterId: Int,
    ): ReaderChapterNav?

    suspend fun getContinuePoint(seriesId: Int): ReaderChapterNav?

    suspend fun getBookInfo(chapterId: Int): ReaderBookInfo?

    /** Reading progress stored on this device only; never touches the network. */
    suspend fun getLocalProgress(chapterId: Int): ReaderProgress? = null

    /** Book info reconstructed from downloaded files; never touches the network. */
    suspend fun getCachedBookInfo(chapterId: Int): ReaderBookInfo? = null

    /** Table of contents of an EPUB, empty when unavailable (e.g. offline). */
    suspend fun getToc(chapterId: Int): List<ReaderTocItem> = emptyList()

    suspend fun markSeriesRead(seriesId: Int)

    suspend fun markSeriesUnread(seriesId: Int)

    suspend fun markVolumeRead(
        seriesId: Int,
        volumeIds: List<Int>,
    )

    suspend fun markVolumeUnread(
        seriesId: Int,
        volumeIds: List<Int>,
    )

    /** Sends the reading sessions that have finished to servers that keep them as the app measured them. */
    suspend fun uploadReadingSessions()

    /** Returns true when this sync attempt can finish without a retry. */
    suspend fun syncLocalProgress(): Boolean

    suspend fun getLatestLocalProgress(seriesId: Int): ReaderProgress?

    suspend fun getLatestLocalProgressForChapters(chapterIds: Set<Int>): ReaderProgress?
}
