package com.iquintero.kavern.ui.download

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.iquintero.kavern.R
import com.iquintero.kavern.core.storage.AppConfig
import com.iquintero.kavern.core.util.formatBytes
import com.iquintero.kavern.data.local.db.entity.DownloadJobV2Entity
import com.iquintero.kavern.ui.common.seriesCoverUrl

private val ListCoverWidth = 56.dp
private val ListCoverHeight = 84.dp
private val ActiveCoverWidth = 44.dp
private val ActiveCoverHeight = 66.dp
private const val FAILED_BORDER_ALPHA = 0.6f

/** A series cover with a book icon behind it, so the row keeps its shape while the image loads or is unavailable. */
@Composable
private fun BookCover(
    config: AppConfig,
    seriesId: Int,
    width: Dp,
    height: Dp,
) {
    Box(
        modifier =
            Modifier
                .size(width, height)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
        val url = seriesCoverUrl(config, seriesId)
        if (url != null) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(url)
                        .crossfade(true)
                        .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width, height),
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/** A downloaded series. Tapping opens it; the menu frees its space. */
@Composable
fun BookRow(
    book: DownloadedBook,
    config: AppConfig,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onChooseChapters: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val title = book.title ?: stringResource(R.string.download_unknown_series)
    val chapterCount = book.chapters.size
    val size = formatBytes(book.bytes)
    val meta =
        if (chapterCount > 1) {
            stringResource(
                R.string.download_meta_format,
                pluralStringResource(R.plurals.download_chapter_count, chapterCount, chapterCount),
                size,
            )
        } else {
            size
        }
    val moreDescription = stringResource(R.string.download_more_description, title)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(config, book.seriesId, ListCoverWidth, ListCoverHeight)
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.download_downloaded_ago, relativeTime(book.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = moreDescription)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (chapterCount > 1) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.download_menu_chapters)) },
                            onClick = {
                                menuOpen = false
                                onChooseChapters()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.download_menu_remove)) },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}

/** A download that is waiting, running, paused or failed, with the controls that make sense for that state. */
@Composable
fun ActiveDownloadCard(
    job: DownloadJobV2Entity,
    title: String,
    subtitle: String?,
    config: AppConfig,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val failed = job.status == DownloadJobV2Entity.STATUS_FAILED
    val paused = job.status == DownloadJobV2Entity.STATUS_PAUSED
    val percent = downloadPercent(job)
    val statusText =
        when {
            failed -> stringResource(R.string.download_status_failed)
            paused -> stringResource(R.string.download_status_paused)
            job.status == DownloadJobV2Entity.STATUS_PENDING -> stringResource(R.string.download_status_waiting)
            percent != null -> stringResource(R.string.download_status_downloading, percent)
            else -> stringResource(R.string.download_status_starting)
        }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        // A failed download keeps the neutral card and is marked by an outline, which reads well in light and dark.
        border = if (failed) BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = FAILED_BORDER_ALPHA)) else null,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            job.seriesId?.let { BookCover(config, it, ActiveCoverWidth, ActiveCoverHeight) }
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!failed) {
                    if (percent != null && job.status != DownloadJobV2Entity.STATUS_PENDING) {
                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(MaterialTheme.shapes.extraSmall),
                        )
                    } else if (job.status == DownloadJobV2Entity.STATUS_RUNNING) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp).clip(MaterialTheme.shapes.extraSmall))
                    }
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (failed) {
                    job.error?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // No extra padding or minimum width, so the label lines up with the text above it.
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                        TextButton(
                            onClick = onRetry,
                            modifier = Modifier.defaultMinSize(minWidth = 1.dp),
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                        ) {
                            Text(stringResource(R.string.download_action_retry))
                        }
                    }
                }
            }
            Column {
                if (!failed) {
                    if (paused) {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.download_action_resume))
                        }
                    } else {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.download_action_pause))
                        }
                    }
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.download_cancel_description, title))
                }
            }
        }
    }
}

@Composable
fun EmptyDownloads() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        Text(text = stringResource(R.string.download_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.download_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Asks before removing downloads, and says how much space that frees. */
@Composable
fun ConfirmRemoveDialog(
    name: String,
    bytes: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_remove_title)) },
        text = { Text(stringResource(R.string.download_remove_body, name, formatBytes(bytes))) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.download_remove_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.general_cancel)) }
        },
    )
}

/** Lets someone free space chapter by chapter for series that have several downloaded. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterSheet(
    book: DownloadedBook,
    onRemoveChapter: (DownloadedChapter) -> Unit,
    onRemoveAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                text = book.title ?: stringResource(R.string.download_unknown_series),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.download_sheet_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(book.chapters, key = { it.chapterId }) { chapter ->
                    val index = book.chapters.indexOf(chapter) + 1
                    val name = chapter.label ?: stringResource(R.string.download_chapter_number, index)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
                            Text(text = name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = formatBytes(chapter.bytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onRemoveChapter(chapter) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.download_remove_chapter_description, name))
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = onRemoveAll,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.download_sheet_remove_all))
            }
        }
    }
}

@Composable
private fun relativeTime(timestamp: Long): String =
    if (timestamp <= 0) {
        "—"
    } else if (System.currentTimeMillis() - timestamp < DateUtils.MINUTE_IN_MILLIS) {
        stringResource(R.string.download_just_now)
    } else {
        DateUtils
            .getRelativeTimeSpanString(timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
            .toString()
    }
