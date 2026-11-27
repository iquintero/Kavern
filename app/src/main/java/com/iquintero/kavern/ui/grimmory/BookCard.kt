package com.iquintero.kavern.ui.grimmory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.iquintero.kavern.core.network.grimmory.GrimmoryBook
import com.iquintero.kavern.ui.common.COVER_ASPECT_RATIO
import com.iquintero.kavern.ui.common.CoverProgressBar
import com.iquintero.kavern.ui.common.DownloadState
import com.iquintero.kavern.ui.common.DownloadStateBadge
import com.iquintero.kavern.ui.theme.einkOutline

/** A book's cover, with how far along it is and whether it is on the device. */
@Composable
internal fun BookCover(
    book: GrimmoryBook,
    coverUrl: String,
    downloadState: DownloadState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(coverUrl).build(),
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
        CoverProgressBar(progress = book.progress / WHOLE, modifier = Modifier.align(Alignment.BottomCenter))
        DownloadStateBadge(
            state = downloadState,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 10.dp),
        )
    }
}

/** A book in a list or a grid: its cover, its title, and who wrote it. */
@Composable
internal fun BookCard(
    book: GrimmoryBook,
    coverUrl: String,
    downloadState: DownloadState,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().clickable { onOpen(book.id) }) {
        BookCover(book, coverUrl, downloadState)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        book.authors.firstOrNull()?.let { author ->
            Text(
                text = author.asName(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Grimmory keeps authors as "Last, First"; people read "First Last". */
internal fun String.asName(): String {
    val parts = split(",").map { it.trim() }
    return if (parts.size == 2 && parts.all { it.isNotEmpty() }) "${parts[1]} ${parts[0]}" else trim()
}

internal const val WHOLE = 100f
