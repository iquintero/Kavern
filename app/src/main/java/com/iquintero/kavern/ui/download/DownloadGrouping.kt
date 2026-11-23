package com.iquintero.kavern.ui.download

import com.iquintero.kavern.data.local.db.entity.DownloadJobV2Entity
import com.iquintero.kavern.data.local.db.entity.DownloadedItemV2Entity

/** One downloaded chapter: every stored page or file that belongs to it. [label] is null when nothing useful is known. */
data class DownloadedChapter(
    val chapterId: Int,
    val label: String?,
    val itemIds: List<Long>,
    val bytes: Long,
    val updatedAt: Long,
)

/** Everything downloaded for one series, which is what the Downloads page lists as a single row. */
data class DownloadedBook(
    val seriesId: Int,
    val title: String?,
    val chapters: List<DownloadedChapter>,
) {
    val bytes: Long get() = chapters.sumOf { it.bytes }
    val updatedAt: Long get() = chapters.maxOf { it.updatedAt }
    val itemIds: List<Long> get() = chapters.flatMap { it.itemIds }
}

/**
 * Groups stored items by series, then by chapter. An EPUB is stored as one item per page, so without this
 * a single book would be dozens of rows. Most recently downloaded books come first.
 */
fun groupDownloads(
    items: List<DownloadedItemV2Entity>,
    lookup: DownloadLookup,
): List<DownloadedBook> =
    items
        .filter { it.seriesId != null }
        .groupBy { it.seriesId ?: 0 }
        .map { (seriesId, seriesItems) ->
            val chapters =
                seriesItems
                    .groupBy { it.chapterId ?: 0 }
                    .map { (chapterId, chapterItems) ->
                        DownloadedChapter(
                            chapterId = chapterId,
                            label = chapterLabel(chapterItems.firstNotNullOfOrNull { it.volumeId }, chapterId, lookup),
                            itemIds = chapterItems.map { it.id },
                            bytes = chapterItems.sumOf { it.bytes ?: 0L },
                            updatedAt = chapterItems.maxOf { it.updatedAt },
                        )
                    }.sortedBy { it.chapterId }
            DownloadedBook(seriesId = seriesId, title = lookup.seriesNames[seriesId], chapters = chapters)
        }.sortedByDescending { it.updatedAt }

/** Kavita fills in this number when a chapter has none, so titles like "Issue #-100000" say nothing. */
private const val NO_NUMBER = "-100000"

/** Volume and chapter name together, leaving out whichever one is missing or is just Kavita's placeholder. */
internal fun chapterLabel(
    volumeId: Int?,
    chapterId: Int,
    lookup: DownloadLookup,
): String? {
    val volume = volumeId?.let { lookup.volumeNames[it] }
    val chapter = lookup.chapterTitles[chapterId]?.takeUnless { it.contains(NO_NUMBER) }
    return listOfNotNull(volume, chapter).distinct().joinToString(" · ").ifBlank { null }
}

private val activeStates =
    listOf(
        DownloadJobV2Entity.STATUS_RUNNING,
        DownloadJobV2Entity.STATUS_PENDING,
        DownloadJobV2Entity.STATUS_PAUSED,
        DownloadJobV2Entity.STATUS_FAILED,
    )

/**
 * Downloads that still need attention, running ones first and failed ones last. Finished and canceled jobs are
 * history, so the page leaves them out: what finished is already listed with the downloaded books.
 */
fun activeDownloads(jobs: List<DownloadJobV2Entity>): List<DownloadJobV2Entity> =
    jobs
        .filter { it.status in activeStates }
        .sortedWith(compareBy({ activeStates.indexOf(it.status) }, { it.createdAt }))

/** Share of a job that is done, 0 to 100, or null while its size is not known yet. */
fun downloadPercent(job: DownloadJobV2Entity): Int? {
    val total = job.totalItems ?: return null
    if (total <= 0) return null
    val done = (job.completedItems ?: 0).coerceIn(0, total)
    return done * 100 / total
}
