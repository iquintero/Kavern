package com.iquintero.kavern.ui.grimmory

import com.iquintero.kavern.core.network.grimmory.GrimmorySession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class GrimmoryHistoryTest {
    private fun session(
        id: Int,
        seconds: Int,
        from: Double?,
        to: Double?,
    ) = GrimmorySession(id, 1, "Book", Instant.EPOCH, Instant.EPOCH.plusSeconds(seconds.toLong()), seconds, from, to)

    @Test
    fun `a book opened and left is not reading`() {
        val visit = session(1, seconds = 20, from = 19.0, to = 19.0)
        assertEquals(emptyList<GrimmorySession>(), listOf(visit).readingOnly())
    }

    @Test
    fun `reading that moved the place is kept however short`() {
        val moved = session(1, seconds = 30, from = 19.0, to = 20.0)
        assertEquals(listOf(moved), listOf(moved).readingOnly())
    }

    @Test
    fun `a longer stretch is kept even when the percentage did not change`() {
        // Progress counts parts of the book, so reading inside one part moves nothing.
        val within = session(1, seconds = 600, from = 19.0, to = 19.0)
        assertEquals(listOf(within), listOf(within).readingOnly())
    }
}
