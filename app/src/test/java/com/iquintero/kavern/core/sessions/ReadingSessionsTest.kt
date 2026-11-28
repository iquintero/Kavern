package com.iquintero.kavern.core.sessions

import com.iquintero.kavern.data.local.db.entity.ReadingSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSessionsTest {
    private fun point(
        at: Long,
        page: Int? = 0,
    ) = ReadingPoint(bookId = 7, seriesId = 7, page = page, pages = 20, atMs = at)

    @Test
    fun firstPointStartsASession() {
        val session = sessionAfter(null, point(1_000, page = 3))
        assertEquals(1_000L, session.startMs)
        assertEquals(1_000L, session.endMs)
        assertEquals(3, session.startPage)
        assertEquals(3, session.endPage)
    }

    @Test
    fun aPointSoonAfterExtendsTheSession() {
        val open = sessionAfter(null, point(0, page = 2)).copy(id = 5)
        val next = sessionAfter(open, point(SESSION_GAP_MS, page = 6))
        assertEquals(5L, next.id)
        assertEquals(0L, next.startMs)
        assertEquals(SESSION_GAP_MS, next.endMs)
        assertEquals(2, next.startPage)
        assertEquals(6, next.endPage)
    }

    @Test
    fun aPointAfterAQuietSpellStartsANewSession() {
        val open = sessionAfter(null, point(0)).copy(id = 5)
        val next = sessionAfter(open, point(SESSION_GAP_MS + 1, page = 9))
        assertEquals(0L, next.id)
        assertEquals(SESSION_GAP_MS + 1, next.startMs)
        assertEquals(9, next.startPage)
    }

    @Test
    fun aPointFromBeforeTheSessionDoesNotJoinIt() {
        val open = sessionAfter(null, point(10_000)).copy(id = 5)
        assertEquals(0L, sessionAfter(open, point(9_000)).id)
    }

    @Test
    fun openingABookIsNotASession() {
        assertFalse(sessionAfter(null, point(0)).worthKeeping)
        assertFalse(sessionAfter(null, point(0)).copy(endMs = 4_000).worthKeeping)
    }

    @Test
    fun timeOrMovementMakesASessionWorthKeeping() {
        assertTrue(sessionAfter(null, point(0)).copy(endMs = 5_000).worthKeeping)
        assertTrue(ReadingSessionEntity(bookId = 1, startMs = 0, endMs = 1, startPage = 1, endPage = 2).worthKeeping)
    }

    @Test
    fun durationIsInWholeSeconds() {
        assertEquals(90, ReadingSessionEntity(bookId = 1, startMs = 1_000, endMs = 91_900).durationSeconds)
    }

    @Test
    fun percentsFollowThePageInTheBook() {
        assertEquals(25f, percentAt(5, 20)!!, 0.001f)
        assertEquals(100f, percentAt(30, 20)!!, 0.001f)
        assertNull(percentAt(5, null))
        assertNull(percentAt(5, 0))
        assertNull(percentAt(null, 20))
    }
}
