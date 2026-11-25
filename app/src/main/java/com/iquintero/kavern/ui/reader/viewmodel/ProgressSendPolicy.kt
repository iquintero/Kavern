package com.iquintero.kavern.ui.reader.viewmodel

/** Where a reader is: the chapter, its page and, in books, the anchor inside that page. */
internal data class ReadPosition(
    val chapterId: Int,
    val page: Int,
    /** Null when the position is only known to the page, e.g. right after turning to it. */
    val scrollId: String? = null,
)

/** What to do with a new reading position, given the last one the server was told about. */
internal sealed interface ProgressDecision {
    /** Nothing new: the position tells the server nothing it does not already know. */
    data object Ignore : ProgressDecision

    /** The reader went back to what the server has, so a send still waiting is no longer needed. */
    data object DropPending : ProgressDecision

    data object SendNow : ProgressDecision

    data class SendAfter(
        val delayMs: Long,
    ) : ProgressDecision
}

/**
 * Decides when reading progress is worth a request. Every request is a reading event on the server, so opening a book
 * for a second, or turning pages within a chapter, should not each make one.
 *
 * - Opening a book, or any position equal to the one the server has, sends nothing.
 * - A new chapter is sent at once, and so is a new page when pages are chapter-sized (books).
 * - Everything else (a page of a comic or PDF, a spot inside a book page) waits until reading pauses for [QUIET_MS],
 *   but never longer than [MAX_WAIT_MS] since the last send, so a long reading stretch still shows as one session.
 *
 * Whatever is waiting when the reader is left is saved by the caller; this class only makes the timing decision.
 */
internal object ProgressSendPolicy {
    const val QUIET_MS = 20_000L
    const val MAX_WAIT_MS = 120_000L

    fun decide(
        sent: ReadPosition?,
        next: ReadPosition,
        sinceLastSendMs: Long,
        pageTurnsAreChapters: Boolean,
    ): ProgressDecision {
        if (sent == null) return ProgressDecision.SendNow
        if (next.chapterId != sent.chapterId) return ProgressDecision.SendNow
        if (next.page != sent.page) {
            return if (pageTurnsAreChapters) ProgressDecision.SendNow else wait(sinceLastSendMs)
        }
        return when (next.scrollId) {
            // Comics and PDFs have no anchor, so the same page again means the reader came back to the sent one.
            null -> if (sent.scrollId == null) ProgressDecision.DropPending else ProgressDecision.Ignore
            sent.scrollId -> ProgressDecision.DropPending
            else -> wait(sinceLastSendMs)
        }
    }

    private fun wait(sinceLastSendMs: Long): ProgressDecision.SendAfter = ProgressDecision.SendAfter(minOf(QUIET_MS, (MAX_WAIT_MS - sinceLastSendMs).coerceAtLeast(0L)))
}
