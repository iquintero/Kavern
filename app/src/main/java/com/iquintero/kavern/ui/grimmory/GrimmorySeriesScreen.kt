package com.iquintero.kavern.ui.grimmory

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
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
import com.iquintero.kavern.grimmory.GrimmoryBooks
import com.iquintero.kavern.grimmory.GrimmoryDownloads
import com.iquintero.kavern.ui.common.CoverProgressBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private class GrimmorySeriesViewModel(
    private val name: String,
    private val books: GrimmoryBooks,
) : ViewModel() {
    private val _state = MutableStateFlow(emptyList<GrimmoryBook>())
    val state: StateFlow<List<GrimmoryBook>> = _state

    fun load() {
        viewModelScope.launch { runCatching { books.inSeries(name) }.onSuccess { list -> _state.update { list } } }
    }

    companion object {
        fun factory(
            name: String,
            books: GrimmoryBooks,
        ): ViewModelProvider.Factory = viewModelFactory { initializer { GrimmorySeriesViewModel(name, books) } }
    }
}

/** One series: its books in reading order, and a way straight to the one to read next. */
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun GrimmorySeriesScreen(
    name: String,
    books: GrimmoryBooks,
    downloads: GrimmoryDownloads,
    onOpenBook: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: GrimmorySeriesViewModel = viewModel(key = "series-$name", factory = GrimmorySeriesViewModel.factory(name, books))
    val list by viewModel.state.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    val next = list.filter { it.inProgress }.maxByOrNull { it.lastReadTime.orEmpty() } ?: list.firstOrNull { it.progress < GrimmoryBook.FINISHED }
    val finished = list.count { it.progress >= GrimmoryBook.FINISHED }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            windowInsets = WindowInsets(0),
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back)) } },
        )
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text =
                            pluralStringResource(R.plurals.grimmory_series_books, list.size, list.size) +
                                " · " + stringResource(R.string.grimmory_series_finished, finished, list.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (next != null) {
                        Button(onClick = { onOpenBook(next.id) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Text(
                                text = stringResource(R.string.grimmory_series_continue, next.title),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
            items(list, key = { it.id }) { book ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenBook(book.id) },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverThumb(books.coverUrl(book.id), Modifier.width(64.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        book.seriesNumber?.let {
                            Text(
                                text = stringResource(R.string.grimmory_book_number, numberText(it)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = stringResource(R.string.home_percent_read, book.progress.toInt()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CoverProgressBar(progress = book.progress / WHOLE, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                }
            }
        }
    }
}
