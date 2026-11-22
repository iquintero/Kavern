package com.iquintero.kavern.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.iquintero.kavern.core.cache.CacheManager
import com.iquintero.kavern.core.downloadv2.DownloadManagerV2
import com.iquintero.kavern.core.downloadv2.DownloadStorageManager
import com.iquintero.kavern.core.logging.LoggingManager
import com.iquintero.kavern.core.storage.AppPreferences
import com.iquintero.kavern.data.api.dto.SeriesDetailDto
import com.iquintero.kavern.data.api.dto.VolumeDto
import com.iquintero.kavern.data.local.db.KavernDatabase
import com.iquintero.kavern.data.local.db.dao.DownloadV2Dao
import com.iquintero.kavern.data.local.db.entity.DownloadJobV2Entity
import com.iquintero.kavern.data.local.db.entity.DownloadedItemV2Entity
import com.iquintero.kavern.ui.seriesdetail.KavernDetailV2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadQueueViewModel(
    private val downloadDao: DownloadV2Dao,
    private val cacheManager: CacheManager,
    private val deleteDownload: suspend (Long) -> Boolean,
    private val enqueueWorker: suspend () -> Unit,
) : ViewModel() {
    val tasks =
        downloadDao
            .observeJobs()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = emptyList(),
            )

    val downloaded =
        downloadDao
            .observeItemsByStatus(DownloadedItemV2Entity.STATUS_COMPLETED)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = emptyList(),
            )
    private val _lookup = MutableStateFlow(DownloadLookup())
    val lookup: StateFlow<DownloadLookup> = _lookup
    private var lastTasks: List<DownloadJobV2Entity> = emptyList()
    private var lastDownloaded: List<DownloadedItemV2Entity> = emptyList()

    init {
        viewModelScope.launch {
            tasks.collectLatest { list ->
                lastTasks = list
                updateLookup()
            }
        }
        viewModelScope.launch {
            downloaded.collectLatest { list ->
                lastDownloaded = list
                updateLookup()
            }
        }
    }

    fun cancelTask(id: Long) {
        viewModelScope.launch {
            downloadDao.transitionJob(
                id,
                listOf(
                    DownloadJobV2Entity.STATUS_PENDING,
                    DownloadJobV2Entity.STATUS_RUNNING,
                    DownloadJobV2Entity.STATUS_PAUSED,
                    DownloadJobV2Entity.STATUS_FAILED,
                ),
                DownloadJobV2Entity.STATUS_CANCELED,
            )
        }
    }

    fun pauseTask(id: Long) {
        viewModelScope.launch {
            downloadDao.transitionJob(
                id,
                listOf(DownloadJobV2Entity.STATUS_PENDING, DownloadJobV2Entity.STATUS_RUNNING),
                DownloadJobV2Entity.STATUS_PAUSED,
            )
        }
    }

    fun resumeTask(id: Long) {
        requeueTask(id, DownloadJobV2Entity.STATUS_PAUSED)
    }

    fun retryTask(id: Long) {
        requeueTask(id, DownloadJobV2Entity.STATUS_FAILED)
    }

    fun deleteDownloaded(itemId: Long) {
        viewModelScope.launch {
            try {
                if (!deleteDownload(itemId)) LoggingManager.w("DownloadQueue", "Could not delete download item $itemId")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoggingManager.e("DownloadQueue", "Could not delete download item $itemId", e)
            }
        }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch {
            downloadDao.clearItemsForJob(id)
            downloadDao.deleteJob(id)
        }
    }

    fun clearCompleted() {
        viewModelScope.launch { downloadDao.deleteJobsByStatus(DownloadJobV2Entity.STATUS_COMPLETED) }
    }

    private fun requeueTask(
        id: Long,
        previousStatus: String,
    ) {
        viewModelScope.launch {
            val changed =
                downloadDao.transitionJob(
                    id,
                    listOf(previousStatus),
                    DownloadJobV2Entity.STATUS_PENDING,
                    resetRetries = previousStatus == DownloadJobV2Entity.STATUS_FAILED,
                )
            if (changed != 0) enqueueWorker()
        }
    }

    private suspend fun updateLookup() {
        val seriesIds =
            buildSet {
                lastTasks.mapNotNullTo(this) { it.seriesId }
                lastDownloaded.mapNotNullTo(this) { it.seriesId }
            }
        if (seriesIds.isEmpty()) {
            _lookup.update { DownloadLookup() }
            return
        }
        val details =
            withContext(Dispatchers.IO) {
                seriesIds
                    .mapNotNull { id ->
                        cacheManager.getCachedSeriesDetailV2(id)?.let { id to it }
                    }.toMap()
            }
        val seriesNames = mutableMapOf<Int, String>()
        val volumeNames = mutableMapOf<Int, String>()
        val chapterTitles = mutableMapOf<Int, String>()
        details.forEach { (seriesId, detail) ->
            val name =
                detail.series
                    ?.name
                    ?.takeIf { it.isNotBlank() }
                    ?: detail.series
                        ?.localizedName
                        ?.takeIf { it.isNotBlank() }
            if (name != null) {
                seriesNames[seriesId] = name
            }
            val detailDto = detail.detail
            detailDto
                ?.volumes
                ?.forEach { volume ->
                    volumeNames[volume.id] = volumeLabel(volume)
                    volume.chapters?.forEach { chapter ->
                        chapterTitles[chapter.id] = chapterLabel(chapter)
                    }
                }
            detailDto
                ?.chapters
                ?.forEach { chapter ->
                    chapterTitles.putIfAbsent(chapter.id, chapterLabel(chapter))
                }
            detailDto
                ?.specials
                ?.forEach { chapter ->
                    chapterTitles.putIfAbsent(chapter.id, chapterLabel(chapter))
                }
            detailDto
                ?.storylineChapters
                ?.forEach { chapter ->
                    chapterTitles.putIfAbsent(chapter.id, chapterLabel(chapter))
                }
        }
        if (LoggingManager.isDebugEnabled()) {
            LoggingManager.d(
                "DownloadQueue",
                "Lookup updated series=${seriesNames.size} volumes=${volumeNames.size} chapters=${chapterTitles.size}",
            )
        }
        _lookup.update {
            DownloadLookup(
                seriesNames = seriesNames,
                volumeNames = volumeNames,
                chapterTitles = chapterTitles,
            )
        }
    }

    private fun volumeLabel(volume: VolumeDto): String {
        val name =
            volume.name?.takeIf { it.isNotBlank() }
                ?: volume.title?.takeIf { it.isNotBlank() }
        if (name != null) return name
        val number =
            volume.number
                ?: volume.minNumber?.takeIf { it > 0 }?.toInt()
        return number?.let { "Vol. $it" } ?: "Volume ${volume.id}"
    }

    private fun chapterLabel(chapter: com.iquintero.kavern.data.api.dto.ChapterDto): String =
        chapter.titleName?.takeIf { it.isNotBlank() }
            ?: chapter.title?.takeIf { it.isNotBlank() }
            ?: chapter.range?.takeIf { it.isNotBlank() }
            ?: "Chapter ${chapter.id}"
}

data class DownloadLookup(
    val seriesNames: Map<Int, String> = emptyMap(),
    val volumeNames: Map<Int, String> = emptyMap(),
    val chapterTitles: Map<Int, String> = emptyMap(),
)

/**
 * Jednoduchý factory pro manuální vytvoření (bez DI).
 */
object DownloadQueueViewModelFactory {
    fun create(
        preferences: AppPreferences,
        cacheManager: CacheManager,
    ): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                val db = KavernDatabase.getInstance(preferences.appContext, preferences.profile)
                val storage = DownloadStorageManager(preferences.appContext, preferences)
                DownloadQueueViewModel(
                    db.downloadV2Dao(),
                    cacheManager,
                    deleteDownload = { itemId -> storage.deleteDownload(db, itemId) },
                    enqueueWorker = { DownloadManagerV2.enqueueWorker(preferences) },
                )
            }
        }
}
