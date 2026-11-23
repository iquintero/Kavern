package com.iquintero.kavern.ui.library

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iquintero.kavern.R
import com.iquintero.kavern.core.cache.CacheManager
import com.iquintero.kavern.core.storage.AppConfig
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.domain.model.Library
import com.iquintero.kavern.domain.model.Series
import com.iquintero.kavern.domain.model.filter.TriState
import com.iquintero.kavern.domain.repository.CollectionsRepository
import com.iquintero.kavern.domain.repository.LibraryRepository
import com.iquintero.kavern.domain.repository.PersonRepository
import com.iquintero.kavern.domain.repository.ReadingListRepository
import com.iquintero.kavern.domain.repository.SeriesRepository
import com.iquintero.kavern.ui.browse.BrowseScreen
import com.iquintero.kavern.ui.browse.BrowseViewModel
import com.iquintero.kavern.ui.common.DownloadState
import com.iquintero.kavern.ui.common.SeriesCoverCard
import com.iquintero.kavern.ui.common.ServerConnectionIndicator
import com.iquintero.kavern.ui.common.collectionCoverUrl
import com.iquintero.kavern.ui.common.personCoverUrl
import com.iquintero.kavern.ui.common.readingListCoverUrl
import com.iquintero.kavern.ui.common.rememberServerConnection
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
        PrimaryScrollableTabRow(selectedTabIndex = uiState.selectedSection.ordinal, edgePadding = 16.dp) {
            LibraryV2Section.entries.forEach { section ->
                Tab(
                    selected = uiState.selectedSection == section,
                    onClick = { viewModel.selectSection(section) },
                    text = { Text(stringResource(section.labelRes)) },
                )
            }
        }
        val retry = { viewModel.selectSection(uiState.selectedSection) }
        when (uiState.selectedSection) {
            LibraryV2Section.Series ->
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
                    libraryChips = {
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
                    },
                )

            LibraryV2Section.Collections ->
                if (uiState.selectedCollectionId != null) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        CollectionHeader(
                            title = uiState.selectedCollectionName ?: stringResource(R.string.library_collection_fallback),
                            onBack = { viewModel.selectCollection(null) },
                        )
                        SeriesCoverGrid(
                            items = uiState.collectionSeries,
                            isLoading = uiState.isCollectionSeriesLoading,
                            error = uiState.collectionSeriesError,
                            config = config,
                            downloadStates = downloadStates,
                            showDownloadBadges = showDownloadBadges,
                            onRetry = {
                                uiState.collections.firstOrNull { it.id == uiState.selectedCollectionId }?.let(viewModel::selectCollection)
                            },
                            onOpenSeries = onOpenSeries,
                        )
                    }
                } else {
                    LibraryStateLayout(
                        isLoading = uiState.isCollectionsLoading,
                        isEmpty = uiState.collections.isEmpty(),
                        error = uiState.collectionsError,
                        emptyIcon = Icons.Filled.CollectionsBookmark,
                        emptyText = stringResource(R.string.library_empty_collections),
                        onRetry = retry,
                        skeleton = { SkeletonGrid() },
                    ) {
                        LibraryGrid {
                            gridItems(uiState.collections, key = { it.id }) { collection ->
                                CoverTile(
                                    title = collection.name.ifBlank { stringResource(R.string.library_collection_fallback) },
                                    imageUrl = collectionCoverUrl(config, collection.id),
                                    onClick = { viewModel.selectCollection(collection) },
                                )
                            }
                        }
                    }
                }

            LibraryV2Section.ReadingLists -> {
                val context = LocalContext.current
                val comingSoon = stringResource(R.string.library_coming_soon)
                LibraryStateLayout(
                    isLoading = uiState.isReadingListsLoading,
                    isEmpty = uiState.readingLists.isEmpty(),
                    error = uiState.readingListsError,
                    emptyIcon = Icons.AutoMirrored.Filled.LibraryBooks,
                    emptyText = stringResource(R.string.library_empty_reading_lists),
                    onRetry = retry,
                    skeleton = { SkeletonGrid() },
                ) {
                    LibraryGrid {
                        gridItems(uiState.readingLists, key = { it.id }) { readingList ->
                            CoverTile(
                                title = readingList.title.ifBlank { stringResource(R.string.library_reading_list_fallback) },
                                subtitle = itemCountText(readingList.itemCount),
                                imageUrl = readingListCoverUrl(config, readingList.id),
                                onClick = { Toast.makeText(context, comingSoon, Toast.LENGTH_SHORT).show() },
                            )
                        }
                    }
                }
            }

            LibraryV2Section.People -> {
                val context = LocalContext.current
                val comingSoon = stringResource(R.string.library_coming_soon)
                val gridState = rememberLazyGridState()
                LaunchedEffect(gridState) {
                    snapshotFlow {
                        val info = gridState.layoutInfo
                        (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
                    }.collect { (lastVisible, total) ->
                        if (total > 0 && lastVisible >= total - LOAD_MORE_THRESHOLD) viewModel.loadMorePeople()
                    }
                }
                LibraryStateLayout(
                    isLoading = uiState.isPeopleLoading,
                    isEmpty = uiState.people.isEmpty(),
                    error = uiState.peopleError,
                    emptyIcon = Icons.Filled.Groups,
                    emptyText = stringResource(R.string.library_empty_people),
                    onRetry = retry,
                    skeleton = { SkeletonGrid(round = true) },
                ) {
                    LibraryGrid(state = gridState) {
                        gridItems(uiState.people, key = { it.id ?: 0 }) { person ->
                            PersonTile(
                                name = person.name?.ifBlank { null } ?: stringResource(R.string.library_person_fallback),
                                imageUrl = person.id?.let { personCoverUrl(config, it) },
                                onClick = { Toast.makeText(context, comingSoon, Toast.LENGTH_SHORT).show() },
                            )
                        }
                        if (uiState.isPeopleLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 6

/** Narrows the series grid to one library. Hidden when there is nothing to choose between. */
@Composable
private fun LibraryPicker(
    libraries: List<Library>,
    selectedLibraryId: Int?,
    onSelect: (Int?) -> Unit,
) {
    if (libraries.size < 2) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
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
                label = { Text(library.name.ifBlank { stringResource(R.string.library_fallback_name, library.id) }) },
            )
        }
    }
}

/** The series of an opened collection, as a cover grid with the same loading and error states as the tabs. */
@Composable
private fun SeriesCoverGrid(
    items: List<Series>,
    isLoading: Boolean,
    error: String?,
    config: AppConfig,
    downloadStates: Map<Int, DownloadState>,
    showDownloadBadges: Boolean,
    onRetry: () -> Unit,
    onOpenSeries: (Int) -> Unit,
) {
    LibraryStateLayout(
        isLoading = isLoading,
        isEmpty = items.isEmpty(),
        error = error,
        emptyIcon = Icons.Filled.CollectionsBookmark,
        emptyText = stringResource(R.string.library_empty_collection_series),
        onRetry = onRetry,
        skeleton = { SkeletonGrid() },
    ) {
        LibraryGrid {
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
