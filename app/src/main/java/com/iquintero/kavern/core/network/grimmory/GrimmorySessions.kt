package com.iquintero.kavern.core.network.grimmory

import com.iquintero.kavern.data.api.dto.ReadingHistoryChapterDto
import com.iquintero.kavern.data.api.dto.ReadingHistorySessionDto
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId

/**
 * Reading sessions, which Grimmory keeps as the client tells it: with the start and end the reading really had, so that
 * reading done offline is recorded on the day it happened.
 */
internal class GrimmorySessions(
    private val client: GrimmoryClient,
    private val catalog: GrimmoryCatalog,
) {
    /** Tells the server about one finished session of [body], which the app sends in its own shape. */
    fun record(body: Json) {
        val bookId = body.int("bookId") ?: return
        val start = body.str("startTimeUtc") ?: return
        val end = body.str("endTimeUtc") ?: return
        val from = body.num("startPercent")
        val to = body.num("endPercent")
        if (alreadyKnown(bookId, start)) return
        client.postJson(
            "api/v1/reading-sessions",
            buildMap {
                put("bookId", bookId)
                put("bookType", catalog.book(bookId)?.fileType?.uppercase() ?: "EPUB")
                put("startTime", start)
                put("endTime", end)
                put("durationSeconds", body.int("durationSeconds") ?: 0)
                from?.let { put("startProgress", it) }
                to?.let { put("endProgress", it) }
                if (from != null && to != null) put("progressDelta", to - from)
            },
        )
    }

    /** Whether the server already has the session that began at [start]: it keeps every post, so a repeat would be a second one. */
    private fun alreadyKnown(
        bookId: Int,
        start: String,
    ): Boolean {
        val begun = runCatching { Instant.parse(start) }.getOrNull() ?: return false
        return client
            .getObject("api/v1/reading-sessions/book/$bookId", mapOf("page" to "0", "size" to SESSIONS_PER_BOOK.toString()))
            .objects("content")
            .any { session -> session.str("startTime")?.let { runCatching { Instant.parse(it) }.getOrNull() } == begun }
    }

    /** The sessions of every book that has been read, newest first, as Kavita lists them. */
    fun history(zone: String?): List<ReadingHistorySessionDto> {
        val timeZone = zone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
        return catalog
            .books()
            .filter { it.lastReadTime != null }
            .flatMap { book ->
                client
                    .getObject("api/v1/reading-sessions/book/${book.id}", mapOf("page" to "0", "size" to SESSIONS_PER_BOOK.toString()))
                    .objects("content")
                    .mapNotNull { session -> historyOf(session, book, timeZone) }
            }.sortedByDescending { it.startTimeUtc }
    }

    private fun historyOf(
        session: Json,
        book: GrimmoryBook,
        zone: ZoneId,
    ): ReadingHistorySessionDto? {
        val start = session.str("startTime") ?: return null
        val day =
            try {
                Instant.parse(start).atZone(zone).toLocalDate()
            } catch (_: DateTimeException) {
                return null
            }
        val from = session.num("startProgress")
        val to = session.num("endProgress")
        val moved = (session.num("progressDelta") ?: 0.0).coerceAtLeast(0.0)
        return ReadingHistorySessionDto(
            sessionId = session.int("id"),
            startTimeUtc = start,
            endTimeUtc = session.str("endTime"),
            localDate = "${day}T00:00:00",
            seriesId = book.id,
            seriesName = book.title,
            chapters = if (from != null && to != null) listOf(ReadingHistoryChapterDto(label = "${from.toInt()}% – ${to.toInt()}%")) else null,
            percentRead = Math.round(moved).toInt(),
            durationSeconds = session.int("durationSeconds"),
        )
    }

    private companion object {
        const val SESSIONS_PER_BOOK = 100
    }
}
