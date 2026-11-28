package com.iquintero.kavern.ui.grimmory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.iquintero.kavern.core.network.grimmory.GrimmoryServers
import com.iquintero.kavern.grimmory.GrimmoryBooks
import com.iquintero.kavern.grimmory.GrimmoryDownloads
import com.iquintero.kavern.grimmory.GrimmoryLibraryInfo
import com.iquintero.kavern.ui.common.DownloadState
import com.iquintero.kavern.ui.common.PagedBox
import com.iquintero.kavern.ui.common.ServerConnectionIndicator
import com.iquintero.kavern.ui.common.pageScroller
import com.iquintero.kavern.ui.common.rememberServerConnection
import com.iquintero.kavern.ui.library.MessageState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class BookSort(
    val label: Int,
) {
    Title(R.string.grimmory_sort_title),
    LastRead(R.string.grimmory_sort_recent),
    Added(R.string.grimmory_sort_added),
    Progress(R.string.grimmory_sort_progress),
}

internal enum class BookStatus(
    val label: Int,
) {
    All(R.string.grimmory_filter_all),
    Reading(R.string.grimmory_filter_reading),
    Unread(R.string.grimmory_filter_unread),
    Finished(R.string.grimmory_filter_finished),
}

internal enum class LibraryView(
    val label: Int,
) {
    Books(R.string.grimmory_view_books),
    Series(R.string.grimmory_view_series),
}

/** Books that belong together: a series, or a lone book that has none. */
internal data class BookGroup(
    val series: String?,
    val books: List<GrimmoryBook>,
    /** How many books the series has in all, when [books] holds only those that match what was asked for. */
    val total: Int = books.size,
) {
    val first: GrimmoryBook get() = books.first()
    val finished: Int get() = books.count { it.progress >= GrimmoryBook.FINISHED }

    /** How far along the whole series is, 0 to 100. */
    val progress: Float get() = books.sumOf { it.progress.toDouble() }.toFloat() / books.size
}

internal data class GrimmoryLibraryState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val offline: Boolean = false,
    val books: List<GrimmoryBook> = emptyList(),
    val libraries: List<GrimmoryLibraryInfo> = emptyList(),
    val query: String = "",
    val sort: BookSort = BookSort.Title,
    val status: BookStatus = BookStatus.All,
    val library: Int? = null,
    val view: LibraryView = LibraryView.Books,
) {
    private val matching: List<GrimmoryBook>
        get() {
            val text = query.trim()
            return books.filter { book ->
                (library == null || book.libraryId == library) &&
                    when (status) {
                        BookStatus.All -> true
                        BookStatus.Reading -> book.inProgress
                        BookStatus.Unread -> book.progress <= 0f
                        BookStatus.Finished -> book.progress >= GrimmoryBook.FINISHED
                    } &&
                    (text.isEmpty() || book.matches(text))
            }
        }

    /** The books to show: the ones that match what was asked for, in the order chosen. */
    val visible: List<GrimmoryBook>
        get() {
            val matching = matching
            return when (sort) {
                BookSort.Title -> matching.sortedBy { it.title.lowercase() }
                BookSort.LastRead -> matching.sortedByDescending { it.lastReadTime.orEmpty() }
                BookSort.Added -> matching.sortedByDescending { it.addedOn.orEmpty() }
                BookSort.Progress -> matching.sortedByDescending { it.progress }
            }
        }

    /** The same books gathered by series, ordered by the series as a whole. */
    val groups: List<BookGroup>
        get() {
            val bySeries = matching.groupBy { it.seriesName }
            val grouped =
                bySeries
                    .filterKeys { it != null }
                    .map { (name, list) -> BookGroup(name, list.sortedBy { it.seriesNumber ?: Float.MAX_VALUE }, total = books.count { it.seriesName == name }) }
            val single = bySeries[null].orEmpty().map { BookGroup(null, listOf(it)) }
            val all = grouped + single
            return when (sort) {
                BookSort.Title -> all.sortedBy { (it.series ?: it.first.title).lowercase() }
                BookSort.LastRead ->
                    all.sortedByDescending { group ->
                        group.books
                            .mapNotNull { it.lastReadTime }
                            .maxOrNull()
                            .orEmpty()
                    }
                BookSort.Added ->
                    all.sortedByDescending { group ->
                        group.books
                            .mapNotNull { it.addedOn }
                            .maxOrNull()
                            .orEmpty()
                    }
                BookSort.Progress -> all.sortedByDescending { it.progress }
            }
        }
}

private fun GrimmoryBook.matches(text: String): Boolean =
    title.contains(text, ignoreCase = true) ||
        seriesName?.contains(text, ignoreCase = true) == true ||
        authors.any { it.contains(text, ignoreCase = true) || it.asName().contains(text, ignoreCase = true) }

internal class GrimmoryLibraryViewModel(
    private val books: GrimmoryBooks,
    downloads: GrimmoryDownloads,
) : ViewModel() {
    private val _state = MutableStateFlow(GrimmoryLibraryState())
    val state: StateFlow<GrimmoryLibraryState> = _state

    val downloadStates: StateFlow<Map<Int, DownloadState>> = downloads.states.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    init {
        load()
    }

    fun reload() {
        books.refresh()
        load()
    }

    fun reloadQuietly() = load()

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadFailed = false) }
            runCatching { books.all() }
                .onSuccess { list -> _state.update { it.copy(isLoading = false, books = list, offline = books.isOffline) } }
                .onFailure { _state.update { it.copy(isLoading = false, loadFailed = true) } }
            runCatching { books.libraries() }.onSuccess { list -> _state.update { it.copy(libraries = list) } }
        }
    }

    fun search(text: String) = _state.update { it.copy(query = text) }

    fun sortBy(sort: BookSort) = _state.update { it.copy(sort = sort) }

    fun filterBy(status: BookStatus) = _state.update { it.copy(status = status) }

    fun show(view: LibraryView) = _state.update { it.copy(view = view) }

    fun inLibrary(id: Int?) = _state.update { it.copy(library = id) }

    companion object {
        fun factory(
            books: GrimmoryBooks,
            downloads: GrimmoryDownloads,
        ): ViewModelProvider.Factory = viewModelFactory { initializer { GrimmoryLibraryViewModel(books, downloads) } }
    }
}

@Composable
internal fun GrimmoryLibraryScreen(
    books: GrimmoryBooks,
    downloads: GrimmoryDownloads,
    onOpenBook: (Int) -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: GrimmoryLibraryViewModel = viewModel(factory = GrimmoryLibraryViewModel.factory(books, downloads))
    val state by viewModel.state.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()
    var sortMenu by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reloadQuietly() }
    // The lists are the server's or the kept copy; when the verdict on the server changes, ask again.
    val unreachable by ServerReachability.unreachable.collectAsState()
    LaunchedEffect(unreachable) { viewModel.reloadQuietly() }
    // After signing in again, whatever could not be loaded is asked for again.
    val signInNeeded by GrimmoryServers.signInRequired.collectAsState()
    LaunchedEffect(signInNeeded) { viewModel.reloadQuietly() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.nav_library)) },
            windowInsets = WindowInsets(0),
            actions = {
                ServerConnectionIndicator(connection = rememberServerConnection(), modifier = Modifier.padding(horizontal = 8.dp))
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    BookSort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(stringResource(sort.label)) },
                            onClick = {
                                sortMenu = false
                                viewModel.sortBy(sort)
                            },
                        )
                    }
                }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings)) }
            },
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            LibraryView.entries.forEachIndexed { index, view ->
                SegmentedButton(
                    selected = state.view == view,
                    onClick = { viewModel.show(view) },
                    shape = SegmentedButtonDefaults.itemShape(index, LibraryView.entries.size),
                ) { Text(stringResource(view.label)) }
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::search,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.grimmory_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.search("") }) { Icon(Icons.Filled.Clear, contentDescription = null) }
                }
            },
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(BookStatus.entries.toList()) { status ->
                FilterChip(selected = state.status == status, onClick = { viewModel.filterBy(status) }, label = { Text(stringResource(status.label)) })
            }
            if (state.libraries.size > 1) {
                items(state.libraries, key = { it.id }) { library ->
                    FilterChip(
                        selected = state.library == library.id,
                        onClick = { viewModel.inLibrary(if (state.library == library.id) null else library.id) },
                        label = { Text(library.name) },
                    )
                }
            }
        }
        val visible = state.visible
        val groups = state.groups
        when {
            state.books.isEmpty() && state.isLoading -> Unit

            state.books.isEmpty() ->
                MessageState(
                    icon = Icons.Filled.CloudOff,
                    text = stringResource(R.string.library_home_load_failed),
                    actionLabel = stringResource(R.string.download_action_retry),
                    onAction = viewModel::reload,
                )

            visible.isEmpty() ->
                Text(
                    text = stringResource(R.string.grimmory_library_empty),
                    modifier = Modifier.padding(16.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )

            else -> {
                val gridState = rememberLazyGridState()
                PagedBox(gridState.pageScroller()) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(GRID_MIN_WIDTH),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (state.view == LibraryView.Books) {
                            items(visible, key = { "book-${it.id}" }) { book ->
                                BookCard(book, books.coverUrl(book.id), downloadStates[book.id] ?: DownloadState.None, onOpenBook)
                            }
                        } else {
                            items(groups, key = { group -> group.series?.let { "series-$it" } ?: "book-${group.first.id}" }) { group ->
                                val series = group.series
                                if (series == null) {
                                    BookCard(group.first, books.coverUrl(group.first.id), downloadStates[group.first.id] ?: DownloadState.None, onOpenBook)
                                } else {
                                    SeriesCard(group, series, books.coverUrl(group.first.id), onOpen = { onOpenSeries(series) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val GRID_MIN_WIDTH = 104.dp
