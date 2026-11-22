package com.iquintero.kavern.ui.library

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.iquintero.kavern.R
import com.iquintero.kavern.core.cache.CacheManager
import com.iquintero.kavern.core.cache.LibraryV2CacheKeys
import com.iquintero.kavern.core.logging.LoggingManager
import com.iquintero.kavern.core.network.NetworkUtils
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.data.local.db.KavernDatabase
import com.iquintero.kavern.data.local.db.entity.DownloadedItemV2Entity
import com.iquintero.kavern.domain.model.Collection
import com.iquintero.kavern.domain.model.Format
import com.iquintero.kavern.domain.model.Library
import com.iquintero.kavern.domain.model.Series
import com.iquintero.kavern.domain.model.filter.KavitaSortField
import com.iquintero.kavern.domain.repository.CollectionsRepository
import com.iquintero.kavern.domain.repository.LibraryRepository
import com.iquintero.kavern.domain.repository.PersonRepository
import com.iquintero.kavern.domain.repository.ReadingListRepository
import com.iquintero.kavern.domain.repository.SeriesRepository
import com.iquintero.kavern.ui.common.DownloadState
import com.iquintero.kavern.ui.common.resolveSeriesDownloadStates
import com.iquintero.kavern.ui.seriesdetail.KavernDetailV2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("LargeClass")
class LibraryV2ViewModel(
    private val libraryRepository: LibraryRepository,
    private val seriesRepository: SeriesRepository,
    private val collectionsRepository: CollectionsRepository,
    private val readingListRepository: ReadingListRepository,
    private val personRepository: PersonRepository,
    private val cacheManager: CacheManager,
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryV2UiState())
    val state: StateFlow<LibraryV2UiState> = _state
    private val downloadDao =
        KavernDatabase.getInstance(appPreferences.appContext, appPreferences.profile).downloadV2Dao()
    private var lastDownloadedItems: List<DownloadedItemV2Entity> = emptyList()
    private var homeLoadJob: Job? = null

    init {
        loadLibraries()
        loadHome()
        observeDownloadStates()
    }

    fun selectSection(section: LibraryV2Section) {
        if (section == LibraryV2Section.Home) {
            goHome()
            return
        }
        _state.update { it.copy(selectedSection = section) }
        if (section == LibraryV2Section.WantToRead) {
            ensureWantToRead()
        }
        if (section == LibraryV2Section.Collections) {
            ensureCollections()
        }
        if (section == LibraryV2Section.ReadingList) {
            ensureReadingLists()
        }
        if (section == LibraryV2Section.BrowsePeople) {
            ensurePeople()
        }
    }

    fun openCollectionFromExternal(
        collectionId: Int,
        collectionName: String?,
    ) {
        _state.update {
            it.copy(
                selectedSection = LibraryV2Section.Collections,
                selectedCollectionId = collectionId,
                selectedCollectionName = collectionName,
                collectionSeries = emptyList(),
                isCollectionSeriesLoading = true,
                collectionSeriesError = null,
            )
        }
        viewModelScope.launch {
            ensureCollections()
            loadCollectionSeries(collectionId)
        }
    }

    fun handleBack(): Boolean {
        val current = _state.value
        return when {
            current.selectedSection == LibraryV2Section.Collections && current.selectedCollectionId != null -> {
                selectCollection(null)
                true
            }

            current.selectedSection != LibraryV2Section.Home -> {
                goHome()
                true
            }

            else -> false
        }
    }

    fun goHome() {
        _state.update {
            clearLibrarySelection(clearCollectionSelection(it.copy(selectedSection = LibraryV2Section.Home)))
        }
        loadHome()
    }

    fun selectLibrary(library: Library) {
        _state.update {
            it.copy(
                selectedSection = LibraryV2Section.LibrarySeries,
                selectedLibraryId = library.id,
                selectedLibraryName = library.name,
                librarySeries = emptyList(),
                librarySeriesError = null,
                librarySeriesPage = 1,
                canLoadMoreLibrarySeries = true,
                libraryAccessDenied = false,
            )
        }
        viewModelScope.launch {
            val accessResult = runCatching { libraryRepository.hasLibraryAccess(library.id) }
            val hasAccess = accessResult.getOrDefault(false)
            if (!hasAccess) {
                _state.update {
                    it.copy(
                        libraryAccessDenied = true,
                        isLibrarySeriesLoading = false,
                        librarySeriesError = if (accessResult.isFailure) accessResult.exceptionOrNull()?.message else null,
                    )
                }
                return@launch
            }
            loadLibrarySeries(pageNumber = 1, reset = true)
        }
    }

    fun loadMoreLibrarySeries() {
        val current = _state.value
        if (
            current.isLibrarySeriesLoading ||
            current.isLibrarySeriesLoadingMore ||
            !current.canLoadMoreLibrarySeries ||
            current.selectedLibraryId == null
        ) {
            return
        }
        val nextPage = current.librarySeriesPage + 1
        loadLibrarySeries(pageNumber = nextPage, reset = false)
    }

    fun selectCollection(collection: Collection?) {
        if (collection == null) {
            _state.update {
                it.copy(
                    selectedCollectionId = null,
                    selectedCollectionName = null,
                    collectionSeries = emptyList(),
                    collectionSeriesError = null,
                )
            }
            return
        }

        if (_state.value.selectedCollectionId == collection.id &&
            _state.value.collectionSeries.isNotEmpty()
        ) {
            showCollectionSeriesDebugToast(collection.id)
            return
        }

        _state.update {
            it.copy(
                selectedCollectionId = collection.id,
                selectedCollectionName = collection.name,
                collectionSeries = emptyList(),
                collectionSeriesError = null,
            )
        }
        loadCollectionSeries(collection.id)
    }

    private fun loadLibraries() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = runCatching { libraryRepository.getLibraries() }
            _state.update {
                it.copy(
                    libraries = result.getOrDefault(emptyList()),
                    isLoading = false,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun retryHome() = loadHome(forceRefresh = true)

    private fun loadHome(forceRefresh: Boolean = false) {
        if (homeLoadJob?.isActive == true) return
        homeLoadJob =
            viewModelScope.launch {
                _state.update { it.copy(isHomeLoading = true, homeLoadFailed = false) }
                if (_state.value.homeSeries.isEmpty()) {
                    val cached = cacheManager.getCachedLibraryV2SeriesList(LibraryV2CacheKeys.HOME_LAST_READ, "")
                    if (cached.isNotEmpty()) {
                        _state.update { it.copy(homeSeries = cached) }
                        updateDownloadStates(lastDownloadedItems)
                    }
                }
                val isOnline = NetworkUtils.isOnline(appPreferences.appContext)
                logCacheDecision("home", "online=$isOnline refresh=$forceRefresh cached=${_state.value.homeSeries.size}")
                if (!isOnline) {
                    _state.update { it.copy(isHomeLoading = false, homeLoadFailed = it.homeSeries.isEmpty()) }
                    return@launch
                }
                val fresh = fetchHomePage(1)
                if (fresh == null) {
                    _state.update { it.copy(isHomeLoading = false, homeLoadFailed = it.homeSeries.isEmpty()) }
                    return@launch
                }
                cacheManager.cacheLibraryV2SeriesList(LibraryV2CacheKeys.HOME_LAST_READ, "", fresh)
                _state.update {
                    it.copy(
                        homeSeries = fresh,
                        isHomeLoading = false,
                        homeSeriesPage = 1,
                        canLoadMoreHomeSeries = fresh.size == HOME_PAGE_SIZE,
                    )
                }
                updateDownloadStates(lastDownloadedItems)
            }
    }

    fun loadMoreHome() {
        val current = _state.value
        if (current.isHomeLoading || current.isHomeLoadingMore || !current.canLoadMoreHomeSeries || current.homeSeries.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isHomeLoadingMore = true) }
            val nextPage = current.homeSeriesPage + 1
            val page = fetchHomePage(nextPage)
            _state.update {
                it.copy(
                    homeSeries = if (page == null) it.homeSeries else it.homeSeries + page,
                    isHomeLoadingMore = false,
                    homeSeriesPage = if (page.isNullOrEmpty()) it.homeSeriesPage else nextPage,
                    canLoadMoreHomeSeries = page != null && page.size == HOME_PAGE_SIZE,
                )
            }
            updateDownloadStates(lastDownloadedItems)
        }
    }

    /** One page of every series, most recently read first; null when it could not be loaded. */
    private suspend fun fetchHomePage(pageNumber: Int): List<Series>? =
        try {
            seriesRepository.getAllSeries(KavitaSortField.LastRead, ascending = false, pageNumber, HOME_PAGE_SIZE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoggingManager.w("LibraryHome", "Could not load page $pageNumber (${e.javaClass.simpleName})")
            null
        }

    private fun ensureWantToRead() {
        val current = _state.value
        if (current.isWantToReadLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isWantToReadLoading = true, wantToReadError = null) }
            val alwaysRefresh = appPreferences.cacheAlwaysRefreshFlow.first()
            val staleMinutes = appPreferences.cacheStaleMinutesFlow.first()
            val isOnline = NetworkUtils.isOnline(appPreferences.appContext)
            val cached =
                cacheManager.getCachedLibraryV2SeriesList(
                    LibraryV2CacheKeys.WANT_TO_READ,
                    "",
                )
            val cachedUpdatedAt =
                cacheManager.getLibraryV2SeriesListUpdatedAt(
                    LibraryV2CacheKeys.WANT_TO_READ,
                    "",
                )
            val hasStateData = current.wantToRead.isNotEmpty()
            val isStale =
                cachedUpdatedAt == null ||
                    (System.currentTimeMillis() - cachedUpdatedAt) > staleMinutes * 60L * 1000L
            val hasAnyData = cached.isNotEmpty() || hasStateData
            logCacheDecision(
                "want",
                "online=$isOnline cache=${cached.size} hasState=$hasStateData stale=$isStale refresh=$alwaysRefresh",
            )
            if (hasAnyData) {
                if (cached.isNotEmpty() && !hasStateData) {
                    _state.update { it.copy(wantToRead = cached, isWantToReadLoading = true, wantToReadError = null) }
                }
                if (!isOnline || (!alwaysRefresh && !isStale)) {
                    logCacheDecision("want", "using cached data only")
                    maybeShowDebugToast(R.string.debug_cache_use)
                    _state.update { it.copy(isWantToReadLoading = false) }
                    updateDownloadStates(lastDownloadedItems)
                    return@launch
                }
                val message =
                    if (alwaysRefresh) {
                        R.string.debug_cache_force_online
                    } else {
                        R.string.debug_cache_stale_reload
                    }
                maybeShowDebugToast(message)
            } else {
                logCacheDecision("want", if (isOnline) "cache miss; fetch fresh" else "cache miss; offline")
                val message =
                    if (isOnline) {
                        R.string.debug_cache_use_fresh
                    } else {
                        R.string.debug_cache_no_cache
                    }
                maybeShowDebugToast(message)
            }
            val result = runCatching { seriesRepository.getWantToReadSeries(1, 50) }
            _state.update {
                it.copy(
                    wantToRead = result.getOrDefault(emptyList()),
                    isWantToReadLoading = false,
                    wantToReadError = result.exceptionOrNull()?.message,
                )
            }
            updateDownloadStates(lastDownloadedItems)
            cacheManager.cacheLibraryV2SeriesList(
                LibraryV2CacheKeys.WANT_TO_READ,
                "",
                result.getOrDefault(emptyList()),
            )
            logCacheDecision("want", "cached list stored size=${result.getOrDefault(emptyList()).size}")
        }
    }

    private fun ensureCollections() {
        val current = _state.value
        if (current.isCollectionsLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isCollectionsLoading = true, collectionsError = null) }
            val alwaysRefresh = appPreferences.cacheAlwaysRefreshFlow.first()
            val staleMinutes = appPreferences.cacheStaleMinutesFlow.first()
            val isOnline = NetworkUtils.isOnline(appPreferences.appContext)
            val cached = cacheManager.getCachedLibraryV2Collections(LibraryV2CacheKeys.COLLECTIONS)
            val cachedUpdatedAt =
                cacheManager.getLibraryV2CollectionsUpdatedAt(
                    LibraryV2CacheKeys.COLLECTIONS,
                )
            val hasStateData = current.collections.isNotEmpty()
            val isStale =
                cachedUpdatedAt == null ||
                    (System.currentTimeMillis() - cachedUpdatedAt) > staleMinutes * 60L * 1000L
            val hasAnyData = cached.isNotEmpty() || hasStateData
            logCacheDecision(
                "collections",
                "online=$isOnline cache=${cached.size} hasState=$hasStateData stale=$isStale refresh=$alwaysRefresh",
            )
            if (hasAnyData) {
                if (cached.isNotEmpty() && !hasStateData) {
                    _state.update { it.copy(collections = cached, isCollectionsLoading = true, collectionsError = null) }
                }
                if (!isOnline || (!alwaysRefresh && !isStale)) {
                    logCacheDecision("collections", "using cached data only")
                    maybeShowDebugToast(R.string.debug_cache_use)
                    _state.update { it.copy(isCollectionsLoading = false) }
                    return@launch
                }
                val message =
                    if (alwaysRefresh) {
                        R.string.debug_cache_force_online
                    } else {
                        R.string.debug_cache_stale_reload
                    }
                maybeShowDebugToast(message)
            } else {
                logCacheDecision("collections", if (isOnline) "cache miss; fetch fresh" else "cache miss; offline")
                val message =
                    if (isOnline) {
                        R.string.debug_cache_use_fresh
                    } else {
                        R.string.debug_cache_no_cache
                    }
                maybeShowDebugToast(message)
            }
            val result = runCatching { collectionsRepository.getCollectionsAll(ownedOnly = false) }
            _state.update {
                it.copy(
                    collections = result.getOrDefault(emptyList()),
                    isCollectionsLoading = false,
                    collectionsError = result.exceptionOrNull()?.message,
                )
            }
            cacheManager.cacheLibraryV2Collections(
                LibraryV2CacheKeys.COLLECTIONS,
                result.getOrDefault(emptyList()),
            )
            logCacheDecision("collections", "cached list stored size=${result.getOrDefault(emptyList()).size}")
        }
    }

    private fun ensureReadingLists() {
        val current = _state.value
        if (current.isReadingListsLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isReadingListsLoading = true, readingListsError = null) }
            val alwaysRefresh = appPreferences.cacheAlwaysRefreshFlow.first()
            val staleMinutes = appPreferences.cacheStaleMinutesFlow.first()
            val isOnline = NetworkUtils.isOnline(appPreferences.appContext)
            val cached = cacheManager.getCachedLibraryV2ReadingLists(LibraryV2CacheKeys.READING_LISTS)
            val cachedUpdatedAt =
                cacheManager.getLibraryV2ReadingListsUpdatedAt(
                    LibraryV2CacheKeys.READING_LISTS,
                )
            val hasStateData = current.readingLists.isNotEmpty()
            val isStale =
                cachedUpdatedAt == null ||
                    (System.currentTimeMillis() - cachedUpdatedAt) > staleMinutes * 60L * 1000L
            val hasAnyData = cached.isNotEmpty() || hasStateData
            logCacheDecision(
                "readingLists",
                "online=$isOnline cache=${cached.size} hasState=$hasStateData stale=$isStale refresh=$alwaysRefresh",
            )
            if (hasAnyData) {
                if (cached.isNotEmpty() && !hasStateData) {
                    _state.update { it.copy(readingLists = cached, isReadingListsLoading = true, readingListsError = null) }
                }
                if (!isOnline || (!alwaysRefresh && !isStale)) {
                    logCacheDecision("readingLists", "using cached data only")
                    maybeShowDebugToast(R.string.debug_cache_use)
                    _state.update { it.copy(isReadingListsLoading = false) }
                    return@launch
                }
                val message =
                    if (alwaysRefresh) {
                        R.string.debug_cache_force_online
                    } else {
                        R.string.debug_cache_stale_reload
                    }
                maybeShowDebugToast(message)
            } else {
                logCacheDecision("readingLists", if (isOnline) "cache miss; fetch fresh" else "cache miss; offline")
                val message =
                    if (isOnline) {
                        R.string.debug_cache_use_fresh
                    } else {
                        R.string.debug_cache_no_cache
                    }
                maybeShowDebugToast(message)
            }
            val result = runCatching { readingListRepository.getReadingLists(includePromoted = true, sortByLastModified = false) }
            _state.update {
                it.copy(
                    readingLists = result.getOrDefault(emptyList()),
                    isReadingListsLoading = false,
                    readingListsError = result.exceptionOrNull()?.message,
                )
            }
            cacheManager.cacheLibraryV2ReadingLists(
                LibraryV2CacheKeys.READING_LISTS,
                result.getOrDefault(emptyList()),
            )
            logCacheDecision("readingLists", "cached list stored size=${result.getOrDefault(emptyList()).size}")
        }
    }

    private fun ensurePeople() {
        val current = _state.value
        if (current.isPeopleLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isPeopleLoading = true, peopleError = null) }
            val alwaysRefresh = appPreferences.cacheAlwaysRefreshFlow.first()
            val staleMinutes = appPreferences.cacheStaleMinutesFlow.first()
            val isOnline = NetworkUtils.isOnline(appPreferences.appContext)
            val cached = cacheManager.getCachedLibraryV2People(LibraryV2CacheKeys.BROWSE_PEOPLE, 1)
            val cachedUpdatedAt =
                cacheManager.getLibraryV2PeopleUpdatedAt(
                    LibraryV2CacheKeys.BROWSE_PEOPLE,
                    1,
                )
            val hasStateData = current.people.isNotEmpty()
            val isStale =
                cachedUpdatedAt == null ||
                    (System.currentTimeMillis() - cachedUpdatedAt) > staleMinutes * 60L * 1000L
            val hasAnyData = cached.isNotEmpty() || hasStateData
            logCacheDecision(
                "people",
                "online=$isOnline cache=${cached.size} hasState=$hasStateData stale=$isStale refresh=$alwaysRefresh",
            )
            if (hasAnyData) {
                if (cached.isNotEmpty() && !hasStateData) {
                    _state.update {
                        it.copy(
                            people = cached,
                            isPeopleLoading = true,
                            peopleError = null,
                            peoplePage = 1,
                            canLoadMorePeople = cached.size == 50,
                        )
                    }
                }
                if (!isOnline || (!alwaysRefresh && !isStale)) {
                    logCacheDecision("people", "using cached data only")
                    maybeShowDebugToast(R.string.debug_cache_use)
                    _state.update { it.copy(isPeopleLoading = false) }
                    updateDownloadStates(lastDownloadedItems)
                    return@launch
                }
                val message =
                    if (alwaysRefresh) {
                        R.string.debug_cache_force_online
                    } else {
                        R.string.debug_cache_stale_reload
                    }
                maybeShowDebugToast(message)
            } else {
                logCacheDecision("people", if (isOnline) "cache miss; fetch fresh" else "cache miss; offline")
                val message =
                    if (isOnline) {
                        R.string.debug_cache_use_fresh
                    } else {
                        R.string.debug_cache_no_cache
                    }
                maybeShowDebugToast(message)
            }
            val result = runCatching { personRepository.getBrowsePeople(pageNumber = 1, pageSize = 50) }
            val pageItems = result.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    people = pageItems,
                    isPeopleLoading = false,
                    peopleError = result.exceptionOrNull()?.message,
                    peoplePage = 1,
                    canLoadMorePeople = pageItems.size == 50,
                )
            }
            updateDownloadStates(lastDownloadedItems)
            cacheManager.cacheLibraryV2People(
                LibraryV2CacheKeys.BROWSE_PEOPLE,
                1,
                pageItems,
            )
            logCacheDecision("people", "cached list stored size=${pageItems.size}")
        }
    }

    private fun loadLibrarySeries(
        pageNumber: Int,
        reset: Boolean,
    ) {
        val libraryId = _state.value.selectedLibraryId ?: return
        viewModelScope.launch {
            if (reset) {
                _state.update { it.copy(isLibrarySeriesLoading = true, librarySeriesError = null) }
                val message =
                    if (NetworkUtils.isOnline(appPreferences.appContext)) {
                        R.string.debug_cache_use_fresh
                    } else {
                        R.string.debug_cache_no_cache
                    }
                maybeShowDebugToast(message)
            } else {
                _state.update { it.copy(isLibrarySeriesLoadingMore = true) }
            }
            val result = runCatching { seriesRepository.getSeriesForLibrary(libraryId, pageNumber, 25) }
            val pageItems = result.getOrDefault(emptyList())
            _state.update {
                val merged =
                    if (reset) {
                        pageItems
                    } else {
                        it.librarySeries + pageItems
                    }
                it.copy(
                    librarySeries = merged,
                    isLibrarySeriesLoading = false,
                    isLibrarySeriesLoadingMore = false,
                    librarySeriesError = result.exceptionOrNull()?.message,
                    librarySeriesPage = if (pageItems.isNotEmpty()) pageNumber else it.librarySeriesPage,
                    canLoadMoreLibrarySeries = pageItems.size == 25,
                )
            }
            updateDownloadStates(lastDownloadedItems)
        }
    }

    private fun clearCollectionSelection(state: LibraryV2UiState): LibraryV2UiState =
        state.copy(
            selectedCollectionId = null,
            selectedCollectionName = null,
            collectionSeries = emptyList(),
            isCollectionSeriesLoading = false,
            collectionSeriesError = null,
        )

    private fun clearLibrarySelection(state: LibraryV2UiState): LibraryV2UiState =
        state.copy(
            selectedLibraryId = null,
            selectedLibraryName = null,
            librarySeries = emptyList(),
            isLibrarySeriesLoading = false,
            isLibrarySeriesLoadingMore = false,
            librarySeriesError = null,
            librarySeriesPage = 1,
            canLoadMoreLibrarySeries = true,
            libraryAccessDenied = false,
        )

    fun loadMorePeople() {
        val current = _state.value
        if (current.isPeopleLoading || current.isPeopleLoadingMore || !current.canLoadMorePeople) return
        viewModelScope.launch {
            val nextPage = current.peoplePage + 1
            _state.update { it.copy(isPeopleLoadingMore = true) }
            val result = runCatching { personRepository.getBrowsePeople(pageNumber = nextPage, pageSize = 50) }
            val pageItems = result.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    people = it.people + pageItems,
                    isPeopleLoadingMore = false,
                    peopleError = result.exceptionOrNull()?.message ?: it.peopleError,
                    peoplePage = if (pageItems.isNotEmpty()) nextPage else it.peoplePage,
                    canLoadMorePeople = pageItems.size == 50,
                )
            }
            updateDownloadStates(lastDownloadedItems)
        }
    }

    private fun loadCollectionSeries(collectionId: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isCollectionSeriesLoading = true, collectionSeriesError = null) }
            val alwaysRefresh = appPreferences.cacheAlwaysRefreshFlow.first()
            val staleMinutes = appPreferences.cacheStaleMinutesFlow.first()
            val isOnline = NetworkUtils.isOnline(appPreferences.appContext)
            val cacheKey = collectionId.toString()
            val cached =
                cacheManager.getCachedLibraryV2SeriesList(
                    LibraryV2CacheKeys.COLLECTION_SERIES,
                    cacheKey,
                )
            val cachedUpdatedAt =
                cacheManager.getLibraryV2SeriesListUpdatedAt(
                    LibraryV2CacheKeys.COLLECTION_SERIES,
                    cacheKey,
                )
            val hasStateData = _state.value.collectionSeries.isNotEmpty()
            val isStale =
                cachedUpdatedAt == null ||
                    (System.currentTimeMillis() - cachedUpdatedAt) > staleMinutes * 60L * 1000L
            val hasAnyData = cached.isNotEmpty() || hasStateData
            logCacheDecision(
                "collection:$collectionId",
                "online=$isOnline cache=${cached.size} hasState=$hasStateData stale=$isStale refresh=$alwaysRefresh",
            )
            if (hasAnyData) {
                if (cached.isNotEmpty() && !hasStateData) {
                    _state.update {
                        it.copy(collectionSeries = cached, isCollectionSeriesLoading = true, collectionSeriesError = null)
                    }
                }
                if (!isOnline || (!alwaysRefresh && !isStale)) {
                    logCacheDecision("collection:$collectionId", "using cached data only")
                    maybeShowDebugToast(R.string.debug_cache_use)
                    _state.update { it.copy(isCollectionSeriesLoading = false) }
                    updateDownloadStates(lastDownloadedItems)
                    return@launch
                }
                val message =
                    if (alwaysRefresh) {
                        R.string.debug_cache_force_online
                    } else {
                        R.string.debug_cache_stale_reload
                    }
                maybeShowDebugToast(message)
            } else {
                logCacheDecision("collection:$collectionId", if (isOnline) "cache miss; fetch fresh" else "cache miss; offline")
                val message =
                    if (isOnline) {
                        R.string.debug_cache_use_fresh
                    } else {
                        R.string.debug_cache_no_cache
                    }
                maybeShowDebugToast(message)
            }
            val result = runCatching { seriesRepository.getSeriesForCollection(collectionId, 1, 50) }
            _state.update {
                it.copy(
                    collectionSeries = result.getOrDefault(emptyList()),
                    isCollectionSeriesLoading = false,
                    collectionSeriesError = result.exceptionOrNull()?.message,
                )
            }
            updateDownloadStates(lastDownloadedItems)
            cacheManager.cacheLibraryV2SeriesList(
                LibraryV2CacheKeys.COLLECTION_SERIES,
                cacheKey,
                result.getOrDefault(emptyList()),
            )
            logCacheDecision(
                "collection:$collectionId",
                "cached list stored size=${result.getOrDefault(emptyList()).size}",
            )
        }
    }

    private suspend fun maybeShowDebugToast(
        @StringRes messageRes: Int,
    ) {
        if (!appPreferences.debugToastsFlow.first()) return
        Toast
            .makeText(
                appPreferences.appContext,
                appPreferences.appContext.getString(messageRes),
                Toast.LENGTH_SHORT,
            ).show()
    }

    private fun logCacheDecision(
        section: String,
        message: String,
    ) {
        if (!LoggingManager.isDebugEnabled()) return
        LoggingManager.d("LibraryV2Cache", "[$section] $message")
    }

    private fun showCollectionSeriesDebugToast(collectionId: Int) {
        viewModelScope.launch {
            val alwaysRefresh = appPreferences.cacheAlwaysRefreshFlow.first()
            val staleMinutes = appPreferences.cacheStaleMinutesFlow.first()
            val isOnline = NetworkUtils.isOnline(appPreferences.appContext)
            val cacheKey = collectionId.toString()
            val cachedUpdatedAt =
                cacheManager.getLibraryV2SeriesListUpdatedAt(
                    LibraryV2CacheKeys.COLLECTION_SERIES,
                    cacheKey,
                )
            val isStale =
                cachedUpdatedAt == null ||
                    (System.currentTimeMillis() - cachedUpdatedAt) > staleMinutes * 60L * 1000L
            val message =
                if (!isOnline || (!alwaysRefresh && !isStale)) {
                    R.string.debug_cache_use
                } else if (alwaysRefresh) {
                    R.string.debug_cache_force_online
                } else {
                    R.string.debug_cache_stale_reload
                }
            maybeShowDebugToast(message)
        }
    }

    private fun observeDownloadStates() {
        viewModelScope.launch {
            downloadDao
                .observeItemsByStatus(DownloadedItemV2Entity.STATUS_COMPLETED)
                .collectLatest { items ->
                    lastDownloadedItems = items
                    updateDownloadStates(items)
                }
        }
    }

    private suspend fun updateDownloadStates(items: List<DownloadedItemV2Entity>) {
        val state = _state.value
        val shown = state.homeSeries + state.wantToRead + state.collectionSeries + state.librarySeries
        val states = resolveSeriesDownloadStates(shown, items, cacheManager)
        _state.update { it.copy(downloadStates = states) }
    }

    companion object {
        private const val HOME_PAGE_SIZE = 25

        fun provideFactory(
            libraryRepository: LibraryRepository,
            seriesRepository: SeriesRepository,
            collectionsRepository: CollectionsRepository,
            readingListRepository: ReadingListRepository,
            personRepository: PersonRepository,
            cacheManager: CacheManager,
            appPreferences: AppPreferences,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LibraryV2ViewModel(
                        libraryRepository,
                        seriesRepository,
                        collectionsRepository,
                        readingListRepository,
                        personRepository,
                        cacheManager,
                        appPreferences,
                    ) as T
            }
    }
}
