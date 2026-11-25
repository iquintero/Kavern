package com.iquintero.kavern.ui.history

import com.iquintero.kavern.data.api.dto.ReadHistoryEventDto
import com.iquintero.kavern.data.api.dto.ReadingHistorySessionDto
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** How far back the History page looks. */
internal enum class HistoryPeriod(
    /** Days counted back from today, today included; null for everything that has been loaded. */
    val days: Int?,
) {
    Week(7),
    Month(30),
    All(null),
}

/** What one book got on one day: its sessions folded into a single row. */
internal data class HistoryEntry(
    val seriesId: Int?,
    val seriesName: String,
    /** First and last chapter read that day, in reading order. Null when the server gave no labels. */
    val firstChapter: String?,
    val lastChapter: String?,
    val chapterCount: Int,
    /** Null on servers that do not report reading time. */
    val seconds: Int?,
    /** Null on servers that do not report pages. */
    val pages: Int?,
    /** When the book was last read that day, in the device's time zone. */
    val lastReadAt: LocalTime?,
)

internal data class HistoryDay(
    val date: LocalDate?,
    val entries: List<HistoryEntry>,
) {
    val seconds: Int? get() = entries.mapNotNull { it.seconds }.takeIf { it.isNotEmpty() }?.sum()
}

internal data class HistorySummary(
    val seconds: Int?,
    val pages: Int?,
    val books: Int,
    val activeDays: Int,
    /** Consecutive days of reading up to today, or up to yesterday while today has none yet. */
    val streak: Int,
)

/** Minutes read on one day of the bar chart. */
internal data class HistoryBar(
    val date: LocalDate,
    val minutes: Int,
)

internal data class HistoryView(
    val days: List<HistoryDay>,
    val summary: HistorySummary,
    val bars: List<HistoryBar>,
)

private const val SECONDS_PER_MINUTE = 60
private const val DATE_LENGTH = 10
private const val WEEK_DAYS = 7
private const val MONTH_DAYS = 30

/** One session as the page needs it: the day it counts for, and what it got done. */
private data class Reading(
    val date: LocalDate?,
    val seriesId: Int?,
    val seriesName: String,
    val chapterLabels: List<String>,
    val seconds: Int?,
    val pages: Int?,
    val endedAt: Instant?,
)

/**
 * Turns the server's reading sessions into the History page: days, one row per book per day, a summary and a bar
 * for each recent day.
 *
 * Every progress update used to start a session, so there are many that read nothing at all (a book opened and left,
 * a chapter flipped past). Those are left out; what is left of a day is the reading that happened. Pages read can be
 * negative when someone goes back to an earlier chapter, and then count as none.
 */
internal fun buildHistory(
    sessions: List<ReadingHistorySessionDto>,
    legacyEvents: List<ReadHistoryEventDto>,
    period: HistoryPeriod,
    today: LocalDate,
    zone: ZoneId,
): HistoryView {
    val readings = sessions.map { it.toReading(zone) } + legacyEvents.map { it.toReading(zone) }
    val earliest = period.days?.let { today.minusDays(it - 1L) }
    val inPeriod =
        readings.filter { reading ->
            val date = reading.date
            earliest == null || (date != null && !date.isBefore(earliest) && !date.isAfter(today))
        }
    val days =
        inPeriod
            .filter { it.didAnything() }
            .groupBy { it.date }
            .map { (date, group) -> HistoryDay(date, group.toEntries(zone)) }
            .sortedWith(compareByDescending<HistoryDay> { it.date != null }.thenByDescending { it.date })
    return HistoryView(
        days = days,
        summary = summarise(inPeriod, readings, today),
        bars = barsFor(readings, today, if (period == HistoryPeriod.Week) WEEK_DAYS else MONTH_DAYS),
    )
}

private fun Reading.didAnything(): Boolean = seconds == null || seconds > 0 || (pages ?: 0) > 0

private fun List<Reading>.toEntries(zone: ZoneId): List<HistoryEntry> =
    groupBy { it.seriesId ?: it.seriesName }
        .values
        .map { group ->
            // Oldest first, so the chapters come out in the order they were read.
            val ordered = group.sortedBy { it.endedAt ?: Instant.EPOCH }
            val labels = ordered.flatMap { it.chapterLabels }.distinct()
            val seconds = group.mapNotNull { it.seconds }.takeIf { it.isNotEmpty() }?.sum()
            val pages = group.mapNotNull { it.pages }.takeIf { it.isNotEmpty() }?.sum()
            HistoryEntry(
                seriesId = group.firstNotNullOfOrNull { it.seriesId },
                seriesName = group.first().seriesName,
                firstChapter = labels.firstOrNull(),
                lastChapter = labels.lastOrNull(),
                chapterCount = labels.size,
                seconds = seconds,
                pages = pages,
                lastReadAt =
                    ordered
                        .lastOrNull()
                        ?.endedAt
                        ?.atZone(zone)
                        ?.toLocalTime(),
            )
        }.sortedByDescending { it.lastReadAt ?: LocalTime.MIN }

private fun summarise(
    inPeriod: List<Reading>,
    all: List<Reading>,
    today: LocalDate,
): HistorySummary {
    val active = inPeriod.filter { it.didAnything() }
    return HistorySummary(
        seconds = active.mapNotNull { it.seconds }.takeIf { it.isNotEmpty() }?.sum(),
        pages = active.mapNotNull { it.pages }.takeIf { it.isNotEmpty() }?.sum(),
        books = active.map { it.seriesId ?: it.seriesName }.distinct().size,
        activeDays = active.mapNotNull { it.date }.distinct().size,
        streak = streak(all.filter { it.didAnything() }.mapNotNull { it.date }.toSet(), today),
    )
}

private fun streak(
    days: Set<LocalDate>,
    today: LocalDate,
): Int {
    // Having not read yet today does not end a streak that was alive yesterday.
    var day = if (today in days) today else today.minusDays(1)
    var count = 0
    while (day in days) {
        count++
        day = day.minusDays(1)
    }
    return count
}

private fun barsFor(
    readings: List<Reading>,
    today: LocalDate,
    dayCount: Int,
): List<HistoryBar> {
    val secondsByDay =
        readings
            .filter { it.date != null }
            .groupBy { it.date }
            .mapValues { (_, group) -> group.sumOf { it.seconds ?: 0 } }
    return (dayCount - 1 downTo 0).map { back ->
        val date = today.minusDays(back.toLong())
        // Part of a minute still shows as a minute, so a short evening is not an empty bar.
        val seconds = secondsByDay[date] ?: 0
        HistoryBar(date, if (seconds == 0) 0 else maxOf(1, (seconds + SECONDS_PER_MINUTE / 2) / SECONDS_PER_MINUTE))
    }
}

private fun ReadingHistorySessionDto.toReading(zone: ZoneId): Reading {
    val ended = parseInstant(endTimeUtc) ?: parseInstant(startTimeUtc)
    return Reading(
        date = localDate?.take(DATE_LENGTH)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: ended?.atZone(zone)?.toLocalDate(),
        seriesId = seriesId,
        seriesName = seriesName.orEmpty(),
        chapterLabels = chapters.orEmpty().mapNotNull { it.label?.takeIf { label -> label.isNotBlank() } },
        seconds = (durationSeconds ?: 0).coerceAtLeast(0),
        pages = (pagesRead ?: 0).coerceAtLeast(0),
        endedAt = ended,
    )
}

/** Servers before 0.8.9 only say that a chapter was read, and when. */
private fun ReadHistoryEventDto.toReading(zone: ZoneId): Reading {
    val raw = readDate ?: readDateUtc
    val instant = parseInstant(readDateUtc)
    return Reading(
        date = raw?.take(DATE_LENGTH)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: instant?.atZone(zone)?.toLocalDate(),
        seriesId = seriesId,
        seriesName = seriesName.orEmpty(),
        chapterLabels = listOfNotNull(chapterNumber?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }),
        seconds = null,
        pages = null,
        endedAt = instant,
    )
}

private fun parseInstant(value: String?): Instant? =
    value?.let {
        runCatching {
            if (it.endsWith("Z")) Instant.parse(it) else LocalDateTime.parse(it).toInstant(ZoneOffset.UTC)
        }.getOrNull()
    }
