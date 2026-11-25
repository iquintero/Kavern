package com.iquintero.kavern.data.repository

import com.iquintero.kavern.domain.model.ReaderProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressSyncPlanTest {
    private fun progress(
        page: Int,
        scrollId: String? = null,
        at: Long = 1_000,
    ) = ReaderProgress(chapterId = 1, page = page, bookScrollId = scrollId, lastModifiedUtcMillis = at)

    @Test
    fun `a chapter the server knows nothing about is sent`() {
        assertEquals(ProgressSyncPlan.SendLocal, planProgressSync(progress(3), remote = null))
    }

    @Test
    fun `a position the server already has is not sent again`() {
        assertEquals(ProgressSyncPlan.AlreadyOnServer, planProgressSync(progress(3, "idx:4"), progress(3, "idx:4", at = 9)))
        // A stored position without an anchor says nothing the server's page does not.
        assertEquals(ProgressSyncPlan.AlreadyOnServer, planProgressSync(progress(3), progress(3, "idx:4", at = 9)))
    }

    @Test
    fun `a different anchor on the same page is sent when it is more recent`() {
        assertEquals(
            ProgressSyncPlan.SendLocal,
            planProgressSync(progress(3, "idx:9", at = 2_000), progress(3, "idx:4", at = 1_000)),
        )
    }

    @Test
    fun `offline reading that is newer than the server's copy is sent`() {
        assertEquals(ProgressSyncPlan.SendLocal, planProgressSync(progress(8, at = 2_000), progress(3, at = 1_000)))
    }

    @Test
    fun `reading done elsewhere after the stored position is kept`() {
        assertEquals(ProgressSyncPlan.ServerIsNewer, planProgressSync(progress(8, at = 1_000), progress(12, at = 2_000)))
    }
}
