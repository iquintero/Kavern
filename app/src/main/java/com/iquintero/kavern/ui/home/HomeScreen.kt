package com.iquintero.kavern.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.iquintero.kavern.R
import com.iquintero.kavern.core.cache.CacheManager
import com.iquintero.kavern.core.storage.AppConfig
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.domain.model.Series
import com.iquintero.kavern.domain.repository.SeriesRepository
import com.iquintero.kavern.ui.common.COVER_ASPECT_RATIO
import com.iquintero.kavern.ui.common.DownloadState
import com.iquintero.kavern.ui.common.SeriesCoverCard
import com.iquintero.kavern.ui.common.ServerConnectionIndicator
import com.iquintero.kavern.ui.common.ShimmerBox
import com.iquintero.kavern.ui.common.StrongProgressBar
import com.iquintero.kavern.ui.common.rememberServerConnection
import com.iquintero.kavern.ui.common.rememberShimmerProgress
import com.iquintero.kavern.ui.common.seriesCoverUrl
import com.iquintero.kavern.ui.library.MessageState
import com.iquintero.kavern.ui.library.seriesProgress
import com.iquintero.kavern.ui.theme.einkBorder
import com.iquintero.kavern.ui.theme.einkOutline

@Composable
fun HomeScreen(
    seriesRepository: SeriesRepository,
    cacheManager: CacheManager,
    appPreferences: AppPreferences,
    onOpenSeries: (Int) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: HomeViewModel =
        viewModel(factory = HomeViewModel.provideFactory(seriesRepository, cacheManager, appPreferences))
    val uiState by viewModel.state.collectAsState()
    val config by appPreferences.configFlow.collectAsState(
        initial = AppConfig(serverUrl = "", apiKey = "", imageApiKey = "", userId = 0),
    )
    val showDownloadBadges by appPreferences.showDownloadBadgesFlow.collectAsState(initial = true)
    val sections = uiState.sections

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.nav_home)) },
            // The app Scaffold already pads for the status bar.
            windowInsets = WindowInsets(0),
            actions = {
                ServerConnectionIndicator(
                    connection = rememberServerConnection(),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                IconButton(onClick = onOpenHistory) {
                    Icon(Icons.Filled.History, contentDescription = stringResource(R.string.nav_history))
                }
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.home_refresh))
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings))
                }
            },
        )
        when {
            sections.isEmpty && uiState.isLoading -> HomeSkeleton()

            sections.isEmpty ->
                MessageState(
                    icon = if (uiState.loadFailed) Icons.Filled.CloudOff else Icons.AutoMirrored.Filled.MenuBook,
                    text = stringResource(if (uiState.loadFailed) R.string.library_home_load_failed else R.string.home_empty),
                    actionLabel = if (uiState.loadFailed) stringResource(R.string.download_action_retry) else null,
                    onAction = viewModel::refresh,
                )

            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    sections.resume?.let { series ->
                        item(key = "resume") {
                            ResumeCard(
                                series = series,
                                config = config,
                                downloadState = uiState.downloadStates[series.id] ?: DownloadState.None,
                                onOpenSeries = onOpenSeries,
                            )
                        }
                    }
                    seriesRow(R.string.home_in_progress, sections.inProgress, config, uiState.downloadStates, showDownloadBadges, onOpenSeries)
                    seriesRow(R.string.home_want_to_read, sections.wantToRead, config, uiState.downloadStates, showDownloadBadges, onOpenSeries)
                    seriesRow(R.string.home_recently_added, sections.recentlyAdded, config, uiState.downloadStates, showDownloadBadges, onOpenSeries)
                }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.seriesRow(
    titleRes: Int,
    series: List<Series>,
    config: AppConfig,
    downloadStates: Map<Int, DownloadState>,
    showDownloadBadges: Boolean,
    onOpenSeries: (Int) -> Unit,
) {
    if (series.isEmpty()) return
    item(key = "row-$titleRes") {
        Column {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(series, key = { it.id }) { item ->
                    Box(modifier = Modifier.width(ROW_COVER_WIDTH)) {
                        SeriesCoverCard(
                            series = item,
                            config = config,
                            downloadState = downloadStates[item.id] ?: DownloadState.None,
                            showDownloadBadges = showDownloadBadges,
                            onOpenSeries = onOpenSeries,
                        )
                    }
                }
            }
        }
    }
}

/** The one series to pick up next: a large cover, how far along it is, and a Resume button. */
@Composable
private fun ResumeCard(
    series: Series,
    config: AppConfig,
    downloadState: DownloadState,
    onOpenSeries: (Int) -> Unit,
) {
    // Without the server only downloaded series can be opened.
    val unavailable = rememberServerConnection().isUnavailable && downloadState == DownloadState.None
    val progress = seriesProgress(series.pagesRead, series.pages)
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .alpha(if (unavailable) UNAVAILABLE_ALPHA else 1f),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = einkBorder(),
        onClick = { onOpenSeries(series.id) },
        enabled = !unavailable,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.width(RESUME_COVER_WIDTH)) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalContext.current)
                            .data(seriesCoverUrl(config, series.id))
                            .build(),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(COVER_ASPECT_RATIO)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .einkOutline(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_continue_reading),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = series.name.ifBlank { "Series ${series.id}" },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (progress != null) {
                    StrongProgressBar(progress = progress)
                    Text(
                        text = stringResource(R.string.home_percent_read, (progress * PERCENT).toInt()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Button(onClick = { onOpenSeries(series.id) }, enabled = !unavailable) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.home_resume))
                }
            }
        }
    }
}

/** Grey shapes in the layout of the real page, shimmering while Home loads. */
@Composable
private fun HomeSkeleton() {
    val progress = rememberShimmerProgress(enabled = true)
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ShimmerBox(modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large), height = 190.dp, progress = progress)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerBox(modifier = Modifier.width(120.dp).clip(RoundedCornerShape(4.dp)), height = 16.dp, progress = progress)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(SKELETON_COVERS) {
                    ShimmerBox(
                        modifier = Modifier.width(ROW_COVER_WIDTH).aspectRatio(COVER_ASPECT_RATIO).clip(MaterialTheme.shapes.medium),
                        progress = progress,
                    )
                }
            }
        }
    }
}

private val ROW_COVER_WIDTH = 112.dp
private const val SKELETON_COVERS = 3
private val RESUME_COVER_WIDTH = 128.dp
private const val UNAVAILABLE_ALPHA = 0.4f
private const val PERCENT = 100
