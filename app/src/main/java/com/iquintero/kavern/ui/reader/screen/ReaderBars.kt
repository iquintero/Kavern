package com.iquintero.kavern.ui.reader.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Toc
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iquintero.kavern.R
import com.iquintero.kavern.domain.model.ReaderTocItem
import com.iquintero.kavern.ui.common.StrongProgressBar

@Composable
internal fun TocDialog(
    items: List<ReaderTocItem>,
    currentPage: Int,
    onSelect: (ReaderTocItem) -> Unit,
    onDismiss: () -> Unit,
) {
    // Best guess for "where am I": the first entry of the furthest book page that is not past the current one.
    val currentIndex =
        remember(items, currentPage) {
            val page = items.filter { it.page <= currentPage }.maxOfOrNull { it.page }
            items.indexOfFirst { it.page == page }.takeIf { it >= 0 }
        }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex ?: 0).coerceAtLeast(0))
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_contents)) },
        text = {
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(items) { index, item ->
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (index == currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(item) }
                                .padding(start = (item.depth.coerceAtMost(4) * 16).dp, top = 10.dp, bottom = 10.dp),
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.general_close))
            }
        },
    )
}

@Composable
internal fun ReaderTopBar(
    title: String,
    pageText: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenToc: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(pageText, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onOpenToc != null) {
            IconButton(onClick = onOpenToc) {
                Icon(Icons.Filled.Toc, contentDescription = stringResource(R.string.reader_contents))
            }
        }
    }
}

@Composable
internal fun ReaderBottomBar(
    isLoading: Boolean,
    enableChapterNav: Boolean,
    pageIndex: Int,
    pageCount: Int,
    timeLeft: String?,
    enableSettings: Boolean,
    progress: Float,
    onScrollTop: () -> Unit,
    onScrollBottom: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onOpenSettings: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp),
    ) {
        timeLeft?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
        }
        StrongProgressBar(progress = progress, height = 8.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left cluster: prev chapter, jump top, prev page
            IconButton(onClick = onPrevChapter, enabled = enableChapterNav) {
                Icon(Icons.Filled.FastRewind, contentDescription = stringResource(R.string.reader_prev_chapter))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onScrollTop, enabled = !isLoading) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.reader_jump_to_start),
                        modifier = Modifier.rotate(90f),
                    )
                }
                IconButton(onClick = onPrevPage, enabled = !isLoading) {
                    Icon(Icons.Filled.NavigateBefore, contentDescription = stringResource(R.string.reader_prev_page))
                }
            }
            // Center: settings
            IconButton(onClick = onOpenSettings, enabled = enableSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.reader_reader_settings))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onNextPage, enabled = !isLoading) {
                    Icon(Icons.Filled.NavigateNext, contentDescription = stringResource(R.string.reader_next_page))
                }
                IconButton(onClick = onScrollBottom, enabled = !isLoading) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.reader_jump_to_end),
                        modifier = Modifier.rotate(-90f),
                    )
                }
            }
            IconButton(onClick = onNextChapter, enabled = enableChapterNav) {
                Icon(Icons.Filled.FastForward, contentDescription = stringResource(R.string.reader_next_chapter))
            }
        }
    }
}

data class ReaderBottomBarState(
    val isLoading: Boolean,
    val enableChapterNav: Boolean,
    val pageIndex: Int,
    val pageCount: Int,
    val timeLeft: String?,
    val enableSettings: Boolean,
    val progress: Float = 0f,
)

data class ReaderBottomBarCallbacks(
    val onScrollTop: () -> Unit,
    val onScrollBottom: () -> Unit,
    val onPrevPage: () -> Unit,
    val onNextPage: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onPrevChapter: () -> Unit,
    val onNextChapter: () -> Unit,
)
