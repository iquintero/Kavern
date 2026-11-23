package com.iquintero.kavern.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.iquintero.kavern.R
import com.iquintero.kavern.ui.common.COVER_ASPECT_RATIO
import com.iquintero.kavern.ui.common.COVER_MIN_WIDTH
import com.iquintero.kavern.ui.common.PagedBox
import com.iquintero.kavern.ui.common.ShimmerBox
import com.iquintero.kavern.ui.common.pageScroller
import com.iquintero.kavern.ui.common.rememberShimmerProgress
import com.iquintero.kavern.ui.theme.LocalEinkMode
import com.iquintero.kavern.ui.theme.einkOutline

private val AvatarSize = 88.dp
private val BACK_BUTTON_SIZE = 48.dp
private val NEAR_TOP_PADDING = 48.dp
private val SearchBarCorner = 28.dp
private val FilterButtonSize = 52.dp

/** Padding shared by every grid in the Library, so the tabs line up when you switch between them. */
val LibraryGridPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)

/** A pill-shaped search field with the filter button beside it. The badge shows how many filters are on. */
@Composable
fun SearchAndFilterBar(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    filterCount: Int,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).einkOutline(RoundedCornerShape(SearchBarCorner)),
            placeholder = { Text(stringResource(R.string.general_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.text.isNotBlank()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.general_clear))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(SearchBarCorner),
            colors =
                TextFieldDefaults.colors(
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    errorIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )
        val filterDescription = stringResource(R.string.general_filter)
        // Selection is inverted black on e-ink, which would read as "pressed"; an outlined button reads as a button.
        val filterButtonColors =
            if (LocalEinkMode.current) {
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                IconButtonDefaults.filledTonalIconButtonColors()
            }
        FilledTonalIconButton(
            onClick = onOpenFilters,
            modifier = Modifier.size(FilterButtonSize).einkOutline(CircleShape),
            colors = filterButtonColors,
        ) {
            BadgedBox(
                badge = {
                    if (filterCount > 0) {
                        Badge { Text(filterCount.toString()) }
                    }
                },
            ) {
                Icon(Icons.Filled.Tune, contentDescription = filterDescription)
            }
        }
    }
}

/**
 * The loading, error and empty states every Library tab shares, so none of them is a blank screen or a
 * bare spinner. [content] only runs once there is something to show.
 */
@Composable
fun LibraryStateLayout(
    isLoading: Boolean,
    isEmpty: Boolean,
    error: String?,
    emptyIcon: ImageVector,
    emptyText: String,
    onRetry: () -> Unit,
    skeleton: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    when {
        isLoading && isEmpty -> skeleton()
        error != null && isEmpty -> MessageState(icon = Icons.Filled.CloudOff, text = error, actionLabel = stringResource(R.string.download_action_retry), onAction = onRetry)
        isEmpty -> MessageState(icon = emptyIcon, text = emptyText)
        else -> content()
    }
}

/** A centered icon and sentence, with an optional button underneath. */
@Composable
fun MessageState(
    icon: ImageVector,
    text: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    nearTop: Boolean = false,
) {
    // Near the top keeps the message above the keyboard when it is open, as it is while searching.
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp).padding(top = if (nearTop) NEAR_TOP_PADDING else 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (nearTop) Arrangement.Top else Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) {
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Grey placeholder tiles that shimmer while a tab loads. [round] makes them circles, for people. */
@Composable
fun SkeletonGrid(
    round: Boolean = false,
    count: Int = SKELETON_COUNT,
) {
    val progress = rememberShimmerProgress(enabled = true)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = COVER_MIN_WIDTH),
        modifier = Modifier.fillMaxSize(),
        contentPadding = LibraryGridPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        repeat(count) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (round) {
                        ShimmerBox(modifier = Modifier.size(AvatarSize).clip(CircleShape), progress = progress)
                    } else {
                        ShimmerBox(
                            modifier = Modifier.fillMaxWidth().aspectRatio(COVER_ASPECT_RATIO).clip(MaterialTheme.shapes.medium),
                            progress = progress,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ShimmerBox(
                        modifier = Modifier.fillMaxWidth(if (round) 0.6f else 0.85f).clip(RoundedCornerShape(4.dp)),
                        height = 12.dp,
                        progress = progress,
                    )
                }
            }
        }
    }
}

private const val SKELETON_COUNT = 12

/** The grid every Library tab uses: as many columns as fit, with the same spacing everywhere. */
@Composable
fun LibraryGrid(
    state: LazyGridState = rememberLazyGridState(),
    content: LazyGridScope.() -> Unit,
) {
    PagedBox(state.pageScroller()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = COVER_MIN_WIDTH),
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = LibraryGridPadding,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** A cover with a title and an optional second line, used for collections and reading lists. */
@Composable
fun CoverTile(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(COVER_ASPECT_RATIO)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .einkOutline(MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(32.dp),
            )
            if (imageUrl != null) {
                SubcomposeAsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalContext.current)
                            .data(imageUrl)
                            .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                ) {
                    // When there is no cover the book icon behind the image stays visible.
                    when (painter.state) {
                        is AsyncImagePainter.State.Error -> Unit
                        else -> SubcomposeAsyncImageContent()
                    }
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** A round portrait with the name centered underneath. */
@Composable
fun PersonTile(
    name: String,
    imageUrl: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(AvatarSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .einkOutline(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(40.dp),
            )
            if (imageUrl != null) {
                SubcomposeAsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalContext.current)
                            .data(imageUrl)
                            .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                ) {
                    when (painter.state) {
                        is AsyncImagePainter.State.Error -> Unit
                        else -> SubcomposeAsyncImageContent()
                    }
                }
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Text for how many items a reading list holds, or null when the server did not say. */
@Composable
fun itemCountText(count: Int?): String? = count?.takeIf { it >= 0 }?.let { pluralStringResource(R.plurals.library_item_count, it, it) }

/** Back arrow and title shown above the series of an opened collection. */
@Composable
fun CollectionHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(BACK_BUTTON_SIZE)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.library_back_description),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
