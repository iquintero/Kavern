package com.iquintero.kavern.data.mapper

import com.iquintero.kavern.data.api.dto.BookInfoDto
import com.iquintero.kavern.data.api.dto.ReaderChapterNavDto
import com.iquintero.kavern.data.api.dto.ReaderProgressDto
import com.iquintero.kavern.data.api.dto.TimeLeftDto
import com.iquintero.kavern.data.local.db.entity.LocalReaderProgressEntity
import com.iquintero.kavern.domain.model.ReaderBookInfo
import com.iquintero.kavern.domain.model.ReaderChapterNav
import com.iquintero.kavern.domain.model.ReaderProgress
import com.iquintero.kavern.domain.model.ReaderTimeLeft
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

private fun String?.toEpochMillis(): Long {
    val raw = this?.trim().orEmpty()
    if (raw.isBlank()) return 0L
    runCatching { return Instant.parse(raw).toEpochMilli() }
    runCatching { return OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
    runCatching {
        return LocalDateTime
            .parse(raw)
            .atZone(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
    }
    return 0L
}

fun ReaderProgressDto.toDomain(): ReaderProgress =
    ReaderProgress(
        chapterId = chapterId,
        page = pageNum,
        bookScrollId = bookScrollId,
        seriesId = seriesId,
        volumeId = volumeId,
        libraryId = libraryId,
        lastModifiedUtcMillis = lastModifiedUtc.toEpochMillis(),
    )

fun ReaderProgress.toDto(): ReaderProgressDto =
    ReaderProgressDto(
        chapterId = chapterId,
        pageNum = page,
        bookScrollId = bookScrollId,
        seriesId = seriesId,
        volumeId = volumeId,
        libraryId = libraryId,
        lastModifiedUtc = Instant.ofEpochMilli(lastModifiedUtcMillis).toString(),
    )

fun TimeLeftDto.toDomain(): ReaderTimeLeft =
    ReaderTimeLeft(
        minHours = minHours,
        maxHours = maxHours,
        avgHours = avgHours,
    )

fun BookInfoDto.toDomain(): ReaderBookInfo =
    ReaderBookInfo(
        pages = pages,
        seriesId = seriesId,
        volumeId = volumeId,
        libraryId = libraryId,
        title = bookTitle ?: seriesName,
        pageTitle = chapterTitle,
    )

fun ReaderChapterNavDto.toDomain(): ReaderChapterNav {
    val chapter = chapterId ?: id
    return ReaderChapterNav(
        seriesId = seriesId,
        volumeId = volumeId,
        chapterId = chapter,
        pagesRead = pagesRead,
    )
}

fun LocalReaderProgressEntity.toDomain(): ReaderProgress =
    ReaderProgress(
        chapterId = chapterId,
        page = page,
        bookScrollId = bookScrollId,
        seriesId = seriesId,
        volumeId = volumeId,
        libraryId = libraryId,
        lastModifiedUtcMillis = lastModifiedUtc,
    )

fun ReaderProgress.toEntity(): LocalReaderProgressEntity =
    LocalReaderProgressEntity(
        chapterId = chapterId,
        page = page,
        bookScrollId = bookScrollId,
        seriesId = seriesId,
        volumeId = volumeId,
        libraryId = libraryId,
        lastModifiedUtc = lastModifiedUtcMillis,
    )
