package com.iquintero.kavern.ui.browse

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebouncerTest {
    @Test
    fun runsOnceAfterTheDelay() =
        runTest {
            val debouncer = Debouncer(this, 350)
            var runs = 0
            debouncer.schedule { runs++ }
            advanceTimeBy(349)
            assertEquals(0, runs)
            advanceTimeBy(2)
            assertEquals(1, runs)
        }

    @Test
    fun everyCallRestartsTheWaitSoTypingSearchesOnce() =
        runTest {
            val debouncer = Debouncer(this, 350)
            val searched = mutableListOf<String>()
            "dune".forEach { letter ->
                debouncer.schedule { searched += letter.toString() }
                advanceTimeBy(100)
            }
            advanceTimeBy(400)
            assertEquals(listOf("e"), searched)
        }

    @Test
    fun cancelDropsAPendingRun() =
        runTest {
            val debouncer = Debouncer(this, 350)
            var runs = 0
            debouncer.schedule { runs++ }
            debouncer.cancel()
            advanceTimeBy(1_000)
            assertEquals(0, runs)
        }

    @Test
    fun canBeUsedAgainAfterRunning() =
        runTest {
            val debouncer = Debouncer(this, 350)
            var runs = 0
            debouncer.schedule { runs++ }
            advanceTimeBy(400)
            debouncer.schedule { runs++ }
            advanceTimeBy(400)
            assertEquals(2, runs)
        }
}
