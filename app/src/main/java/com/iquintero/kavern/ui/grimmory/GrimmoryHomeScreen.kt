package com.iquintero.kavern.ui.grimmory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
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
import com.iquintero.kavern.core.network.ServerReachability
import com.iquintero.kavern.core.network.grimmory.GrimmoryBook
import com.iquintero.kavern.grimmory.GrimmoryBooks
import com.iquintero.kavern.grimmory.GrimmoryDownloads
import com.iquintero.kavern.ui.common.DownloadState
import com.iquintero.kavern.ui.common.PagedBox
import com.iquintero.kavern.ui.common.ServerConnectionIndicator
import com.iquintero.kavern.ui.common.StrongProgressBar
import com.iquintero.kavern.ui.common.pageScroller
import com.iquintero.kavern.ui.common.rememberServerConnection
import com.iquintero.kavern.ui.library.MessageState
import com.iquintero.kavern.ui.theme.einkBorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What Home shows: the book to pick up next, the others that are started, and what was added lately. */
internal data class GrimmoryHomeState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val offline: Boolean = false,
    val resume: GrimmoryBook? = null,
    val inProgress: List<GrimmoryBook> = emptyList(),
    val recent: List<GrimmoryBook> = emptyList(),
) {
    val isEmpty: Boolean get() = resume == null && inProgress.isEmpty() && recent.isEmpty()
}

internal class GrimmoryHomeViewModel(
    private val books: GrimmoryBooks,
    downloads: GrimmoryDownloads,
) : ViewModel() {
    private val _state = MutableStateFlow(GrimmoryHomeState())
    val state: StateFlow<GrimmoryHomeState> = _state

    val downloadStates: StateFlow<Map<Int, DownloadState>> =
        downloads.states.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    init {
        load()
    }

    fun refresh() {
        books.refresh()
        load()
    }

    /** Looks again when the screen comes back, without forcing the server to be asked if the library was loaded just now. */
    fun refreshQuietly() = load()

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadFailed = false) }
            runCatching {
                val started = books.inProgress()
                val shown = started.map { it.id }.toSet()
                GrimmoryHomeState(
                    isLoading = false,
                    offline = books.isOffline,
                    resume = started.firstOrNull(),
                    inProgress = started.drop(1),
                    recent = books.recentlyAdded().filter { it.id !in shown },
                )
            }.onSuccess { done -> _state.value = done }
                .onFailure { _state.update { it.copy(isLoading = false, loadFailed = true) } }
        }
    }

    companion object {
        fun factory(
            books: GrimmoryBooks,
            downloads: GrimmoryDownloads,
        ): ViewModelProvider.Factory = viewModelFactory { initializer { GrimmoryHomeViewModel(books, downloads) } }
    }
}

@Composable
internal fun GrimmoryHomeScreen(
    books: GrimmoryBooks,
    downloads: GrimmoryDownloads,
    onOpenBook: (Int) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: GrimmoryHomeViewModel = viewModel(factory = GrimmoryHomeViewModel.factory(books, downloads))
    val state by viewModel.state.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshQuietly() }
    // The lists are the server's or the kept copy; when the verdict on the server changes, ask again.
    val unreachable by ServerReachability.unreachable.collectAsState()
    LaunchedEffect(unreachable) { viewModel.refreshQuietly() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.nav_home)) },
            windowInsets = WindowInsets(0),
            actions = {
                ServerConnectionIndicator(connection = rememberServerConnection(), modifier = Modifier.padding(horizontal = 8.dp))
                IconButton(onClick = onOpenHistory) { Icon(Icons.Filled.History, contentDescription = stringResource(R.string.nav_history)) }
                IconButton(onClick = viewModel::refresh) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.home_refresh)) }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings)) }
            },
        )
        when {
            state.isEmpty && state.isLoading -> Box(Modifier.fillMaxSize())

            state.isEmpty ->
                MessageState(
                    icon = if (state.loadFailed) Icons.Filled.CloudOff else Icons.AutoMirrored.Filled.MenuBook,
                    text = stringResource(if (state.loadFailed) R.string.library_home_load_failed else R.string.grimmory_home_empty),
                    actionLabel = if (state.loadFailed) stringResource(R.string.download_action_retry) else null,
                    onAction = viewModel::refresh,
                )

            else -> {
                val listState = rememberLazyListState()
                PagedBox(listState.pageScroller()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        if (state.offline) {
                            item(key = "offline") {
                                Text(
                                    text = stringResource(R.string.grimmory_offline_notice),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                        state.resume?.let { book ->
                            item(key = "resume") {
                                ResumeCard(book, books.coverUrl(book.id), downloadStates[book.id] ?: DownloadState.None, onOpenBook)
                            }
                        }
                        bookRow(R.string.home_in_progress, state.inProgress, books, downloadStates, onOpenBook)
                        bookRow(R.string.home_recently_added, state.recent, books, downloadStates, onOpenBook)
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.bookRow(
    titleRes: Int,
    list: List<GrimmoryBook>,
    books: GrimmoryBooks,
    downloadStates: Map<Int, DownloadState>,
    onOpenBook: (Int) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "row-$titleRes") {
        Column {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list, key = { it.id }) { book ->
                    Box(modifier = Modifier.width(ROW_COVER_WIDTH)) {
                        BookCard(book, books.coverUrl(book.id), downloadStates[book.id] ?: DownloadState.None, onOpenBook)
                    }
                }
            }
        }
    }
}

/** The one book to pick up next: a large cover, how far along it is, and a Resume button. */
@Composable
private fun ResumeCard(
    book: GrimmoryBook,
    coverUrl: String,
    downloadState: DownloadState,
    onOpenBook: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = einkBorder(),
        onClick = { onOpenBook(book.id) },
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BookCover(book, coverUrl, downloadState, modifier = Modifier.width(RESUME_COVER_WIDTH))
            Column(
                modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_continue_reading),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(text = book.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                StrongProgressBar(progress = book.progress / WHOLE)
                Text(
                    text = stringResource(R.string.home_percent_read, book.progress.toInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Button(onClick = { onOpenBook(book.id) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.home_resume))
                }
            }
        }
    }
}

private val ROW_COVER_WIDTH = 112.dp
private val RESUME_COVER_WIDTH = 128.dp
