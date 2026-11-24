package com.iquintero.kavern.ui.seriesdetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesDetailHeaderTest {
    @Test
    fun kavitasPlaceholderDatesAreNotRealDates() {
        assertFalse(isRealDate("0001-01-01T00:00:00"))
        assertFalse(isRealDate("0001-01-01T00:00:00Z"))
        assertFalse(isRealDate("1970-01-01T00:00:00Z"))
        assertFalse(isRealDate(""))
        assertFalse(isRealDate(null))
        assertFalse(isRealDate("not a date"))
    }

    @Test
    fun ordinaryDatesAreReal() {
        assertTrue(isRealDate("2026-11-23T18:04:56.3258366"))
        assertTrue(isRealDate("2026-11-23T18:04:56Z"))
        assertTrue(isRealDate("2026-11-23T18:04:56+00:00"))
    }

    @Test
    fun factsKeepOnlyWhatIsKnownInOrder() {
        val facts =
            seriesFacts(
                readStatus = "In progress",
                publicationStatus = "Completed",
                releaseYear = 1965,
                timeToRead = "9.1 h to read",
                wordCount = 184_000,
                wordsLabel = { "$it words" },
            )
        assertEquals(listOf("In progress", "Completed", "1965", "9.1 h to read", "184k words"), facts)
    }

    @Test
    fun missingZeroAndBlankValuesAreDropped() {
        val facts =
            seriesFacts(
                readStatus = null,
                publicationStatus = " ",
                releaseYear = 0,
                timeToRead = null,
                wordCount = 0,
                wordsLabel = { "$it words" },
            )
        assertTrue(facts.isEmpty())
    }

    @Test
    fun timeLeftIsShownOnlyWhenThereIsSomethingLeft() {
        assertEquals(2.5f, hoursLeftToRead(2.5f, 9f))
        assertEquals(9f, hoursLeftToRead(null, 9f))
        assertEquals(null, hoursLeftToRead(0f, 9f))
        assertEquals(null, hoursLeftToRead(null, null))
        assertEquals(null, hoursLeftToRead(null, 0f))
    }

    @Test
    fun aHalfReadOneChapterBookIsInProgressNotUnread() {
        assertEquals(ReadStatus.InProgress, readStatus(unreadCount = 1, totalCount = 1, hasProgress = true))
        assertEquals(ReadStatus.Unread, readStatus(unreadCount = 1, totalCount = 1, hasProgress = false))
        assertEquals(ReadStatus.Unread, readStatus(unreadCount = 1, totalCount = 1, hasProgress = null))
    }

    @Test
    fun finishedAndPartlyReadSeriesAreReportedByChapterCounts() {
        assertEquals(ReadStatus.Completed, readStatus(unreadCount = 0, totalCount = 5, hasProgress = true))
        assertEquals(ReadStatus.InProgress, readStatus(unreadCount = 2, totalCount = 5, hasProgress = false))
        assertEquals(null, readStatus(unreadCount = 0, totalCount = 0, hasProgress = true))
    }

    @Test
    fun withoutChapterCountsProgressDecides() {
        assertEquals(ReadStatus.InProgress, readStatus(null, null, true))
        assertEquals(ReadStatus.Unread, readStatus(null, null, false))
        assertEquals(null, readStatus(null, null, null))
    }
}
