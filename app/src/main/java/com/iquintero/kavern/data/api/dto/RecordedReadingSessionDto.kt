package com.iquintero.kavern.data.api.dto

/** A stretch of reading the app tells the server about after the fact, with the times it really had. */
data class RecordedReadingSessionDto(
    val bookId: Int,
    val startTimeUtc: String,
    val endTimeUtc: String,
    val durationSeconds: Int,
    val startPercent: Float? = null,
    val endPercent: Float? = null,
)
