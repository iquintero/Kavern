package com.iquintero.kavern.ui.grimmory

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iquintero.kavern.R
import com.iquintero.kavern.core.util.runCatchingCancellable
import com.iquintero.kavern.grimmory.DownloadEntry
import com.iquintero.kavern.grimmory.GrimmoryBooks
import com.iquintero.kavern.grimmory.GrimmoryDownloads
import com.iquintero.kavern.ui.common.DownloadState
import com.iquintero.kavern.ui.common.ServerConnectionIndicator
import com.iquintero.kavern.ui.common.rememberServerConnection
import com.iquintero.kavern.ui.library.MessageState
import kotlinx.coroutines.launch

/** The books on this device, and the ones on their way. */
@Composable
internal fun GrimmoryDownloadsScreen(
    books: GrimmoryBooks,
    downloads: GrimmoryDownloads,
    onOpenBook: (Int) -> Unit,
) {
    val entries by downloads.entries.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var titles by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    LaunchedEffect(entries.size) {
        runCatchingCancellable { books.all() }.onSuccess { list -> titles = list.associate { it.id to it.title } }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.nav_downloads)) },
            windowInsets = WindowInsets(0),
            actions = { ServerConnectionIndicator(connection = rememberServerConnection(), modifier = Modifier.padding(horizontal = 8.dp)) },
        )
        if (entries.isEmpty()) {
            MessageState(icon = Icons.Filled.Download, text = stringResource(R.string.grimmory_downloads_empty), actionLabel = null, onAction = {})
            return@Column
        }
        val onDevice = entries.filter { it.state == DownloadState.Complete }
        Text(
            text = pluralStringResource(R.plurals.grimmory_downloads_summary, onDevice.size, onDevice.size, Formatter.formatShortFileSize(context, onDevice.sumOf { it.bytes })),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(entries, key = { it.bookId }) { entry ->
                EntryRow(
                    entry = entry,
                    title = titles[entry.bookId] ?: "",
                    coverUrl = books.coverUrl(entry.bookId),
                    size = Formatter.formatShortFileSize(context, entry.bytes),
                    onOpen = { onOpenBook(entry.bookId) },
                    onRemove = { scope.launch { downloads.remove(entry.bookId) } },
                )
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: DownloadEntry,
    title: String,
    coverUrl: String,
    size: String,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onOpen,
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverThumb(coverUrl, modifier = Modifier.width(56.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Text(
                    text =
                        when {
                            entry.failed -> stringResource(R.string.grimmory_download_failed)
                            entry.state == DownloadState.Complete -> size
                            entry.total > 0 -> stringResource(R.string.grimmory_download_progress, entry.done * PERCENT / entry.total)
                            else -> stringResource(R.string.grimmory_downloading)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (entry.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = if (entry.state == DownloadState.Complete) Icons.Filled.Delete else Icons.Filled.Close,
                    contentDescription = stringResource(R.string.grimmory_remove_download),
                )
            }
        }
    }
}

private const val PERCENT = 100
