package com.iquintero.kavern.ui.grimmory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.iquintero.kavern.R
import com.iquintero.kavern.core.network.grimmory.GrimmoryBook
import com.iquintero.kavern.grimmory.GrimmoryBookDetails
import com.iquintero.kavern.grimmory.GrimmoryBooks
import com.iquintero.kavern.grimmory.GrimmoryDownloads
import com.iquintero.kavern.ui.common.DownloadState
import com.iquintero.kavern.ui.common.StrongProgressBar
import com.iquintero.kavern.ui.seriesdetail.utils.cleanHtml
import com.iquintero.kavern.ui.theme.einkBorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class GrimmoryBookState(
    val isLoading: Boolean = true,
    val book: GrimmoryBook? = null,
    val details: GrimmoryBookDetails? = null,
    val series: List<GrimmoryBook> = emptyList(),
    /** How many parts the book is read and downloaded in; null when the app cannot read it. */
    val parts: Int? = null,
)

internal class GrimmoryBookViewModel(
    private val bookId: Int,
    private val books: GrimmoryBooks,
    private val downloads: GrimmoryDownloads,
) : ViewModel() {
    private val _state = MutableStateFlow(GrimmoryBookState())
    val state: StateFlow<GrimmoryBookState> = _state

    val downloadState: StateFlow<DownloadState> =
        downloads.states
            .map { it[bookId] ?: DownloadState.None }
            .stateIn(viewModelScope, SharingStarted.Lazily, DownloadState.None)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val book = runCatching { books.book(bookId) }.getOrNull()
            if (book == null) {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            _state.update { it.copy(isLoading = false, book = book) }
            launch { runCatching { books.siblings(book) }.onSuccess { list -> _state.update { it.copy(series = list) } } }
            launch { runCatching { books.details(bookId) }.onSuccess { d -> _state.update { it.copy(details = d) } } }
            launch {
                // A downloaded copy can be read at once; the server, when it answers, has the full count.
                downloads.downloadedParts(bookId)?.let { local -> _state.update { it.copy(parts = it.parts ?: local) } }
                runCatching { books.parts(book) }.getOrNull()?.let { full -> _state.update { it.copy(parts = full) } }
            }
        }
    }

    fun setRead(read: Boolean) {
        viewModelScope.launch {
            runCatching { books.setRead(bookId, read) }
            load()
        }
    }

    fun download() {
        val parts = _state.value.parts ?: return
        viewModelScope.launch { downloads.download(bookId, parts) }
    }

    fun removeDownload() {
        viewModelScope.launch { downloads.remove(bookId) }
    }

    companion object {
        fun factory(
            id: Int,
            books: GrimmoryBooks,
            downloads: GrimmoryDownloads,
        ): ViewModelProvider.Factory = viewModelFactory { initializer { GrimmoryBookViewModel(id, books, downloads) } }
    }
}

@Composable
internal fun GrimmoryBookScreen(
    bookId: Int,
    books: GrimmoryBooks,
    downloads: GrimmoryDownloads,
    onOpenReader: (bookId: Int, part: Int) -> Unit,
    onOpenBook: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: GrimmoryBookViewModel = viewModel(key = "book-$bookId", factory = GrimmoryBookViewModel.factory(bookId, books, downloads))
    val state by viewModel.state.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    var menu by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val book = state.book

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {},
            windowInsets = WindowInsets(0),
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back)) } },
            actions = {
                if (book != null) {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.grimmory_mark_read)) },
                            onClick = {
                                menu = false
                                viewModel.setRead(true)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.grimmory_mark_unread)) },
                            onClick = {
                                menu = false
                                viewModel.setRead(false)
                            },
                        )
                    }
                }
            },
        )
        if (book == null) {
            if (!state.isLoading) {
                Text(
                    text = stringResource(R.string.grimmory_book_not_found),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Column
        }
        val readable = book.format == GrimmoryBook.FORMAT_EPUB && state.parts != null
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BookCover(book, books.coverUrl(book.id), downloadState, modifier = Modifier.width(COVER_WIDTH))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(book.title, style = MaterialTheme.typography.headlineSmall)
                    if (book.authors.isNotEmpty()) {
                        Text(
                            text = book.authors.joinToString { it.asName() },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    book.seriesName?.let { name ->
                        Text(
                            text = book.seriesNumber?.let { stringResource(R.string.grimmory_series_line, name, numberText(it)) } ?: name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Facts(book, state.details)
                }
            }

            ProgressCard(book)

            if (!readable && state.parts == null && book.format != GrimmoryBook.FORMAT_EPUB) {
                Text(
                    text = stringResource(R.string.grimmory_unsupported_format, book.fileType ?: "?"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = {
                    val parts = state.parts ?: 0
                    val part =
                        if (book.progress <= 0f ||
                            book.progress >= GrimmoryBook.FINISHED
                        ) {
                            0
                        } else {
                            Math.round(book.progress * parts / WHOLE).coerceIn(0, (parts - 1).coerceAtLeast(0))
                        }
                    onOpenReader(book.id, part)
                },
                enabled = readable,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(
                        when {
                            book.progress <= 0f -> R.string.series_detail_start_reading
                            book.progress >= GrimmoryBook.FINISHED -> R.string.volume_detail_re_read
                            else -> R.string.home_resume
                        },
                    ),
                )
            }
            OutlinedButton(
                onClick = { if (downloadState == DownloadState.Complete) confirmRemove = true else viewModel.download() },
                enabled = readable && downloadState != DownloadState.Partial,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(if (downloadState == DownloadState.Complete) Icons.Filled.DownloadDone else Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(
                        when (downloadState) {
                            DownloadState.Complete -> R.string.grimmory_downloaded
                            DownloadState.Partial -> R.string.grimmory_downloading
                            DownloadState.None -> R.string.grimmory_download
                        },
                    ),
                )
            }

            cleanHtml(state.details?.description)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            if (state.series.size > 1) {
                Text(stringResource(R.string.grimmory_in_series), style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.series, key = { it.id }) { other ->
                        BookCard(other, books.coverUrl(other.id), DownloadState.None, onOpenBook, modifier = Modifier.width(SERIES_COVER_WIDTH))
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    if (confirmRemove && book != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.series_detail_remove_chapter_title)) },
            text = { Text(stringResource(R.string.series_detail_remove_chapter_body, book.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        viewModel.removeDownload()
                    },
                ) { Text(stringResource(R.string.series_detail_remove_chapter_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.general_close)) } },
        )
    }
}

@Composable
private fun Facts(
    book: GrimmoryBook,
    details: GrimmoryBookDetails?,
) {
    val facts =
        listOfNotNull(
            details?.year,
            details?.pageCount?.let { stringResource(R.string.grimmory_printed_pages, it) },
            book.fileType,
        )
    if (facts.isEmpty()) return
    Text(
        text = facts.joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ProgressCard(book: GrimmoryBook) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = einkBorder(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.Start) {
            StrongProgressBar(progress = book.progress / WHOLE)
            Text(
                text = stringResource(R.string.home_percent_read, book.progress.toInt()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun numberText(number: Float): String = if (number % 1f == 0f) number.toInt().toString() else number.toString()

private val COVER_WIDTH = 140.dp
private val SERIES_COVER_WIDTH = 112.dp
