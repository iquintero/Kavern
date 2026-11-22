package com.iquintero.kavern.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.iquintero.kavern.core.cache.CacheManager
import com.iquintero.kavern.core.cache.LibraryV2CacheKeys
import com.iquintero.kavern.core.logging.LoggingManager
import com.iquintero.kavern.core.network.NetworkUtils
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.data.local.db.KavernDatabase
import com.iquintero.kavern.data.local.db.entity.DownloadedItemV2Entity
import com.iquintero.kavern.domain.model.Series
import com.iquintero.kavern.domain.repository.SeriesRepository
import com.iquintero.kavern.ui.common.DownloadState
import com.iquintero.kavern.ui.common.resolveSeriesDownloadStates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val sections: HomeSections = HomeSections(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val downloadStates: Map<Int, DownloadState> = emptyMap(),
)

class HomeViewModel(
    private val seriesRepository: SeriesRepository,
    private val cacheManager: CacheManager,
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state
    private val downloadDao =
        KavernDatabase.getInstance(appPreferences.appContext, appPreferences.profile).downloadV2Dao()
    private var downloadedItems: List<DownloadedItemV2Entity> = emptyList()
    private var loadJob: Job? = null

    init {
        load()
        observeDownloadStates()
    }

    fun refresh() = load()

    private fun load() {
        if (loadJob?.isActive == true) return
        loadJob =
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, loadFailed = false) }
                if (_state.value.sections.isEmpty) {
                    showSections(
                        onDeck = cached(LibraryV2CacheKeys.HOME_ON_DECK),
                        wantToRead = cached(LibraryV2CacheKeys.WANT_TO_READ),
                        recentlyAdded = cached(LibraryV2CacheKeys.HOME_RECENTLY_ADDED),
                    )
                }
                if (!NetworkUtils.isOnline(appPreferences.appContext)) {
                    _state.update { it.copy(isLoading = false, loadFailed = it.sections.isEmpty) }
                    return@launch
                }
                val fetched = fetchLists()
                if (fetched == null) {
                    _state.update { it.copy(isLoading = false, loadFailed = it.sections.isEmpty) }
                    return@launch
                }
                val (onDeck, wantToRead, recentlyAdded) = fetched
                cacheManager.cacheLibraryV2SeriesList(LibraryV2CacheKeys.HOME_ON_DECK, "", onDeck)
                cacheManager.cacheLibraryV2SeriesList(LibraryV2CacheKeys.WANT_TO_READ, "", wantToRead)
                cacheManager.cacheLibraryV2SeriesList(LibraryV2CacheKeys.HOME_RECENTLY_ADDED, "", recentlyAdded)
                showSections(onDeck, wantToRead, recentlyAdded)
                _state.update { it.copy(isLoading = false) }
            }
    }

    private suspend fun cached(listType: String): List<Series> = cacheManager.getCachedLibraryV2SeriesList(listType, "")

    /** The three server lists, or null when any of them could not be loaded. */
    private suspend fun fetchLists(): Triple<List<Series>, List<Series>, List<Series>>? =
        try {
            coroutineScope {
                val onDeck = async { seriesRepository.getOnDeckSeries(1, LIST_SIZE) }
                val wantToRead = async { seriesRepository.getWantToReadSeries(1, LIST_SIZE) }
                val recentlyAdded = async { seriesRepository.getRecentlyAddedSeries(1, LIST_SIZE) }
                Triple(onDeck.await(), wantToRead.await(), recentlyAdded.await())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoggingManager.w("Home", "Could not load Home lists (${e.javaClass.simpleName})")
            null
        }

    private suspend fun showSections(
        onDeck: List<Series>,
        wantToRead: List<Series>,
        recentlyAdded: List<Series>,
    ) {
        val sections = buildHomeSections(onDeck, wantToRead, recentlyAdded)
        _state.update { it.copy(sections = sections) }
        updateDownloadStates()
    }

    private fun observeDownloadStates() {
        viewModelScope.launch {
            downloadDao
                .observeItemsByStatus(DownloadedItemV2Entity.STATUS_COMPLETED)
                .collectLatest { items ->
                    downloadedItems = items
                    updateDownloadStates()
                }
        }
    }

    private suspend fun updateDownloadStates() {
        val sections = _state.value.sections
        val shown = listOfNotNull(sections.resume) + sections.inProgress + sections.wantToRead + sections.recentlyAdded
        val states = resolveSeriesDownloadStates(shown, downloadedItems, cacheManager)
        _state.update { it.copy(downloadStates = states) }
    }

    companion object {
        private const val LIST_SIZE = 20

        fun provideFactory(
            seriesRepository: SeriesRepository,
            cacheManager: CacheManager,
            appPreferences: AppPreferences,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    HomeViewModel(seriesRepository, cacheManager, appPreferences)
                }
            }
    }
}
