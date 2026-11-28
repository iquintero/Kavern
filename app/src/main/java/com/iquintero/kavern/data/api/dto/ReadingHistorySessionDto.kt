package com.iquintero.kavern.data.api.dto

/** One reading session from `GET api/Stats/reading-history` (Kavita 0.8.9 and newer). */
data class ReadingHistorySessionDto(
    val sessionId: Int? = null,
    val startTimeUtc: String? = null,
    val endTimeUtc: String? = null,
    /** The session's day in the requested time zone, e.g. `2026-11-20T00:00:00`. */
    val localDate: String? = null,
    val seriesId: Int? = null,
    val seriesName: String? = null,
    val chapters: List<ReadingHistoryChapterDto>? = null,
    val pagesRead: Int? = null,
    /** Servers that measure reading as a share of the book rather than in pages report this instead of [pagesRead]. */
    val percentRead: Int? = null,
    val durationSeconds: Int? = null,
)

data class ReadingHistoryChapterDto(
    val chapterId: Int? = null,
    val label: String? = null,
)
