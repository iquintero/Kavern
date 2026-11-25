package com.iquintero.kavern.ui.history

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.iquintero.kavern.R
import com.iquintero.kavern.core.network.KavitaApiFactory
import com.iquintero.kavern.core.network.NetworkUtils
import com.iquintero.kavern.core.storage.AppConfig
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.core.util.runCatchingCancellable
import com.iquintero.kavern.data.api.dto.ReadHistoryEventDto
import com.iquintero.kavern.data.api.dto.ReadingHistorySessionDto
import com.iquintero.kavern.ui.common.LoadingIndicator
import com.iquintero.kavern.ui.common.ServerConnectionIndicator
import com.iquintero.kavern.ui.common.rememberServerConnection
import com.iquintero.kavern.ui.common.seriesCoverUrl
import com.iquintero.kavern.ui.theme.LocalEinkMode
import com.iquintero.kavern.ui.theme.einkBorder
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** What has been fetched from the server so far. */
private data class HistoryData(
    val sessions: List<ReadingHistorySessionDto> = emptyList(),
    val legacyEvents: List<ReadHistoryEventDto> = emptyList(),
    val nextPage: Int = 1,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
)

@Composable
fun HistoryScreen(
    appPreferences: AppPreferences,
    onBack: () -> Unit,
) {
    val config by appPreferences.configFlow.collectAsState(
        initial = AppConfig(serverUrl = "", apiKey = "", imageApiKey = "", userId = 0),
    )
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var data by remember { mutableStateOf(HistoryData()) }
    var periodName by rememberSaveable { mutableStateOf(HistoryPeriod.Week.name) }
    val period = HistoryPeriod.valueOf(periodName)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(config.serverUrl, config.apiKey, config.caCertificate) {
        if (!config.isConfigured) {
            isLoading = false
            error = context.getString(R.string.general_server_or_api_key_not_set)
            return@LaunchedEffect
        }
        if (!NetworkUtils.isOnline(context)) {
            isLoading = false
            error = context.getString(R.string.general_offline)
            return@LaunchedEffect
        }
        isLoading = true
        error = null
        when (val result = fetchHistoryPage(context, config, page = 1)) {
            is HistoryFetch.Sessions -> data = HistoryData(sessions = result.sessions, nextPage = 2, hasMore = result.hasMore)
            is HistoryFetch.Legacy -> data = HistoryData(legacyEvents = result.events)
            is HistoryFetch.Failed -> error = result.message
        }
        isLoading = false
    }

    val zone = ZoneId.systemDefault()
    val view = remember(data, period) { buildHistory(data.sessions, data.legacyEvents, period, LocalDate.now(zone), zone) }

    // The summary has to cover the whole period, so keep fetching older pages until the period is reached.
    val oldestLoaded = remember(data.sessions) { oldestSessionDay(data.sessions) }
    val periodStart = period.days?.let { LocalDate.now(zone).minusDays(it - 1L) }
    val needsOlder = periodStart != null && oldestLoaded != null && oldestLoaded.isAfter(periodStart)
    LaunchedEffect(data.nextPage, data.hasMore, needsOlder) {
        if (data.hasMore && !data.loadingMore && needsOlder && data.nextPage <= MAX_AUTO_PAGES) {
            data = data.copy(loadingMore = true)
            data = loadMoreHistory(context, config, data)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.nav_history)) },
            // The app Scaffold already pads for the status bar.
            windowInsets = WindowInsets(0),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back))
                }
            },
            actions = {
                ServerConnectionIndicator(
                    connection = rememberServerConnection(),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            },
        )
        Surface(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                isLoading -> CenteredBox { LoadingIndicator() }
                error != null -> CenteredBox { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
                else ->
                    HistoryContent(
                        view = view,
                        period = period,
                        config = config,
                        hasMore = data.hasMore,
                        loadingMore = data.loadingMore,
                        onPeriod = { periodName = it.name },
                        onLoadMore = {
                            if (!data.loadingMore) {
                                scope.launch {
                                    data = data.copy(loadingMore = true)
                                    data = loadMoreHistory(context, config, data)
                                }
                            }
                        },
                    )
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun HistoryContent(
    view: HistoryView,
    period: HistoryPeriod,
    config: AppConfig,
    hasMore: Boolean,
    loadingMore: Boolean,
    onPeriod: (HistoryPeriod) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "period") { PeriodChips(period, onPeriod) }
        item(key = "summary") { SummaryCard(view) }
        if (view.days.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(if (hasMore || period == HistoryPeriod.All) R.string.history_no_history else R.string.history_period_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        view.days.forEach { day ->
            val dayKey = day.date?.toEpochDay() ?: "unknown"
            item(key = "day-$dayKey") { DayHeader(day) }
            items(day.entries, key = { "row-$dayKey-${it.seriesId ?: it.seriesName}" }) { entry ->
                HistoryRow(entry, config)
            }
        }
        if (hasMore) {
            item(key = "more") {
                OutlinedButton(onClick = onLoadMore, enabled = !loadingMore, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.history_load_earlier))
                }
            }
        }
    }
}

@Composable
private fun PeriodChips(
    selected: HistoryPeriod,
    onSelect: (HistoryPeriod) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HistoryPeriod.entries.forEach { period ->
            FilterChip(
                selected = period == selected,
                onClick = { onSelect(period) },
                label = {
                    Text(
                        stringResource(
                            when (period) {
                                HistoryPeriod.Week -> R.string.history_period_week
                                HistoryPeriod.Month -> R.string.history_period_month
                                HistoryPeriod.All -> R.string.history_period_all
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun SummaryCard(view: HistoryView) {
    val summary = view.summary
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = einkBorder(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                summary.seconds?.let { Stat(stringResource(R.string.history_stat_time), durationText(it)) }
                summary.pages?.let { Stat(stringResource(R.string.history_stat_pages), it.toString()) }
                Stat(stringResource(R.string.history_stat_books), summary.books.toString())
                Stat(stringResource(R.string.history_stat_streak), summary.streak.toString())
            }
            if (summary.seconds != null) DayBars(view.bars)
        }
    }
}

@Composable
private fun Stat(
    label: String,
    value: String,
) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val BarAreaHeight = 56.dp
private val EmptyBarHeight = 2.dp
private val MinBarHeight = 4.dp

/** A bar for each day, as tall as the minutes read that day. */
@Composable
private fun DayBars(bars: List<HistoryBar>) {
    if (bars.isEmpty()) return
    val tallest = bars.maxOf { it.minutes }.coerceAtLeast(1)
    val barColor = if (LocalEinkMode.current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.outlineVariant
    val description = stringResource(R.string.history_chart_description, bars.size)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(BarAreaHeight).semantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(if (bars.size > WIDE_CHART_DAYS) 2.dp else 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEach { bar ->
                Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    val height = if (bar.minutes == 0) EmptyBarHeight else (BarAreaHeight * bar.minutes / tallest).coerceAtLeast(MinBarHeight)
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(height)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(if (bar.minutes == 0) emptyColor else barColor),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val edge = MaterialTheme.typography.labelSmall
            Text(shortDate(bars.first().date), style = edge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.general_today), style = edge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val WIDE_CHART_DAYS = 10

private fun shortDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))

@Composable
private fun DayHeader(day: HistoryDay) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(dayLabel(day.date, context), style = MaterialTheme.typography.titleSmall)
        day.seconds?.let {
            Text(durationText(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val CoverWidth = 40.dp
private val CoverHeight = 60.dp

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    config: AppConfig,
) {
    val cover = entry.seriesId?.let { seriesCoverUrl(config, it) }
    val timeFormat = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = einkBorder(),
            modifier = Modifier.size(width = CoverWidth, height = CoverHeight),
        ) {
            SubcomposeAsyncImage(
                model = cover,
                contentDescription = entry.seriesName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> Box(contentAlignment = Alignment.Center) { LoadingIndicator(modifier = Modifier.size(16.dp)) }
                    is AsyncImagePainter.State.Error ->
                        Box(contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.history_no_cover), style = MaterialTheme.typography.labelSmall)
                        }
                    else -> SubcomposeAsyncImageContent()
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                entry.seriesName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val details = entryDetails(entry)
            if (details.isNotEmpty()) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        entry.lastReadAt?.let {
            Text(
                it.format(timeFormat),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(TimeColumnWidth),
            )
        }
    }
}

private val TimeColumnWidth = 56.dp

/** "Chapter 3 – Chapter 5 · 22 min · 14 pages", leaving out whatever the server did not say. */
@Composable
private fun entryDetails(entry: HistoryEntry): String {
    val context = LocalContext.current
    val chapters =
        when {
            entry.firstChapter == null -> null
            entry.firstChapter == entry.lastChapter || entry.chapterCount <= 1 -> chapterText(context, entry.firstChapter)
            else -> "${chapterText(context, entry.firstChapter)} – ${chapterText(context, entry.lastChapter.orEmpty())}"
        }
    val pages =
        entry.pages?.takeIf { it > 0 }?.let { context.resources.getQuantityString(R.plurals.history_pages, it, it) }
    return listOfNotNull(chapters, entry.seconds?.takeIf { it > 0 }?.let { durationText(context, it) }, pages).joinToString(" · ")
}

/** Servers before 0.8.9 give a bare chapter number; newer ones give a label that already says what it is. */
private fun chapterText(
    context: Context,
    label: String,
): String = if (label.toFloatOrNull() != null) context.getString(R.string.history_chapter_single, label) else label

@Composable
private fun durationText(seconds: Int): String = durationText(LocalContext.current, seconds)

private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60

private fun durationText(
    context: Context,
    seconds: Int,
): String {
    val minutes = maxOf(1, (seconds + SECONDS_PER_MINUTE / 2) / SECONDS_PER_MINUTE)
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        hours == 0 -> context.getString(R.string.history_duration_min, rest)
        rest == 0 -> context.getString(R.string.history_duration_h, hours)
        else -> context.getString(R.string.history_duration_h_min, hours, rest)
    }
}

private fun dayLabel(
    date: LocalDate?,
    context: Context,
): String {
    if (date == null) return context.getString(R.string.general_unknown_date)
    return when (ChronoUnit.DAYS.between(date, LocalDate.now())) {
        0L -> context.getString(R.string.general_today)
        1L -> context.getString(R.string.general_yesterday)
        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
    }
}

private fun oldestSessionDay(sessions: List<ReadingHistorySessionDto>): LocalDate? =
    sessions
        .mapNotNull { it.localDate?.take(DATE_LENGTH)?.let { day -> runCatching { LocalDate.parse(day) }.getOrNull() } }
        .minOrNull()

private const val DATE_LENGTH = 10
private const val HISTORY_PAGE_SIZE = 100
private const val HTTP_NOT_FOUND = 404

/** Most pages fetched without the reader asking, so a long-unread period cannot keep the screen fetching. */
private const val MAX_AUTO_PAGES = 6

private sealed interface HistoryFetch {
    data class Sessions(
        val sessions: List<ReadingHistorySessionDto>,
        val hasMore: Boolean,
    ) : HistoryFetch

    data class Legacy(
        val events: List<ReadHistoryEventDto>,
    ) : HistoryFetch

    data class Failed(
        val message: String?,
    ) : HistoryFetch
}

private suspend fun loadMoreHistory(
    context: Context,
    config: AppConfig,
    current: HistoryData,
): HistoryData {
    if (!current.hasMore) return current.copy(loadingMore = false)
    return when (val result = fetchHistoryPage(context, config, current.nextPage)) {
        is HistoryFetch.Sessions ->
            current.copy(
                sessions = current.sessions + result.sessions,
                nextPage = current.nextPage + 1,
                hasMore = result.hasMore,
                loadingMore = false,
            )
        else -> current.copy(hasMore = false, loadingMore = false)
    }
}

/** One page of sessions; Kavita 0.8.9 replaced the history route, so older servers fall back to the original one. */
private suspend fun fetchHistoryPage(
    context: Context,
    config: AppConfig,
    page: Int,
): HistoryFetch {
    val api = KavitaApiFactory.createAuthenticated(config)
    val libraryIds =
        runCatchingCancellable {
            api
                .getLibrariesFilter()
                .body()
                .orEmpty()
                .map { it.id }
        }.getOrDefault(emptyList())
    val response =
        runCatchingCancellable {
            api.getReadingHistorySessions(
                pageNumber = page,
                pageSize = HISTORY_PAGE_SIZE,
                timeZoneId = ZoneId.systemDefault().id,
                libraries = libraryIds,
            )
        }
    val body = response.getOrNull()
    return when {
        body?.isSuccessful == true -> {
            val sessions = body.body().orEmpty()
            HistoryFetch.Sessions(sessions, hasMore = sessions.size >= HISTORY_PAGE_SIZE)
        }
        body == null -> HistoryFetch.Failed(response.exceptionOrNull()?.message)
        body.code() != HTTP_NOT_FOUND -> HistoryFetch.Failed(context.getString(R.string.history_error_http, body.code()))
        else -> fetchLegacyHistory(context, config)
    }
}

private suspend fun fetchLegacyHistory(
    context: Context,
    config: AppConfig,
): HistoryFetch {
    val api = KavitaApiFactory.createAuthenticated(config)
    val userId =
        if (config.userId > 0) {
            config.userId
        } else {
            runCatchingCancellable { api.getAccount() }.getOrNull()?.body()?.id ?: 0
        }
    val result = runCatchingCancellable { api.getReadingHistory(userId = userId) }
    val resp = result.getOrNull()
    return when {
        resp?.isSuccessful == true -> HistoryFetch.Legacy(resp.body().orEmpty())
        resp != null -> HistoryFetch.Failed(context.getString(R.string.history_error_http, resp.code()))
        else -> HistoryFetch.Failed(result.exceptionOrNull()?.message)
    }
}
