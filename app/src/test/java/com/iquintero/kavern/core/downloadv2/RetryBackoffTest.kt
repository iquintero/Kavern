package com.iquintero.kavern.core.downloadv2

import org.junit.Assert.assertEquals
import org.junit.Test

class RetryBackoffTest {
    @Test
    fun noWaitWithoutFailures() {
        assertEquals(0L, retryBackoffMillis(0))
    }

    @Test
    fun doublesEachFailedRound() {
        assertEquals(2_000L, retryBackoffMillis(1))
        assertEquals(4_000L, retryBackoffMillis(2))
        assertEquals(8_000L, retryBackoffMillis(3))
    }

    @Test
    fun isCapped() {
        assertEquals(60_000L, retryBackoffMillis(10))
        assertEquals(60_000L, retryBackoffMillis(1_000))
    }
}
