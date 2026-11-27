package com.iquintero.kavern.core.sessions

import com.iquintero.kavern.data.local.db.dao.ReaderDao
import com.iquintero.kavern.data.local.db.entity.ReadingSessionEntity

/** Reading that stops for this long ends a session; the next page turn starts another. */
internal const val SESSION_GAP_MS = 10 * 60_000L

/** A session this short that went nowhere is somebody opening a book, not reading it. */
private const val MIN_SESSION_MS = 5_000L

private const val MS_PER_SECOND = 1000L
private const val PERCENT = 100f

/** Where the reader was, in whatever the book's pages are, at a moment in time. */
internal data class ReadingPoint(
    val bookId: Int,
    val seriesId: Int?,
    val page: Int?,
    val pages: Int?,
    val atMs: Long,
)

/** The session [point] belongs to: the book's open one when this comes soon enough after it, otherwise a new one. */
internal fun sessionAfter(
    open: ReadingSessionEntity?,
    point: ReadingPoint,
): ReadingSessionEntity =
    if (open != null && point.atMs - open.endMs in 0..SESSION_GAP_MS) {
        open.copy(
            endMs = point.atMs,
            endPage = point.page ?: open.endPage,
            pages = point.pages ?: open.pages,
            seriesId = point.seriesId ?: open.seriesId,
        )
    } else {
        ReadingSessionEntity(
            bookId = point.bookId,
            seriesId = point.seriesId,
            startMs = point.atMs,
            endMs = point.atMs,
            startPage = point.page,
            endPage = point.page,
            pages = point.pages,
        )
    }

internal val ReadingSessionEntity.durationSeconds: Int get() = ((endMs - startMs) / MS_PER_SECOND).toInt()

/** Whether anything happened in the session worth telling the server about. */
internal val ReadingSessionEntity.worthKeeping: Boolean
    get() = endMs - startMs >= MIN_SESSION_MS || startPage != endPage

/** How far into the book the page is, 0 to 100, or null when the book's length is not known. */
internal fun percentAt(
    page: Int?,
    pages: Int?,
): Float? {
    if (page == null || pages == null || pages <= 0) return null
    return (page * PERCENT / pages).coerceIn(0f, PERCENT)
}

/** Keeps the reader's progress as sessions on the device. */
internal class ReadingSessionRecorder(
    private val dao: ReaderDao,
) {
    suspend fun record(point: ReadingPoint) {
        val open = dao.latestOpenReadingSession(point.bookId)
        val session = sessionAfter(open, point)
        if (open != null && session.id == open.id) dao.updateReadingSession(session) else dao.insertReadingSession(session)
    }
}
