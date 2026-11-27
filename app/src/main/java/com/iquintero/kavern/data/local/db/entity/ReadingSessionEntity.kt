package com.iquintero.kavern.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A stretch of reading in one book, kept on the device so that it keeps its real times however late it reaches the server.
 * Pages are the reader's own units; [pages] is the book's length in them when it was known.
 */
@Entity(tableName = "reading_sessions", indices = [Index("bookId")])
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Int,
    val seriesId: Int? = null,
    val startMs: Long,
    val endMs: Long,
    val startPage: Int? = null,
    val endPage: Int? = null,
    val pages: Int? = null,
    val uploaded: Boolean = false,
)
