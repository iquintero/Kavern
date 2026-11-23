package com.iquintero.kavern.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.iquintero.kavern.R
import com.iquintero.kavern.core.storage.AppConfig
import com.iquintero.kavern.ui.common.DownloadState
import com.iquintero.kavern.ui.common.DownloadStateBadge
import com.iquintero.kavern.ui.common.rememberServerConnection
import com.iquintero.kavern.ui.common.seriesCoverUrl
import com.iquintero.kavern.ui.library.seriesProgress

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
    // Over a cover the bar needs its own contrast: a white fill on a dark track reads on any image and on grayscale.
    LinearProgressIndicator(
        progress = { progress },
        modifier =
            modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(MaterialTheme.shapes.medium.copy(topStart = CornerSize(0.dp), topEnd = CornerSize(0.dp))),
        color = Color.White,
        trackColor = Color.Black.copy(alpha = 0.7f),
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
