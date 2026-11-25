package com.iquintero.kavern.ui.history

import com.iquintero.kavern.data.api.dto.ReadHistoryEventDto
import com.iquintero.kavern.data.api.dto.ReadingHistoryChapterDto
import com.iquintero.kavern.data.api.dto.ReadingHistorySessionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class HistoryModelTest {
    private val today = LocalDate.of(2026, 11, 25)
    private val zone = ZoneOffset.UTC

    private fun session(
        daysAgo: Int = 0,
        at: String = "20:00:00",
        seriesId: Int = 1,
        name: String = "Dracula",
        chapters: List<String> = listOf("Ch 1"),
        seconds: Int = 600,
        pages: Int = 3,
    ): ReadingHistorySessionDto {
        val date = today.minusDays(daysAgo.toLong())
        return ReadingHistorySessionDto(
            sessionId = 1,
            startTimeUtc = "${date}T$at",
            endTimeUtc = "${date}T$at",
            localDate = "${date}T00:00:00",
            seriesId = seriesId,
            seriesName = name,
            chapters = chapters.map { ReadingHistoryChapterDto(label = it) },
            pagesRead = pages,
            durationSeconds = seconds,
        )
    }

    private fun build(
        vararg sessions: ReadingHistorySessionDto,
        period: HistoryPeriod = HistoryPeriod.All,
        legacy: List<ReadHistoryEventDto> = emptyList(),
    ) = buildHistory(sessions.toList(), legacy, period, today, zone)

    @Test
    fun `sessions that read nothing are left out`() {
        val view =
            build(
                session(seconds = 0, pages = 0),
                session(seriesId = 2, name = "Emma", seconds = 0, pages = 0),
            )
        assertEquals(emptyList<HistoryDay>(), view.days)
        assertEquals(0, view.summary.books)
    }

    @Test
    fun `a session with time but no pages is kept, since reading inside a chapter turns no pages`() {
        val entry =
            build(session(seconds = 900, pages = 0))
                .days
                .single()
                .entries
                .single()
        assertEquals(900, entry.seconds)
        assertEquals(0, entry.pages)
    }

    @Test
    fun `pages read below zero count as none`() {
        val entry =
            build(session(seconds = 300, pages = -12))
                .days
                .single()
                .entries
                .single()
        assertEquals(0, entry.pages)
    }

    @Test
    fun `a books sessions on one day become one row`() {
        val view =
            build(
                session(at = "09:00:00", chapters = listOf("Ch 1", "Ch 2"), seconds = 600, pages = 2),
                session(at = "21:30:00", chapters = listOf("Ch 2", "Ch 3"), seconds = 1_200, pages = 4),
                session(at = "12:00:00", seriesId = 2, name = "Emma", seconds = 60, pages = 1),
            )
        val entries = view.days.single().entries
        assertEquals(listOf("Dracula", "Emma").sorted(), entries.map { it.seriesName }.sorted())
        val dracula = entries.single { it.seriesId == 1 }
        assertEquals(1_800, dracula.seconds)
        assertEquals(6, dracula.pages)
        assertEquals("Ch 1", dracula.firstChapter)
        assertEquals("Ch 3", dracula.lastChapter)
        assertEquals(3, dracula.chapterCount)
        assertEquals(LocalTime.of(21, 30), dracula.lastReadAt)
        assertEquals("the most recently read book comes first", 1, entries.first().seriesId)
    }

    @Test
    fun `days come newest first`() {
        val view = build(session(daysAgo = 3), session(daysAgo = 0), session(daysAgo = 1))
        assertEquals(listOf(today, today.minusDays(1), today.minusDays(3)), view.days.map { it.date })
    }

    @Test
    fun `the period leaves out older days but the streak still looks further back`() {
        val view = build(session(daysAgo = 0), session(daysAgo = 1), session(daysAgo = 2), session(daysAgo = 20), period = HistoryPeriod.Week)
        assertEquals(3, view.days.size)
        assertEquals(3, view.summary.activeDays)
        val month = build(session(daysAgo = 0), session(daysAgo = 20), period = HistoryPeriod.Week)
        assertEquals(1, month.days.size)
    }

    @Test
    fun `the summary adds up the period`() {
        val view =
            build(
                session(seconds = 600, pages = 3),
                session(daysAgo = 1, seriesId = 2, name = "Emma", seconds = 1_500, pages = 10),
                session(daysAgo = 1, seconds = 0, pages = 0),
            )
        assertEquals(2_100, view.summary.seconds)
        assertEquals(13, view.summary.pages)
        assertEquals(2, view.summary.books)
        assertEquals(2, view.summary.activeDays)
    }

    @Test
    fun `a streak counts back from today and survives a day not read yet`() {
        assertEquals(3, build(session(daysAgo = 0), session(daysAgo = 1), session(daysAgo = 2), session(daysAgo = 4)).summary.streak)
        assertEquals("nothing read yet today", 2, build(session(daysAgo = 1), session(daysAgo = 2)).summary.streak)
        assertEquals("a missed day ends it", 0, build(session(daysAgo = 2)).summary.streak)
    }

    @Test
    fun `the bars cover the last seven or thirty days with empty days at zero`() {
        val week = build(session(daysAgo = 0, seconds = 600), session(daysAgo = 2, seconds = 20), period = HistoryPeriod.Week).bars
        assertEquals(7, week.size)
        assertEquals(today, week.last().date)
        assertEquals(10, week.last().minutes)
        assertEquals("twenty seconds still shows", 1, week[4].minutes)
        assertEquals(0, week[5].minutes)
        assertEquals(30, build(session(), period = HistoryPeriod.Month).bars.size)
    }

    @Test
    fun `older servers give days and chapters without time or pages`() {
        val events =
            listOf(
                ReadHistoryEventDto(seriesId = 1, seriesName = "Dracula", readDate = "${today}T10:00:00", chapterNumber = 1f),
                ReadHistoryEventDto(seriesId = 1, seriesName = "Dracula", readDate = "${today}T11:00:00", chapterNumber = 2f),
            )
        val view = build(legacy = events)
        val entry =
            view.days
                .single()
                .entries
                .single()
        assertEquals(2, entry.chapterCount)
        assertEquals("1", entry.firstChapter)
        assertEquals("2", entry.lastChapter)
        assertNull(entry.seconds)
        assertNull(view.summary.seconds)
        assertEquals(1, view.summary.books)
    }
}
