package com.iquintero.kavern.ui.library

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
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
import com.iquintero.kavern.domain.model.Library
import com.iquintero.kavern.domain.model.filter.TriState
import com.iquintero.kavern.domain.repository.CollectionsRepository
import com.iquintero.kavern.domain.repository.LibraryRepository
import com.iquintero.kavern.domain.repository.PersonRepository
import com.iquintero.kavern.domain.repository.ReadingListRepository
import com.iquintero.kavern.domain.repository.SeriesRepository
import com.iquintero.kavern.ui.browse.BrowseScreen
import com.iquintero.kavern.ui.browse.BrowseViewModel
import com.iquintero.kavern.ui.common.COVER_ASPECT_RATIO
import com.iquintero.kavern.ui.common.COVER_MIN_WIDTH
import com.iquintero.kavern.ui.common.CoverProgressBar
import com.iquintero.kavern.ui.common.DownloadState
import com.iquintero.kavern.ui.common.DownloadStateBadge
import com.iquintero.kavern.ui.common.SeriesCoverCard
import com.iquintero.kavern.ui.common.ServerConnectionIndicator
import com.iquintero.kavern.ui.common.collectionCoverUrl
import com.iquintero.kavern.ui.common.personCoverUrl
import com.iquintero.kavern.ui.common.readingListCoverUrl
import com.iquintero.kavern.ui.common.rememberServerConnection
import com.iquintero.kavern.ui.common.seriesCoverUrl
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as rowItems

@Composable
fun LibraryV2Screen(
    libraryRepository: LibraryRepository,
    seriesRepository: SeriesRepository,
    collectionsRepository: CollectionsRepository,
    readingListRepository: ReadingListRepository,
    personRepository: PersonRepository,
    cacheManager: CacheManager,
    appPreferences: AppPreferences,
    onOpenSeries: (Int) -> Unit,
    onOpenSettings: () -> Unit = {},
    initialCollectionId: Int? = null,
    initialCollectionName: String? = null,
    initialGenreId: Int? = null,
    initialGenreName: String? = null,
    initialTagId: Int? = null,
    initialTagName: String? = null,
) {
    val viewModel: LibraryV2ViewModel =
        viewModel(
            factory =
                LibraryV2ViewModel.provideFactory(
                    libraryRepository,
                    seriesRepository,
                    collectionsRepository,
                    readingListRepository,
                    personRepository,
                    cacheManager,
                    appPreferences,
                ),
        )
    val browseViewModel: BrowseViewModel =
        viewModel(factory = BrowseViewModel.provideFactory(seriesRepository, appPreferences, cacheManager))
    val uiState by viewModel.state.collectAsState()
    val browseState by browseViewModel.state.collectAsState()
    var presetApplied by remember { mutableStateOf(false) }

    LaunchedEffect(initialCollectionId) {
        if (presetApplied) return@LaunchedEffect
        if (initialCollectionId != null) {
            viewModel.openCollectionFromExternal(initialCollectionId, initialCollectionName)
            presetApplied = true
        }
    }
    val config by appPreferences.configFlow.collectAsState(
        initial = AppConfig(serverUrl = "", apiKey = "", imageApiKey = "", userId = 0),
    )
    val showDownloadBadges by appPreferences.showDownloadBadgesFlow.collectAsState(initial = true)
    val downloadStates = uiState.downloadStates
    if (uiState.selectedCollectionId != null) {
        BackHandler { viewModel.handleBack() }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = stringResource(R.string.nav_library), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            // The app Scaffold already pads for the status bar.
            windowInsets = WindowInsets(0),
            actions = {
                ServerConnectionIndicator(
                    connection = rememberServerConnection(),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings))
                }
            },
        )
        PrimaryScrollableTabRow(selectedTabIndex = uiState.selectedSection.ordinal, edgePadding = 0.dp) {
            LibraryV2Section.entries.forEach { section ->
                Tab(
                    selected = uiState.selectedSection == section,
                    onClick = { viewModel.selectSection(section) },
                    text = { Text(stringResource(section.labelRes)) },
                )
            }
        }
        when (uiState.selectedSection) {
            LibraryV2Section.Series -> {
                LibraryPicker(
                    libraries = uiState.libraries,
                    selectedLibraryId =
                        browseState.appliedFilter.libraries.entries
                            .firstOrNull { it.value == TriState.Include }
                            ?.key,
                    onSelect = { libraryId ->
                        browseViewModel.updateDraftFilter {
                            it.copy(libraries = if (libraryId == null) emptyMap() else mapOf(libraryId to TriState.Include))
                        }
                        browseViewModel.applyFilterAndReload()
                    },
                )
                BrowseScreen(
                    seriesRepository = seriesRepository,
                    appPreferences = appPreferences,
                    cacheManager = cacheManager,
                    onOpenSeries = onOpenSeries,
                    initialGenreId = initialGenreId,
                    initialGenreName = initialGenreName,
                    initialTagId = initialTagId,
                    initialTagName = initialTagName,
                    viewModel = browseViewModel,
                )
            }

            LibraryV2Section.Collections -> {
                if (uiState.selectedCollectionId != null) {
                    CollectionSeriesGrid(
                        title = uiState.selectedCollectionName ?: "Collection",
                        items = uiState.collectionSeries,
                        isLoading = uiState.isCollectionSeriesLoading,
                        error = uiState.collectionSeriesError,
                        config = config,
                        downloadStates = downloadStates,
                        showDownloadBadges = showDownloadBadges,
                        onBack = { viewModel.selectCollection(null) },
                        onOpenSeries = onOpenSeries,
                    )
                } else {
                    CollectionsGrid(
                        items = uiState.collections,
                        isLoading = uiState.isCollectionsLoading,
                        error = uiState.collectionsError,
                        config = config,
                        onSelect = { collection -> viewModel.selectCollection(collection) },
                    )
                }
            }

            LibraryV2Section.ReadingLists ->
                ReadingListGrid(
                    items = uiState.readingLists,
                    isLoading = uiState.isReadingListsLoading,
                    error = uiState.readingListsError,
                    config = config,
                )

            LibraryV2Section.People ->
                PeopleGrid(
                    items = uiState.people,
                    isLoading = uiState.isPeopleLoading,
                    isLoadingMore = uiState.isPeopleLoadingMore,
                    error = uiState.peopleError,
                    config = config,
                    onLoadMore = { viewModel.loadMorePeople() },
                )
        }
    }
}

/** Narrows the series grid to one library. Hidden when there is nothing to choose between. */
@Composable
private fun LibraryPicker(
    libraries: List<Library>,
    selectedLibraryId: Int?,
    onSelect: (Int?) -> Unit,
) {
    if (libraries.size < 2) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            FilterChip(
                selected = selectedLibraryId == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.library_all_libraries)) },
            )
        }
        rowItems(libraries, key = { it.id }) { library ->
            FilterChip(
                selected = selectedLibraryId == library.id,
                onClick = { onSelect(library.id) },
                label = { Text(library.name.ifBlank { "Library ${library.id}" }) },
            )
        }
    }
}

@Composable
private fun SeriesCoverGrid(
    items: List<com.iquintero.kavern.domain.model.Series>,
    isLoading: Boolean,
    error: String?,
    config: AppConfig,
    downloadStates: Map<Int, DownloadState>,
    showDownloadBadges: Boolean,
    onOpenSeries: (Int) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading && items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            error != null && items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.library_empty_reading_list),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = COVER_MIN_WIDTH),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(items, key = { it.id }) { series ->
                        SeriesCoverCard(
                            series = series,
                            config = config,
                            downloadState = downloadStates[series.id] ?: DownloadState.None,
                            showDownloadBadges = showDownloadBadges,
                            onOpenSeries = onOpenSeries,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionsGrid(
    items: List<com.iquintero.kavern.domain.model.Collection>,
    isLoading: Boolean,
    error: String?,
    config: AppConfig,
    onSelect: (com.iquintero.kavern.domain.model.Collection) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading && items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            error != null && items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.library_empty_reading_list),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = COVER_MIN_WIDTH),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(items, key = { it.id }) { collection ->
                        CollectionCard(
                            collection = collection,
                            config = config,
                            onSelect = onSelect,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionCard(
    collection: com.iquintero.kavern.domain.model.Collection,
    config: AppConfig,
    onSelect: (com.iquintero.kavern.domain.model.Collection) -> Unit,
) {
    val imageUrl = collectionCoverUrl(config, collection.id)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onSelect(collection) },
    ) {
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
                    .height(160.dp)
                    .clip(
                        androidx.compose.foundation.shape
                            .RoundedCornerShape(8.dp),
                    ),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = collection.name.ifBlank { "Collection ${collection.id}" },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CollectionSeriesGrid(
    title: String,
    items: List<com.iquintero.kavern.domain.model.Series>,
    isLoading: Boolean,
    error: String?,
    config: AppConfig,
    downloadStates: Map<Int, DownloadState>,
    showDownloadBadges: Boolean,
    onBack: () -> Unit,
    onOpenSeries: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Back",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onBack() },
            )
        }
        SeriesCoverGrid(
            items = items,
            isLoading = isLoading,
            error = error,
            config = config,
            downloadStates = downloadStates,
            showDownloadBadges = showDownloadBadges,
            onOpenSeries = onOpenSeries,
        )
    }
}

@Composable
private fun ReadingListGrid(
    items: List<com.iquintero.kavern.domain.model.ReadingList>,
    isLoading: Boolean,
    error: String?,
    config: AppConfig,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading && items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            error != null && items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.library_empty_reading_list),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = COVER_MIN_WIDTH),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(items, key = { it.id }) { readingList ->
                        ReadingListCard(
                            readingList = readingList,
                            config = config,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingListCard(
    readingList: com.iquintero.kavern.domain.model.ReadingList,
    config: AppConfig,
) {
    val context = LocalContext.current
    val imageUrl = readingListCoverUrl(config, readingList.id)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    android.widget.Toast
                        .makeText(context, "Not implemented yet", android.widget.Toast.LENGTH_SHORT)
                        .show()
                },
    ) {
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
                    .height(160.dp)
                    .clip(
                        androidx.compose.foundation.shape
                            .RoundedCornerShape(8.dp),
                    ),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = readingList.title.ifBlank { "Reading List ${readingList.id}" },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PeopleGrid(
    items: List<com.iquintero.kavern.domain.model.Person>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    config: AppConfig,
    onLoadMore: () -> Unit,
) {
    val context = LocalContext.current
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            lastVisible to total
        }.collect { (lastVisible, total) ->
            if (total > 0 && lastVisible >= total - 6) {
                onLoadMore()
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading && items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            error != null && items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No people found.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = COVER_MIN_WIDTH),
                    modifier = Modifier.fillMaxSize(),
                    state = gridState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(items, key = { it.id ?: 0 }) { person ->
                        PersonCard(
                            person = person,
                            config = config,
                            onClick = {
                                android.widget.Toast
                                    .makeText(context, "Not implemented yet", android.widget.Toast.LENGTH_SHORT)
                                    .show()
                            },
                        )
                    }
                    if (isLoadingMore) {
                        item(span = {
                            GridItemSpan(maxLineSpan)
                        }) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonCard(
    person: com.iquintero.kavern.domain.model.Person,
    config: AppConfig,
    onClick: () -> Unit,
) {
    val imageUrl = person.id?.let { personCoverUrl(config, it) }
    val imageModifier =
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() },
    ) {
        if (imageUrl == null) {
            PersonPlaceholder(modifier = imageModifier)
        } else {
            SubcomposeAsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = imageModifier,
                loading = { PersonPlaceholder(modifier = imageModifier) },
                error = { PersonPlaceholder(modifier = imageModifier) },
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = person.name?.ifBlank { null } ?: "Person ${person.id ?: 0}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PersonPlaceholder(modifier: Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
    }
}
