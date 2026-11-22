package com.iquintero.kavern.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.iquintero.kavern.R
import com.iquintero.kavern.core.cache.CacheManager
import com.iquintero.kavern.core.storage.AppConfig
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.domain.repository.CollectionsRepository
import com.iquintero.kavern.domain.repository.LibraryRepository
import com.iquintero.kavern.domain.repository.PersonRepository
import com.iquintero.kavern.domain.repository.ReadingListRepository
import com.iquintero.kavern.domain.repository.SeriesRepository
import com.iquintero.kavern.ui.common.DownloadState
import com.iquintero.kavern.ui.common.DownloadStateBadge
import com.iquintero.kavern.ui.common.ServerConnectionIndicator
import com.iquintero.kavern.ui.common.collectionCoverUrl
import com.iquintero.kavern.ui.common.personCoverUrl
import com.iquintero.kavern.ui.common.readingListCoverUrl
import com.iquintero.kavern.ui.common.rememberServerConnection
import com.iquintero.kavern.ui.common.seriesCoverUrl
import com.iquintero.kavern.ui.library.seriesProgress
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.grid.items as gridItems

/** Smallest width of a cover cell; the grid fits as many columns as the window allows. */
val COVER_MIN_WIDTH = 110.dp
private const val UNAVAILABLE_ALPHA = 0.4f
private val UNAVAILABLE_BADGE_SIZE = 40.dp
private val UNAVAILABLE_BADGE_PADDING = 8.dp

/** Book covers are close to 2:3. */
const val COVER_ASPECT_RATIO = 2f / 3f

/** A thin bar along the bottom of a cover for books that are started but not finished. */
@Composable
fun CoverProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    if (progress == null || progress <= 0f || progress >= 1f) return
    LinearProgressIndicator(
        progress = { progress },
        modifier =
            modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(MaterialTheme.shapes.medium.copy(topStart = CornerSize(0.dp), topEnd = CornerSize(0.dp))),
        trackColor = Color.Black.copy(alpha = 0.45f),
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
fun SeriesCoverCard(
    series: com.iquintero.kavern.domain.model.Series,
    config: AppConfig,
    downloadState: DownloadState,
    showDownloadBadges: Boolean,
    onOpenSeries: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageUrl = seriesCoverUrl(config, series.id)
    // Without the server only downloaded series can be opened, so fade out the rest.
    val unavailable = rememberServerConnection().isUnavailable && downloadState == DownloadState.None
    val coverAlpha = if (unavailable) UNAVAILABLE_ALPHA else 1f
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(enabled = !unavailable) { onOpenSeries(series.id) },
    ) {
        Box {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(COVER_ASPECT_RATIO)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .alpha(coverAlpha),
                contentScale = ContentScale.Crop,
            )
            if (unavailable) {
                // A solid badge stays readable on e-ink screens where the fade alone is subtle.
                Icon(
                    imageVector = Icons.Filled.CloudOff,
                    contentDescription = stringResource(R.string.server_status_unreachable),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(UNAVAILABLE_BADGE_SIZE)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .padding(UNAVAILABLE_BADGE_PADDING),
                )
            }
            CoverProgressBar(
                progress = seriesProgress(series.pagesRead, series.pages),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            if (showDownloadBadges) {
                DownloadStateBadge(
                    state = downloadState,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 6.dp, bottom = 10.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = series.name.ifBlank { "Series ${series.id}" },
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(coverAlpha),
        )
    }
}
