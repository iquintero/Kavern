package com.iquintero.kavern.core.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class RunCatchingCancellableTest {
    @Test
    fun `returns the value on success`() {
        assertEquals(42, runCatchingCancellable { 42 }.getOrNull())
    }

    @Test
    fun `returns ordinary failures as a result`() {
        val failure = IOException("offline")
        assertEquals(failure, runCatchingCancellable<Int> { throw failure }.exceptionOrNull())
    }

    @Test
    fun `also returns errors that are not exceptions`() {
        assertTrue(runCatchingCancellable<Int> { throw StackOverflowError() }.exceptionOrNull() is StackOverflowError)
    }

    @Test(expected = CancellationException::class)
    fun `rethrows cancellation`() {
        runCatchingCancellable<Int> { throw CancellationException("cancelled") }
    }

    @Test
    fun `a cancelled coroutine is not reported as a failed request`() =
        runBlocking {
            val started = CompletableDeferred<Unit>()
            var reportedFailure = false
            val job =
                async(start = CoroutineStart.UNDISPATCHED) {
                    // Plain runCatching would return a failure here and let the caller carry on as if the call failed.
                    runCatchingCancellable {
                        started.complete(Unit)
                        delay(60_000)
                    }.onFailure { reportedFailure = true }
                }
            started.await()
            job.cancel()
            val outcome = runCatching { job.await() }.exceptionOrNull()
            assertTrue(outcome is CancellationException)
            assertEquals(false, reportedFailure)
        }

    @Test
    fun `plain runCatching does swallow cancellation, which is why this helper exists`() =
        runBlocking {
            val result = runCatching { throw CancellationException("cancelled") }
            assertTrue(result.exceptionOrNull() is CancellationException)
        }
}
