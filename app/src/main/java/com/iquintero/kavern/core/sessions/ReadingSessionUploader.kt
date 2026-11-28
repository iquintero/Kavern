package com.iquintero.kavern.core.sessions

import com.iquintero.kavern.core.util.runCatchingCancellable
import com.iquintero.kavern.data.api.KavitaApi
import com.iquintero.kavern.data.api.dto.RecordedReadingSessionDto
import com.iquintero.kavern.data.local.db.dao.ReaderDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * Sends finished sessions to the server with the times they really had, which is what lets reading done offline land on
 * the right day. Sessions that fail to send stay and go out next time.
 */
internal class ReadingSessionUploader(
    private val dao: ReaderDao,
) {
    suspend fun upload(
        api: KavitaApi,
        now: Long = System.currentTimeMillis(),
    ) = upload(now) { api.recordReadingSession(it).isSuccessful }

    /** Sends each finished session with [send], which says whether the server took it. */
    suspend fun upload(
        now: Long = System.currentTimeMillis(),
        send: suspend (RecordedReadingSessionDto) -> Boolean,
    ) = lock.withLock {
        // The list is read inside the lock: a session another upload has just sent is no longer in it.
        for (session in dao.finishedReadingSessions(endedBefore = now - SESSION_GAP_MS)) {
            if (!session.worthKeeping) {
                dao.deleteReadingSession(session.id)
                continue
            }
            val sent =
                runCatchingCancellable {
                    send(
                        RecordedReadingSessionDto(
                            bookId = session.bookId,
                            startTimeUtc = Instant.ofEpochMilli(session.startMs).toString(),
                            endTimeUtc = Instant.ofEpochMilli(session.endMs).toString(),
                            durationSeconds = session.durationSeconds,
                            startPercent = percentAt(session.startPage, session.pages),
                            endPercent = percentAt(session.endPage, session.pages),
                        ),
                    )
                }.getOrDefault(false)
            if (!sent) return@withLock
            dao.markReadingSessionUploaded(session.id)
        }
    }

    private companion object {
        /** Uploads can start from the reader, the sync job and the history page at once; a session must go out once. */
        val lock = Mutex()
    }
}
