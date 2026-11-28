package com.iquintero.kavern.core.network.grimmory

import com.iquintero.kavern.data.api.dto.RecordedReadingSessionDto
import java.time.Instant

/** One stretch of reading as Grimmory keeps it. */
internal data class GrimmorySession(
    val id: Int,
    val bookId: Int,
    val title: String,
    val start: Instant,
    val end: Instant?,
    val durationSeconds: Int,
    /** How far into the book the session began and ended, 0 to 100, when the server was told. */
    val startPercent: Double?,
    val endPercent: Double?,
) {
    /** How much of the book the session moved forward. */
    val percentRead: Int get() = Math.round(((endPercent ?: 0.0) - (startPercent ?: 0.0)).coerceAtLeast(0.0)).toInt()
}

/**
 * Reading sessions, which Grimmory keeps as the client tells it: with the start and end the reading really had, so that
 * reading done offline is recorded on the day it happened.
 */
internal class GrimmorySessions(
    private val client: GrimmoryClient,
    private val catalog: GrimmoryCatalog,
) {
    /** Tells the server about one finished session, unless it already has it. */
    fun record(session: RecordedReadingSessionDto) {
        if (alreadyKnown(session.bookId, session.startTimeUtc)) return
        val from = session.startPercent
        val to = session.endPercent
        client.postJson(
            "api/v1/reading-sessions",
            buildMap {
                put("bookId", session.bookId)
                put("bookType", catalog.book(session.bookId)?.fileType?.uppercase() ?: "EPUB")
                put("startTime", session.startTimeUtc)
                put("endTime", session.endTimeUtc)
                put("durationSeconds", session.durationSeconds)
                from?.let { put("startProgress", it) }
                to?.let { put("endProgress", it) }
                if (from != null && to != null) put("progressDelta", to - from)
            },
        )
    }

    /** The sessions of every book that has been read, newest first. */
    fun history(): List<GrimmorySession> =
        catalog
            .books()
            .filter { it.lastReadTime != null }
            .flatMap { book -> forBook(book.id).mapNotNull { session -> sessionOf(session, book) } }
            .sortedByDescending { it.start }

    /** Whether the server already has the session that began at [start]: it keeps every post, so a repeat would be a second one. */
    private fun alreadyKnown(
        bookId: Int,
        start: String,
    ): Boolean {
        val begun = runCatching { Instant.parse(start) }.getOrNull() ?: return false
        return forBook(bookId).any { session -> session.str("startTime")?.let { runCatching { Instant.parse(it) }.getOrNull() } == begun }
    }

    private fun forBook(bookId: Int): List<Json> =
        client
            .getObject("api/v1/reading-sessions/book/$bookId", mapOf("page" to "0", "size" to SESSIONS_PER_BOOK.toString()))
            .objects("content")

    private fun sessionOf(
        session: Json,
        book: GrimmoryBook,
    ): GrimmorySession? {
        val start = session.str("startTime")?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        return GrimmorySession(
            id = session.int("id") ?: return null,
            bookId = book.id,
            title = book.title,
            start = start,
            end = session.str("endTime")?.let { runCatching { Instant.parse(it) }.getOrNull() },
            durationSeconds = session.int("durationSeconds") ?: 0,
            startPercent = session.num("startProgress"),
            endPercent = session.num("endProgress"),
        )
    }

    private companion object {
        const val SESSIONS_PER_BOOK = 100
    }
}
