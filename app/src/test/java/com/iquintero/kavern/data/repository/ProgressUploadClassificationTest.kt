package com.iquintero.kavern.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressUploadClassificationTest {
    @Test
    fun successIsSent() {
        assertEquals(ProgressUploadResult.Sent, classifyProgressStatus(200))
        assertEquals(ProgressUploadResult.Sent, classifyProgressStatus(204))
    }

    @Test
    fun serverAndRateLimitErrorsAreRetried() {
        listOf(408, 429, 500, 503).forEach {
            assertEquals("status $it", ProgressUploadResult.RetryableFailure, classifyProgressStatus(it))
        }
    }

    @Test
    fun authFailuresAreRetriedNotDropped() {
        assertEquals(ProgressUploadResult.RetryableFailure, classifyProgressStatus(401))
        assertEquals(ProgressUploadResult.RetryableFailure, classifyProgressStatus(403))
    }

    @Test
    fun otherClientErrorsAreRejected() {
        listOf(400, 404, 409, 422).forEach {
            assertEquals("status $it", ProgressUploadResult.Rejected, classifyProgressStatus(it))
        }
    }
}
