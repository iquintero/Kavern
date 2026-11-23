package com.iquintero.kavern.ui.common

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageScrollingTest {
    @Test
    fun aPageJumpIsJustUnderOneScreenSoTheEyeKeepsItsPlace() {
        assertEquals(900f, pageDelta(1000, forward = true), 0.001f)
        assertEquals(-900f, pageDelta(1000, forward = false), 0.001f)
    }

    private fun scroller(
        canBack: Boolean = true,
        canForward: Boolean = true,
        viewport: Int = 2000,
        record: MutableList<Float>,
    ) = StatePageScroller(
        canBack = { canBack },
        canForward = { canForward },
        viewportPx = { viewport },
        scrollBy = { record += it },
    )

    @Test
    fun pagingForwardScrollsDownAndBackScrollsUp() =
        runTest {
            val moves = mutableListOf<Float>()
            val s = scroller(record = moves)
            s.page(forward = true)
            s.page(forward = false)
            assertEquals(listOf(1800f, -1800f), moves)
        }

    @Test
    fun nothingScrollsPastTheEnds() =
        runTest {
            val moves = mutableListOf<Float>()
            val atTop = scroller(canBack = false, record = moves)
            atTop.page(forward = false)
            val atBottom = scroller(canForward = false, record = moves)
            atBottom.page(forward = true)
            assertTrue(moves.isEmpty())
            assertFalse(atTop.canPage(forward = false))
            assertTrue(atTop.canPage(forward = true))
        }

    @Test
    fun aListThatHasNotBeenMeasuredYetDoesNotScroll() =
        runTest {
            val moves = mutableListOf<Float>()
            scroller(viewport = 0, record = moves).page(forward = true)
            assertTrue(moves.isEmpty())
        }
}

class PageKeyEventsTest {
    @Test
    fun anOldScreenLeavingDoesNotRemoveTheNewScreensHandler() {
        val oldScreen: (Boolean) -> Unit = {}
        val newScreen: (Boolean) -> Unit = {}
        PageKeyEvents.claim(oldScreen)
        // The new screen is composed first, then the old one is disposed.
        PageKeyEvents.claim(newScreen)
        PageKeyEvents.release(oldScreen)
        assertTrue(PageKeyEvents.isClaimed())
        PageKeyEvents.release(newScreen)
        assertFalse(PageKeyEvents.isClaimed())
    }
}
