package com.iquintero.kavern.ui.grimmory

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
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iquintero.kavern.R
import com.iquintero.kavern.core.network.grimmory.GrimmorySession
import com.iquintero.kavern.core.util.runCatchingCancellable
import com.iquintero.kavern.domain.repository.ReaderRepository
import com.iquintero.kavern.grimmory.GrimmoryBooks
import com.iquintero.kavern.ui.common.ServerConnectionIndicator
import com.iquintero.kavern.ui.common.rememberServerConnection
import com.iquintero.kavern.ui.library.MessageState
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The reading sessions of the books that have been read, by the day they happened. */
@Composable
internal fun GrimmoryHistoryScreen(
    books: GrimmoryBooks,
    readerRepository: ReaderRepository,
    onOpenBook: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var sessions by remember { mutableStateOf<List<GrimmorySession>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Sessions that have finished but not yet been sent would otherwise be missing from the list.
        runCatchingCancellable { readerRepository.uploadReadingSessions() }
        runCatchingCancellable { books.sessions() }
            .onSuccess { sessions = it }
            .onFailure { failed = true }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.nav_history)) },
            windowInsets = WindowInsets(0),
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back)) } },
            actions = { ServerConnectionIndicator(connection = rememberServerConnection(), modifier = Modifier.padding(horizontal = 8.dp)) },
        )
        val list = sessions
        when {
            list == null && !failed -> Unit
            list == null -> MessageState(icon = Icons.Filled.CloudOff, text = stringResource(R.string.library_home_load_failed), actionLabel = null, onAction = {})
            list.isEmpty() -> MessageState(icon = Icons.Filled.History, text = stringResource(R.string.history_no_history), actionLabel = null, onAction = {})
            else -> {
                val zone = ZoneId.systemDefault()
                val days = list.groupBy { it.start.atZone(zone).toLocalDate() }.toSortedMap(compareByDescending { it })
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    days.forEach { (day, sessionsOfDay) ->
                        item(key = "day-$day") { Text(dayLabel(day), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) }
                        items(sessionsOfDay, key = { it.id }) { session -> SessionRow(session, books, zone, onOpenBook) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: GrimmorySession,
    books: GrimmoryBooks,
    zone: ZoneId,
    onOpenBook: (Int) -> Unit,
) {
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())
    val range = listOfNotNull(session.start, session.end).joinToString(" – ") { time.format(it.atZone(zone)) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = { onOpenBook(session.bookId) },
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CoverThumb(books.coverUrl(session.bookId), modifier = Modifier.width(56.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(session.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                if (session.startPercent != null && session.endPercent != null) {
                    Text(
                        text = "${session.startPercent.toInt()}% – ${session.endPercent.toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(range, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = stringResource(R.string.history_session_percent_summary, session.percentRead, maxOf(1, (session.durationSeconds + SECONDS - 1) / SECONDS)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A day as people say it, with the year when it is not this one. */
private fun dayLabel(day: LocalDate): String {
    val pattern = if (day.year == LocalDate.now().year) "d. MMMM" else "d. MMMM yyyy"
    return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(day)
}

private const val SECONDS = 60
