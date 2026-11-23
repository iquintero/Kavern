package com.iquintero.kavern.ui.browse

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.iquintero.kavern.R
import com.iquintero.kavern.core.cache.CacheManager
import com.iquintero.kavern.core.storage.AppConfig
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.domain.model.ReadState
import com.iquintero.kavern.domain.model.Series
import com.iquintero.kavern.domain.model.filter.KavitaCombination
import com.iquintero.kavern.domain.model.filter.KavitaSortField
import com.iquintero.kavern.domain.model.filter.TriState
import com.iquintero.kavern.domain.repository.SeriesRepository
import com.iquintero.kavern.domain.usecase.AppliedFilter
import com.iquintero.kavern.domain.usecase.ReadStatusFilter
import com.iquintero.kavern.domain.usecase.SpecialFilter
import com.iquintero.kavern.ui.browse.utils.AgeRatings
import com.iquintero.kavern.ui.browse.utils.PublicationState
import com.iquintero.kavern.ui.common.COVER_MIN_WIDTH
import com.iquintero.kavern.ui.common.CoverProgressBar
import com.iquintero.kavern.ui.common.DownloadState
import com.iquintero.kavern.ui.common.DownloadStateBadge
import com.iquintero.kavern.ui.common.ShimmerBox
import com.iquintero.kavern.ui.common.ShimmerBoxAnimated
import com.iquintero.kavern.ui.common.rememberShimmerProgress
import com.iquintero.kavern.ui.library.LibraryGridPadding
import com.iquintero.kavern.ui.library.MessageState
import com.iquintero.kavern.ui.library.SearchAndFilterBar
import com.iquintero.kavern.ui.library.activeFilterCount
import com.iquintero.kavern.ui.library.seriesProgress
import com.iquintero.kavern.ui.theme.LocalEinkMode
import com.iquintero.kavern.ui.theme.einkOutline
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    seriesRepository: SeriesRepository,
    appPreferences: AppPreferences,
    cacheManager: CacheManager,
    onOpenSeries: (Int) -> Unit,
    initialGenreId: Int? = null,
    initialGenreName: String? = null,
    initialTagId: Int? = null,
    initialTagName: String? = null,
    libraryChips: @Composable () -> Unit = {},
    viewModel: BrowseViewModel =
        viewModel(
            factory = BrowseViewModel.provideFactory(seriesRepository, appPreferences, cacheManager),
        ),
) {
    val uiState by viewModel.state.collectAsState()
    var presetApplied by remember { mutableStateOf(false) }

    var showFilterSheet by remember { mutableStateOf(false) }
    var sheetHasFocus by remember { mutableStateOf(false) }
    val specialOptions = listOf(SpecialFilter.WantToRead)
    val ageRatingsList =
        AgeRatings.entries.map {
            it.code to stringResource(it.titleRes)
        }
    val publicationItems =
        PublicationState.entries.map {
            it.code to stringResource(it.titleRes)
        }
    val gridState = rememberLazyGridState()
    var hideSearch by remember { mutableStateOf(false) }
    var lastScrollValue by remember { mutableStateOf(0) }

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val config by appPreferences.configFlow.collectAsState(
        initial = AppConfig(serverUrl = "", apiKey = "", imageApiKey = "", userId = 0),
    )
    val showDownloadBadges by appPreferences.showDownloadBadgesFlow.collectAsState(initial = true)
    val browsePageSize by appPreferences.browsePageSizeFlow.collectAsState(initial = 25)
    val disableBrowseThumbnails by appPreferences.disableBrowseThumbnailsFlow.collectAsState(initial = false)
    val context = LocalContext.current

    LaunchedEffect(initialGenreId, initialTagId) {
        if (presetApplied) return@LaunchedEffect
        when {
            initialGenreId != null -> {
                viewModel.applyQuickGenreFilter(initialGenreId, initialGenreName)
                Toast
                    .makeText(
                        context,
                        context.getString(
                            R.string.browse_filter_applied_genre,
                            initialGenreName ?: initialGenreId.toString(),
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
            }
            initialTagId != null -> {
                viewModel.applyQuickTagFilter(initialTagId, initialTagName)
                Toast
                    .makeText(
                        context,
                        context.getString(
                            R.string.browse_filter_applied_tag,
                            initialTagName ?: initialTagId.toString(),
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        }
        presetApplied = initialGenreId != null || initialTagId != null
    }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val value = index * 10000 + offset
                val delta = value - lastScrollValue
                lastScrollValue = value
                hideSearch =
                    when {
                        index == 0 && offset < 20 -> false
                        delta > 10 -> true
                        delta < -10 -> false
                        else -> hideSearch
                    }
            }
    }

    LaunchedEffect(showFilterSheet) {
        if (showFilterSheet) {
            viewModel.loadMetadataIfNeeded(
                uiState.draftFilter.libraries.keys
                    .toList(),
            )
        }
    }

    // Initial load handled by ViewModel on init; search/filter changes applied via ViewModel.
    // back handling solved via confirmValueChange in sheet state

    // The search bar, chips and filter sheet stay on screen whatever the results are, so a search or filter
    // that matches nothing can always be changed. Only the area below them swaps between results, a message
    // and the placeholders.
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // On e-ink the bar stays put: hiding it while scrolling would shift the whole grid and redraw it.
            AnimatedVisibility(visible = LocalEinkMode.current || !hideSearch || uiState.series.isEmpty()) {
                Column {
                    SearchAndFilterBar(
                        query = uiState.searchInput,
                        onQueryChange = { viewModel.updateSearch(it) },
                        onSearch = {
                            viewModel.applySearchAndReload()
                            focusManager.clearFocus()
                        },
                        onClear = {
                            viewModel.updateSearch(TextFieldValue(""))
                            viewModel.applySearchAndReload()
                        },
                        filterCount = activeFilterCount(uiState.appliedFilter),
                        onOpenFilters = { showFilterSheet = true },
                    )
                    libraryChips()
                }
            }

            if (showBrowseError(uiState.error, hasItems = uiState.series.isNotEmpty())) {
                MessageState(
                    icon = Icons.Filled.CloudOff,
                    text = uiState.error.orEmpty(),
                    actionLabel = stringResource(R.string.download_action_retry),
                    onAction = { viewModel.reloadFirstPage() },
                )
            } else if (uiState.series.isEmpty() && uiState.isLoading) {
                PlaceholderSeriesGrid(
                    placeholderCount = browsePageSize,
                    gridState = gridState,
                    disableThumbnails = disableBrowseThumbnails,
                )
            } else if (uiState.series.isEmpty()) {
                MessageState(icon = Icons.Filled.SearchOff, text = stringResource(R.string.general_no_results), nearTop = true)
            } else {
                SeriesGrid(
                    seriesList = uiState.series,
                    config = config,
                    isLoadingMore = uiState.isLoadingMore,
                    loadingPlaceholderCount = browsePageSize,
                    gridState = gridState,
                    disableThumbnails = disableBrowseThumbnails,
                    downloadStates = uiState.downloadStates,
                    showDownloadBadges = showDownloadBadges,
                    onSeriesClick = { onOpenSeries(it.id) },
                    onLoadMore = { viewModel.loadNextPage() },
                )
            }
        }

        if (showFilterSheet) {
            var minFocused by remember { mutableStateOf(false) }
            var maxFocused by remember { mutableStateOf(false) }
            val sheetState =
                rememberModalBottomSheetState(
                    skipPartiallyExpanded = true,
                    confirmValueChange = { target ->
                        if (target == SheetValue.Hidden && (minFocused || maxFocused)) {
                            focusManager.clearFocus(force = true)
                            false
                        } else {
                            true
                        }
                    },
                )
            var combinationExpanded by remember { mutableStateOf(false) }
            var sortExpanded by remember { mutableStateOf(true) }
            var statusExpanded by remember { mutableStateOf(true) }
            var yearExpanded by remember { mutableStateOf(false) }
            var genresExpanded by remember { mutableStateOf(false) }
            var tagsExpanded by remember { mutableStateOf(false) }
            var languagesExpanded by remember { mutableStateOf(false) }
            var ageExpanded by remember { mutableStateOf(false) }
            var publicationExpanded by remember { mutableStateOf(false) }
            var collectionsExpanded by remember { mutableStateOf(false) }
            var librariesExpanded by remember { mutableStateOf(false) }
            var specialExpanded by remember { mutableStateOf(false) }
            var smartExpanded by remember { mutableStateOf(false) }

            BackHandler(enabled = showFilterSheet) {
                if (sheetHasFocus || minFocused || maxFocused) {
                    focusManager.clearFocus(force = true)
                } else {
                    showFilterSheet = false
                }
            }

            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                sheetState = sheetState,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.general_filter), style = MaterialTheme.typography.titleLarge)

                        SectionHeader(stringResource(R.string.general_combination), combinationExpanded) {
                            combinationExpanded = !combinationExpanded
                        }
                        if (combinationExpanded) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = uiState.draftFilter.combination == KavitaCombination.MatchAll,
                                    onClick = { viewModel.setCombination(KavitaCombination.MatchAll) },
                                    label = { Text(stringResource(R.string.browse_filter_match_all)) },
                                )
                                FilterChip(
                                    selected = uiState.draftFilter.combination == KavitaCombination.MatchAny,
                                    onClick = { viewModel.setCombination(KavitaCombination.MatchAny) },
                                    label = { Text(stringResource(R.string.browse_filter_match_any)) },
                                )
                            }
                        }

                        SectionHeader(
                            stringResource(R.string.browse_filter_sort_by),
                            sortExpanded,
                            count =
                                if (uiState.draftFilter.sortField != KavitaSortField.SortName ||
                                    uiState.draftFilter.sortDesc
                                ) {
                                    1
                                } else {
                                    0
                                },
                        ) { sortExpanded = !sortExpanded }
                        if (sortExpanded) {
                            val sortOptions =
                                listOf(
                                    stringResource(R.string.browse_filter_sort_name) to KavitaSortField.SortName,
                                    stringResource(R.string.browse_filter_sort_created) to KavitaSortField.Created,
                                    stringResource(R.string.browse_filter_sort_last_modified) to KavitaSortField.LastModified,
                                    stringResource(R.string.browse_filter_sort_item_added) to KavitaSortField.ItemAdded,
                                    stringResource(R.string.browse_filter_sort_time_to_read) to KavitaSortField.TimeToRead,
                                    stringResource(R.string.browse_filter_sort_release_year) to KavitaSortField.ReleaseYear,
                                    stringResource(R.string.browse_filter_sort_last_read) to KavitaSortField.LastRead,
                                    stringResource(R.string.browse_filter_sort_avg_rating) to KavitaSortField.AverageRating,
                                    stringResource(R.string.browse_filter_sort_random) to KavitaSortField.Random,
                                )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                sortOptions.chunked(3).forEach { chunk ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        chunk.forEach { (label, field) ->
                                            SortChip(
                                                label = label,
                                                field = field,
                                                current = uiState.draftFilter.sortField,
                                                descending = uiState.draftFilter.sortDesc,
                                            ) { f, d -> viewModel.setSort(f, d) }
                                        }
                                    }
                                }
                            }
                        }

                        SectionHeader(
                            stringResource(R.string.browse_filter_status),
                            statusExpanded,
                            count =
                                if (uiState.draftFilter.statusFilter !=
                                    ReadStatusFilter.Any
                                ) {
                                    1
                                } else {
                                    0
                                },
                        ) { statusExpanded = !statusExpanded }
                        if (statusExpanded) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                StatusChip(ReadStatusFilter.Any, uiState.draftFilter.statusFilter) { viewModel.setStatusFilter(it) }
                                StatusChip(ReadStatusFilter.Unread, uiState.draftFilter.statusFilter) { viewModel.setStatusFilter(it) }
                                StatusChip(ReadStatusFilter.InProgress, uiState.draftFilter.statusFilter) { viewModel.setStatusFilter(it) }
                                StatusChip(ReadStatusFilter.Completed, uiState.draftFilter.statusFilter) { viewModel.setStatusFilter(it) }
                            }
                        }

                        SectionHeader(
                            stringResource(R.string.browse_filter_release_year),
                            yearExpanded,
                            count =
                                if (uiState.draftFilter.minYear.isNotBlank() ||
                                    uiState.draftFilter.maxYear.isNotBlank()
                                ) {
                                    1
                                } else {
                                    0
                                },
                        ) { yearExpanded = !yearExpanded }
                        if (yearExpanded) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = uiState.draftFilter.minYear,
                                    onValueChange = { viewModel.setYearBounds(it, uiState.draftFilter.maxYear) },
                                    label = { Text(stringResource(R.string.general_min)) },
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .onFocusChanged { focusState ->
                                                minFocused = focusState.isFocused
                                                sheetHasFocus = focusState.isFocused || maxFocused
                                            },
                                )
                                OutlinedTextField(
                                    value = uiState.draftFilter.maxYear,
                                    onValueChange = { viewModel.setYearBounds(uiState.draftFilter.minYear, it) },
                                    label = { Text(stringResource(R.string.general_max)) },
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .onFocusChanged { focusState ->
                                                maxFocused = focusState.isFocused
                                                sheetHasFocus = focusState.isFocused || minFocused
                                            },
                                )
                            }
                        }

                        val genreItems = uiState.genresRemote.map { (it.id ?: 0) to (it.title ?: (it.id ?: 0).toString()) }
                        SectionHeader(stringResource(R.string.general_genres), genresExpanded, count = uiState.draftFilter.genres.size) { genresExpanded = !genresExpanded }
                        if (genresExpanded) {
                            TriStateRow(genreItems, uiState.draftFilter.genres) { id, st ->
                                viewModel.setTriStateGenre(id, st)
                            }
                        }
                        val tagItems = uiState.tagsRemote.map { (it.id ?: 0) to (it.title ?: (it.id ?: 0).toString()) }
                        SectionHeader(stringResource(R.string.general_tags), tagsExpanded, count = uiState.draftFilter.tags.size) { tagsExpanded = !tagsExpanded }
                        if (tagsExpanded) {
                            TriStateRow(tagItems, uiState.draftFilter.tags) { id, st ->
                                viewModel.setTriStateTag(id, st)
                            }
                        }
                        val languageItems = uiState.languagesRemote.map { (it.isoCode) to (it.title ?: it.isoCode) }
                        SectionHeader(stringResource(R.string.general_languages), languagesExpanded, count = uiState.draftFilter.languages.size) {
                            languagesExpanded =
                                !languagesExpanded
                        }
                        if (languagesExpanded) {
                            TriStateRowString(languageItems, uiState.draftFilter.languages) { code, st ->
                                viewModel.setTriStateLanguage(code, st)
                            }
                        }
                        SectionHeader(stringResource(R.string.browse_filter_age_rating), ageExpanded, count = uiState.draftFilter.ageRatings.size) {
                            ageExpanded =
                                !ageExpanded
                        }
                        if (ageExpanded) {
                            TriStateRow(ageRatingsList, uiState.draftFilter.ageRatings) { id, st ->
                                viewModel.setTriStateAge(id, st)
                            }
                        }
                        SectionHeader(
                            stringResource(R.string.general_publication_status),
                            publicationExpanded,
                            count = uiState.draftFilter.publication.count { it.value },
                        ) {
                            publicationExpanded =
                                !publicationExpanded
                        }
                        if (publicationExpanded) {
                            MultiToggleRow(publicationItems, uiState.draftFilter.publication) { id, checked ->
                                viewModel.setPublication(id, checked)
                            }
                        }
                        SectionHeader(stringResource(R.string.general_collections), collectionsExpanded, count = uiState.draftFilter.collections.size) {
                            collectionsExpanded =
                                !collectionsExpanded
                        }
                        if (collectionsExpanded) {
                            val collectionItems = uiState.collectionsRemote.map { it.id to (it.title ?: it.id.toString()) }
                            TriStateRow(collectionItems, uiState.draftFilter.collections) { id, st ->
                                viewModel.setTriStateCollection(id, st)
                            }
                        }

                        SectionHeader(stringResource(R.string.general_libraries), librariesExpanded, count = uiState.draftFilter.libraries.size) {
                            librariesExpanded =
                                !librariesExpanded
                        }
                        if (librariesExpanded) {
                            val libraryItems = uiState.librariesRemote.map { it.id to (it.name ?: it.id.toString()) }
                            TriStateRow(libraryItems, uiState.draftFilter.libraries) { id, st ->
                                viewModel.setTriStateLibrary(id, st)
                            }
                        }

                        HorizontalDivider()
                        Text(stringResource(R.string.browse_filter_filter_no_others))
                        SectionHeader(
                            stringResource(R.string.browse_filter_special_list),
                            specialExpanded,
                            count =
                                if (uiState.draftFilter.special !=
                                    null
                                ) {
                                    1
                                } else {
                                    0
                                },
                        ) { specialExpanded = !specialExpanded }
                        if (specialExpanded) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                specialOptions.forEach { option ->
                                    val label =
                                        when (option) {
                                            SpecialFilter.WantToRead -> stringResource(R.string.general_want_to_read)
                                        }
                                    FilterChip(
                                        selected = uiState.draftFilter.special == option,
                                        onClick = {
                                            viewModel.setSmartFilter(null, null)
                                            viewModel.setSpecial(option)
                                        },
                                        label = { Text(label) },
                                    )
                                }
                                FilterChip(
                                    selected = uiState.draftFilter.special == null,
                                    onClick = { viewModel.setSpecial(null) },
                                    label = { Text(stringResource(R.string.general_none)) },
                                )
                            }
                        }

                        SectionHeader(
                            stringResource(R.string.browse_filter_smart_filters),
                            smartExpanded,
                            count =
                                if (uiState.draftFilter.smartFilterId !=
                                    null
                                ) {
                                    1
                                } else {
                                    0
                                },
                        ) { smartExpanded = !smartExpanded }
                        if (smartExpanded) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val smartItems = uiState.smartFilters.map { it.id to (it.name ?: "${stringResource(R.string.general_filter)} ${it.id}") }
                                smartItems.forEach { (id, name) ->
                                    FilterChip(
                                        selected = uiState.draftFilter.smartFilterId == id,
                                        onClick = { viewModel.onSmartSelected(id) },
                                        label = { Text(name) },
                                    )
                                }
                                FilterChip(
                                    selected = uiState.draftFilter.smartFilterId == null,
                                    onClick = { viewModel.onSmartSelected(null) },
                                    label = { Text(stringResource(R.string.general_none)) },
                                )
                            }
                        }
                    }
                    Row(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.updateDraftFilter { AppliedFilter() }
                                viewModel.applyFilterAndReload()
                                showFilterSheet = false
                            },
                        ) {
                            Text(stringResource(R.string.general_reset))
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showFilterSheet = false
                                scope.launch {
                                    val smartId = uiState.draftFilter.smartFilterId
                                    if (smartId != null && uiState.draftFilter.decodedSmartFilter == null) {
                                        val decoded = viewModel.decodeSmartFilter(smartId)
                                        if (decoded != null) {
                                            viewModel.setSmartFilter(smartId, decoded)
                                        }
                                    }
                                    viewModel.applyFilterAndReload()
                                }
                            },
                        ) {
                            Text(stringResource(R.string.general_filter))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderSeriesGrid(
    placeholderCount: Int,
    gridState: LazyGridState,
    disableThumbnails: Boolean,
) {
    val shimmerProgress = rememberShimmerProgress(enabled = true)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = COVER_MIN_WIDTH),
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        contentPadding = LibraryGridPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(placeholderCount) {
            PlaceholderSeriesTile(
                shimmerProgress = shimmerProgress,
                disableThumbnails = disableThumbnails,
            )
        }
    }
}

@Composable
private fun PlaceholderSeriesTile(
    shimmerProgress: Float,
    disableThumbnails: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (!disableThumbnails) {
            ShimmerBox(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(MaterialTheme.shapes.medium),
                progress = shimmerProgress,
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        ShimmerBox(
            modifier =
                Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(4.dp)),
            height = 12.dp,
            progress = shimmerProgress,
        )
        Spacer(modifier = Modifier.height(4.dp))
        ShimmerBox(
            modifier =
                Modifier
                    .fillMaxWidth(0.6f)
                    .clip(RoundedCornerShape(4.dp)),
            height = 10.dp,
            progress = shimmerProgress,
        )
    }
}

@Composable
private fun SeriesGrid(
    seriesList: List<Series>,
    config: AppConfig,
    isLoadingMore: Boolean,
    loadingPlaceholderCount: Int,
    gridState: LazyGridState,
    disableThumbnails: Boolean,
    downloadStates: Map<Int, DownloadState>,
    showDownloadBadges: Boolean,
    onLoadMore: () -> Unit,
    onSeriesClick: (Series) -> Unit,
) {
    val shimmerProgress = rememberShimmerProgress(enabled = isLoadingMore)
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            lastVisible to total
        }.distinctUntilChanged()
            .collect { (lastVisible, total) ->
                if (lastVisible >= total - 6) {
                    onLoadMore()
                }
            }
    }

    val context = LocalContext.current

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = COVER_MIN_WIDTH),
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        contentPadding = LibraryGridPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(seriesList, key = { it.id }) { series ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSeriesClick(series) },
            ) {
                if (!disableThumbnails) {
                    val imageData = series.localThumbPath?.let { java.io.File(it) } ?: seriesCoverUrl(config, series.id)
                    val imageRequest =
                        remember(imageData) {
                            imageData?.let {
                                ImageRequest
                                    .Builder(context)
                                    .data(it)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .networkCachePolicy(CachePolicy.ENABLED)
                                    .build()
                            }
                        }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .einkOutline(MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center,
                    ) {
                        SubcomposeAsyncImage(
                            model = imageRequest,
                            contentDescription = series.name,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                        ) {
                            when (painter.state) {
                                is coil.compose.AsyncImagePainter.State.Loading -> {
                                    ShimmerBoxAnimated(
                                        modifier = Modifier.matchParentSize(),
                                    )
                                }
                                is coil.compose.AsyncImagePainter.State.Error -> {
                                    Icon(
                                        imageVector = Icons.Filled.ImageNotSupported,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                else -> SubcomposeAsyncImageContent()
                            }
                        }
                        CoverProgressBar(
                            progress = seriesProgress(series.pagesRead, series.pages),
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                        if (showDownloadBadges) {
                            DownloadStateBadge(
                                state = downloadStates[series.id] ?: DownloadState.None,
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 6.dp, bottom = 10.dp),
                            )
                        }
                    }
                }

                Text(
                    series.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
                // Unread is the usual state, so only started and finished series get a label.
                readingStatusLabel(context, series.readState.takeIf { it != ReadState.Unread })?.let { status ->
                    Text(
                        text = status,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                color =
                                    when (series.readState) {
                                        ReadState.Completed -> MaterialTheme.colorScheme.primary
                                        ReadState.InProgress -> MaterialTheme.colorScheme.secondary
                                        ReadState.Unread, null -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            ),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        if (isLoadingMore) {
            items(loadingPlaceholderCount, key = { "loading_$it" }) {
                PlaceholderSeriesTile(
                    shimmerProgress = shimmerProgress,
                    disableThumbnails = disableThumbnails,
                )
            }
        }
    }
}

private fun seriesCoverUrl(
    config: AppConfig,
    seriesId: Int,
): String? {
    if (config.serverUrl.isBlank() || config.apiKey.isBlank()) return null
    val base = if (config.serverUrl.endsWith("/")) config.serverUrl.dropLast(1) else config.serverUrl
    return "$base/api/Image/series-cover?seriesId=$seriesId&apiKey=${config.apiKey}"
}

private fun readingStatusLabel(
    context: Context,
    state: ReadState?,
): String? =
    when (state) {
        ReadState.Completed -> context.resources.getString(R.string.general_reading_status_completed)
        ReadState.InProgress -> context.resources.getString(R.string.general_reading_status_in_progress)
        ReadState.Unread -> context.resources.getString(R.string.general_reading_status_unread)
        null -> null
    }

@Composable
private fun SortChip(
    label: String,
    field: KavitaSortField,
    current: KavitaSortField,
    descending: Boolean,
    onToggle: (KavitaSortField, Boolean) -> Unit,
) {
    val selected = current == field
    val arrow =
        when {
            !selected -> ""
            descending -> "v"
            else -> "^"
        }
    FilterChip(
        selected = selected,
        onClick = {
            if (selected) onToggle(field, !descending) else onToggle(field, false)
        },
        label = { Text("$label $arrow") },
    )
}

@Composable
private fun StatusChip(
    value: ReadStatusFilter,
    current: ReadStatusFilter,
    onSelect: (ReadStatusFilter) -> Unit,
) {
    val label =
        when (value) {
            ReadStatusFilter.Any -> stringResource(R.string.general_any)
            ReadStatusFilter.Unread -> stringResource(R.string.general_reading_status_unread)
            ReadStatusFilter.InProgress -> stringResource(R.string.general_reading_status_in_progress)
            ReadStatusFilter.Completed -> stringResource(R.string.general_reading_status_completed)
        }
    FilterChip(
        selected = current == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
    )
}

@Composable
private fun SectionHeader(
    title: String,
    expanded: Boolean,
    count: Int = 0,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        // How many values are chosen in this section, so a collapsed header still tells you what is on.
        if (count > 0) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
        )
    }
}

@Composable
private fun TriStateRow(
    items: List<Pair<Int, String>>,
    stateMap: Map<Int, TriState>,
    onStateChange: (Int, TriState) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (id, title) ->
            val state = stateMap[id] ?: TriState.None
            val prefix = ""
            val colors =
                when (state) {
                    TriState.Include ->
                        FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    TriState.Exclude ->
                        FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer,
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    TriState.None -> FilterChipDefaults.filterChipColors()
                }
            FilterChip(
                selected = state != TriState.None,
                onClick = {
                    val next =
                        when (state) {
                            TriState.None -> TriState.Include
                            TriState.Include -> TriState.Exclude
                            TriState.Exclude -> TriState.None
                        }
                    onStateChange(id, next)
                },
                label = { Text("$prefix$title") },
                colors = colors,
            )
        }
    }
}

@Composable
private fun MultiToggleRow(
    items: List<Pair<Int, String>>,
    stateMap: Map<Int, Boolean>,
    onStateChange: (Int, Boolean) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (id, title) ->
            val selected = stateMap[id] ?: false
            FilterChip(
                selected = selected,
                onClick = { onStateChange(id, !selected) },
                label = { Text(title) },
            )
        }
    }
}

@Composable
private fun TriStateRowString(
    items: List<Pair<String, String>>,
    stateMap: Map<String, TriState>,
    onStateChange: (String, TriState) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (id, title) ->
            val state = stateMap[id] ?: TriState.None
            val colors =
                when (state) {
                    TriState.Include ->
                        FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    TriState.Exclude ->
                        FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer,
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    TriState.None -> FilterChipDefaults.filterChipColors()
                }
            FilterChip(
                selected = state != TriState.None,
                onClick = {
                    val next =
                        when (state) {
                            TriState.None -> TriState.Include
                            TriState.Include -> TriState.Exclude
                            TriState.Exclude -> TriState.None
                        }
                    onStateChange(id, next)
                },
                label = { Text(title) },
                colors = colors,
            )
        }
    }
}

/** A failed refresh must not hide results that are already on screen (e.g. the cached list while offline). */
internal fun showBrowseError(
    error: String?,
    hasItems: Boolean,
): Boolean = error != null && !hasItems
