package com.iquintero.kavern.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.iquintero.kavern.data.local.db.entity.CachedPageEntity
import com.iquintero.kavern.data.local.db.entity.LocalReaderProgressEntity
import com.iquintero.kavern.data.local.db.entity.ReadingSessionEntity

@Dao
interface ReaderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPage(page: CachedPageEntity)

    @Query("SELECT * FROM cached_pages WHERE chapterId = :chapterId AND page = :page LIMIT 1")
    suspend fun getPage(
        chapterId: Int,
        page: Int,
    ): CachedPageEntity?

    @Query("DELETE FROM cached_pages WHERE chapterId = :chapterId")
    suspend fun clearChapter(chapterId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocalProgress(progress: LocalReaderProgressEntity)

    @Query("SELECT * FROM local_progress WHERE chapterId = :chapterId LIMIT 1")
    suspend fun getLocalProgress(chapterId: Int): LocalReaderProgressEntity?

    @Query("SELECT * FROM local_progress")
    suspend fun getAllLocalProgress(): List<LocalReaderProgressEntity>

    @Query("DELETE FROM local_progress WHERE chapterId = :chapterId")
    suspend fun clearLocalProgress(chapterId: Int)

    @Insert
    suspend fun insertReadingSession(session: ReadingSessionEntity): Long

    @Update
    suspend fun updateReadingSession(session: ReadingSessionEntity)

    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId AND uploaded = 0 ORDER BY endMs DESC LIMIT 1")
    suspend fun latestOpenReadingSession(bookId: Int): ReadingSessionEntity?

    /** Sessions that have not been sent and have been quiet since [endedBefore], so nothing more will be added to them. */
    @Query("SELECT * FROM reading_sessions WHERE uploaded = 0 AND endMs < :endedBefore ORDER BY startMs")
    suspend fun finishedReadingSessions(endedBefore: Long): List<ReadingSessionEntity>

    @Query("UPDATE reading_sessions SET uploaded = 1 WHERE id = :id")
    suspend fun markReadingSessionUploaded(id: Long)

    @Query("DELETE FROM reading_sessions WHERE id = :id")
    suspend fun deleteReadingSession(id: Long)

    @Transaction
    suspend fun finishProgressSync(
        original: LocalReaderProgressEntity,
        replacement: LocalReaderProgressEntity?,
    ) {
        if (getLocalProgress(original.chapterId) != original) return

        if (replacement == null) {
            clearLocalProgress(original.chapterId)
        } else {
            upsertLocalProgress(replacement)
        }
    }
}
