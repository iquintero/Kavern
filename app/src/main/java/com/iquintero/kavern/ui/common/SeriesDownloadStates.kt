package com.iquintero.kavern.ui.common

import com.iquintero.kavern.core.cache.CacheManager
import com.iquintero.kavern.data.local.db.entity.DownloadedItemV2Entity
import com.iquintero.kavern.domain.model.Format
import com.iquintero.kavern.domain.model.Series
import com.iquintero.kavern.ui.seriesdetail.KavernDetailV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How much of each series is on the device, keyed by series id. */
suspend fun resolveSeriesDownloadStates(
    series: List<Series>,
    downloadedItems: List<DownloadedItemV2Entity>,
    cacheManager: CacheManager,
): Map<Int, DownloadState> {
    if (series.isEmpty()) return emptyMap()
    // The same series can appear in several lists, so keep the most complete facts about each.
    val infoById = mutableMapOf<Int, Pair<Format?, Int?>>()
    series.forEach { item ->
        val current = infoById[item.id]
        infoById[item.id] = (item.format ?: current?.first) to (item.pages ?: current?.second)
    }
    // Formats the list does not describe well need the cached detail to know how many files to expect.
    val cacheIds =
        infoById
            .filter { (_, info) -> info.first == null || info.first == Format.Pdf || (info.second ?: 0) <= 0 }
            .keys
            .sorted()
    val cachedDetails =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<Int, KavernDetailV2>()
            cacheIds.forEach { id -> cacheManager.getCachedSeriesDetailV2(id)?.let { result[id] = it } }
            result
        }
    val itemsBySeries = downloadedItems.filter { it.seriesId != null }.groupBy { it.seriesId!! }
    return infoById.mapValues { (id, info) ->
        val detail = cachedDetails[id]
        DownloadStateResolver.resolveSeriesState(
            format = info.first ?: Format.fromId(detail?.series?.format),
            pagesHint = info.second,
            detail = detail?.detail,
            items = itemsBySeries[id].orEmpty(),
        )
    }
}
