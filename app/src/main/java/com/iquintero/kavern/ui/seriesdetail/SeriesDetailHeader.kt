package com.iquintero.kavern.ui.seriesdetail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.iquintero.kavern.R
import com.iquintero.kavern.ui.theme.LocalEinkMode
import com.iquintero.kavern.ui.theme.einkBorder
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

private val HeroCoverWidth = 124.dp
private const val DISABLED_ALPHA = 0.38f

enum class ReadStatus { Unread, InProgress, Completed }

/**
 * Where a series stands, from how many of its chapters are unread. Counting chapters alone calls a one-chapter book
 * "unread" until the last page, so a book you are halfway through also needs [hasProgress] to be called in progress.
 */
internal fun readStatus(
    unreadCount: Int?,
    totalCount: Int?,
    hasProgress: Boolean?,
): ReadStatus? {
    if (unreadCount == null || totalCount == null) {
        return when (hasProgress) {
            true -> ReadStatus.InProgress
            false -> ReadStatus.Unread
            null -> null
        }
    }
    if (totalCount <= 0) return null
    return when {
        unreadCount <= 0 -> ReadStatus.Completed
        unreadCount >= totalCount -> if (hasProgress == true) ReadStatus.InProgress else ReadStatus.Unread
        else -> ReadStatus.InProgress
    }
}

/** Kavita sends year 0001 or a 1970 epoch for "never", so anything before this is not a real date. */
private const val FIRST_REAL_YEAR = 1971

/** True when [value] is a date that actually happened, not one of Kavita's placeholders such as `0001-01-01`. */
internal fun isRealDate(value: String?): Boolean {
    if (value.isNullOrBlank()) return false
    val year =
        runCatching { OffsetDateTime.parse(value).year }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value).year }.getOrNull()
            ?: runCatching { Instant.parse(value).atZone(ZoneId.systemDefault()).year }.getOrNull()
            ?: return false
    return year >= FIRST_REAL_YEAR
}

/** Below this many hours there is nothing meaningful left to read, so no time is shown. */
private const val MIN_HOURS_LEFT = 0.05f

/**
 * Hours to show next to "to read": what is left when the server knows it and it is worth mentioning, otherwise
 * the whole series when no progress figure exists at all. Null once the series is essentially finished.
 */
internal fun hoursLeftToRead(
    left: Float?,
    total: Float?,
): Float? =
    when {
        left == null -> total?.takeIf { it > MIN_HOURS_LEFT }
        left > MIN_HOURS_LEFT -> left
        else -> null
    }

/**
 * The short facts shown as chips under a series title, in order, leaving out anything unknown. The old page printed
 * a line for each of these even when it had nothing to say ("Release year: 0", "Words: -").
 */
internal fun seriesFacts(
    readStatus: String?,
    publicationStatus: String?,
    releaseYear: Int?,
    timeToRead: String?,
    wordCount: Long?,
    wordsLabel: (String) -> String,
): List<String> =
    listOfNotNull(
        readStatus?.takeIf { it.isNotBlank() },
        publicationStatus?.takeIf { it.isNotBlank() },
        releaseYear?.takeIf { it > 0 }?.toString(),
        timeToRead?.takeIf { it.isNotBlank() },
        wordCount?.takeIf { it > 0 }?.let { wordsLabel(formatCount(it)) },
    )

/** Cover, title, author and a row of fact chips: the top of the series page. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun SeriesHero(
    title: String,
    author: String?,
    coverUrl: String?,
    facts: List<String>,
    footnote: String?,
    onCoverClick: () -> Unit,
    onTitleLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            // A shadow is a gray gradient, which e-ink panels dither; the outline does the job there.
            shadowElevation = if (LocalEinkMode.current) 0.dp else 3.dp,
            border = einkBorder(),
            modifier = Modifier.width(HeroCoverWidth).aspectRatio(COVER_RATIO),
            onClick = onCoverClick,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(32.dp),
                )
                if (coverUrl != null) {
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(LocalContext.current)
                                .data(coverUrl)
                                .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onTitleLongClick),
            )
            if (!author.isNullOrBlank()) {
                Text(
                    text = author,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (facts.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    facts.forEach { fact -> FactChip(fact) }
                }
            }
            if (!footnote.isNullOrBlank()) {
                Text(
                    text = footnote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun FactChip(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = einkBorder(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/** Want to read, collections, open on the web and share, as four equal icon-over-label tiles. */
@Composable
internal fun SeriesActionTiles(
    wantToRead: Boolean,
    wantToReadEnabled: Boolean,
    collectionsEnabled: Boolean,
    onToggleWant: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenWeb: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionTile(
            icon = if (wantToRead) Icons.Filled.Star else Icons.Filled.StarBorder,
            label = stringResource(R.string.general_want_to_read),
            highlighted = wantToRead,
            enabled = wantToReadEnabled,
            onClick = onToggleWant,
            modifier = Modifier.weight(1f),
        )
        ActionTile(
            icon = Icons.Filled.SwapHoriz,
            label = stringResource(R.string.general_collections),
            enabled = collectionsEnabled,
            onClick = onOpenCollections,
            modifier = Modifier.weight(1f),
        )
        ActionTile(
            icon = Icons.Filled.Public,
            label = stringResource(R.string.general_open_in_browser),
            onClick = onOpenWeb,
            modifier = Modifier.weight(1f),
        )
        ActionTile(
            icon = Icons.Filled.Share,
            label = stringResource(R.string.general_share),
            onClick = onShare,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    enabled: Boolean = true,
) {
    Column(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.medium)
                .clickable(enabled = enabled, onClick = onClick)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = einkBorder(),
            modifier = Modifier.size(width = 56.dp, height = 36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.heightIn(min = 14.dp),
        )
    }
}

private const val COVER_RATIO = 2f / 3f
