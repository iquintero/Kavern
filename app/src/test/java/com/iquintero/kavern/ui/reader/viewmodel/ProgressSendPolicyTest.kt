package com.iquintero.kavern.ui.reader.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressSendPolicyTest {
    private val sent = ReadPosition(chapterId = 1, page = 3, scrollId = "id(\"a\")")

    private fun decide(
        next: ReadPosition,
        sentAt: ReadPosition? = sent,
        sinceMs: Long = 5_000,
        chapterSizedPages: Boolean = true,
    ) = ProgressSendPolicy.decide(sentAt, next, sinceMs, chapterSizedPages)

    @Test
    fun `the first position of a reader with nothing sent goes out`() {
        assertEquals(ProgressDecision.SendNow, decide(ReadPosition(1, 0), sentAt = null))
    }

    @Test
    fun `a page without an anchor on the page already sent is nothing new`() {
        assertEquals(ProgressDecision.Ignore, decide(ReadPosition(1, 3, null)))
    }

    @Test
    fun `a new chapter or a new book page is sent at once`() {
        assertEquals(ProgressDecision.SendNow, decide(ReadPosition(2, 0)))
        assertEquals(ProgressDecision.SendNow, decide(ReadPosition(1, 4)))
    }

    @Test
    fun `a page of a comic waits for a pause like any other small step`() {
        assertEquals(
            ProgressDecision.SendAfter(ProgressSendPolicy.QUIET_MS),
            decide(ReadPosition(1, 4), chapterSizedPages = false),
        )
        assertEquals(ProgressDecision.SendNow, decide(ReadPosition(2, 0), chapterSizedPages = false))
    }

    @Test
    fun `a new spot inside the same page waits for a pause`() {
        assertEquals(
            ProgressDecision.SendAfter(ProgressSendPolicy.QUIET_MS),
            decide(ReadPosition(1, 3, "id(\"b\")")),
        )
    }

    @Test
    fun `the wait is cut short so a long stretch of reading still reports every two minutes`() {
        assertEquals(
            ProgressDecision.SendAfter(5_000),
            decide(ReadPosition(1, 3, "id(\"b\")"), sinceMs = ProgressSendPolicy.MAX_WAIT_MS - 5_000),
        )
        assertEquals(
            ProgressDecision.SendAfter(0),
            decide(ReadPosition(1, 3, "id(\"b\")"), sinceMs = ProgressSendPolicy.MAX_WAIT_MS + 1),
        )
    }

    @Test
    fun `coming back to the position the server has drops what was waiting`() {
        assertEquals(ProgressDecision.DropPending, decide(sent))
        assertEquals(
            ProgressDecision.DropPending,
            decide(ReadPosition(1, 3), sentAt = ReadPosition(1, 3), chapterSizedPages = false),
        )
    }
}
