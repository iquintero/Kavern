package com.iquintero.kavern.ui.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iquintero.kavern.R
import com.iquintero.kavern.core.util.formatBytes
import com.iquintero.kavern.data.local.db.entity.DownloadJobV2Entity
import com.iquintero.kavern.ui.common.PagedBox
import com.iquintero.kavern.ui.common.ServerConnectionIndicator
import com.iquintero.kavern.ui.common.pageScroller
import com.iquintero.kavern.ui.common.rememberServerConnection

/** What a pending removal will delete: a whole book, or a single chapter of one. */
private data class RemovalRequest(
    val name: String,
    val bytes: Long,
    val itemIds: List<Long>,
)

/**
 * The Downloads page: what is downloading right now at the top, then one row per downloaded series.
 * Chapters stay out of the way; they are only listed on request for series that have several.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQueueScreen(
    viewModel: DownloadQueueViewModel,
    onOpenSeries: (seriesId: Int) -> Unit,
) {
    val tasks by viewModel.tasks.collectAsState()
    val downloaded by viewModel.downloaded.collectAsState()
    val lookup by viewModel.lookup.collectAsState()
    val config by viewModel.config.collectAsState()

    val books = remember(downloaded, lookup) { groupDownloads(downloaded, lookup) }
    val active = remember(tasks) { activeDownloads(tasks) }
    var removal by remember { mutableStateOf<RemovalRequest?>(null) }
    var chapterSheetFor by remember { mutableStateOf<Int?>(null) }
    val unknownSeries = stringResource(R.string.download_unknown_series)

    val listState = rememberLazyListState()
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.download_screen_title)) },
            // The app Scaffold already pads for the status bar.
            windowInsets = WindowInsets(0),
            actions = {
                ServerConnectionIndicator(
                    connection = rememberServerConnection(),
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            },
        )
        PagedBox(listState.pageScroller()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (books.isNotEmpty()) {
                    item(key = "summary") {
                        Text(
                            text =
                                stringResource(
                                    R.string.download_summary_format,
                                    pluralStringResource(R.plurals.download_book_count, books.size, books.size),
                                    formatBytes(books.sumOf { it.bytes }),
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (active.isNotEmpty()) {
                    item(key = "active-header") { SectionHeader(stringResource(R.string.download_section_active, active.size)) }
                    items(active, key = { "job-${it.id}" }) { job ->
                        ActiveDownloadCard(
                            job = job,
                            title = lookup.seriesNames[job.seriesId] ?: unknownSeries,
                            subtitle =
                                jobSubtitle(job, lookup)?.takeUnless {
                                    it.equals(lookup.seriesNames[job.seriesId], ignoreCase = true)
                                },
                            config = config,
                            onPause = { viewModel.pauseTask(job.id) },
                            onResume = { viewModel.resumeTask(job.id) },
                            onRetry = { viewModel.retryTask(job.id) },
                            onCancel = { viewModel.cancelTask(job.id) },
                        )
                    }
                }
                if (books.isNotEmpty()) {
                    if (active.isNotEmpty()) {
                        item(key = "device-header") { SectionHeader(stringResource(R.string.download_section_device)) }
                    }
                    items(books, key = { "book-${it.seriesId}" }) { book ->
                        BookRow(
                            book = book,
                            config = config,
                            onOpen = { onOpenSeries(book.seriesId) },
                            onRemove = {
                                removal = RemovalRequest(book.title ?: unknownSeries, book.bytes, book.itemIds)
                            },
                            onChooseChapters = { chapterSheetFor = book.seriesId },
                        )
                    }
                }
                if (books.isEmpty() && active.isEmpty()) {
                    item(key = "empty") { EmptyDownloads() }
                }
            }
        }
    }

    removal?.let { request ->
        ConfirmRemoveDialog(
            name = request.name,
            bytes = request.bytes,
            onConfirm = {
                viewModel.deleteDownloads(request.itemIds)
                removal = null
            },
            onDismiss = { removal = null },
        )
    }

    books.firstOrNull { it.seriesId == chapterSheetFor }?.let { book ->
        val bookName = book.title ?: unknownSeries
        ChapterSheet(
            book = book,
            onRemoveChapter = { chapter ->
                removal = RemovalRequest(chapter.label ?: bookName, chapter.bytes, chapter.itemIds)
            },
            onRemoveAll = {
                chapterSheetFor = null
                removal = RemovalRequest(bookName, book.bytes, book.itemIds)
            },
            onDismiss = { chapterSheetFor = null },
        )
    }
}

/** The chapter a job is for, or what kind of job it is when it covers more than one chapter. */
@Composable
private fun jobSubtitle(
    job: DownloadJobV2Entity,
    lookup: DownloadLookup,
): String? =
    when (job.type) {
        DownloadJobV2Entity.TYPE_CHAPTER -> lookup.chapterTitles[job.chapterId]
        DownloadJobV2Entity.TYPE_VOLUME -> lookup.volumeNames[job.volumeId] ?: stringResource(R.string.download_type_volume)
        else -> stringResource(R.string.download_type_series_all)
    }
