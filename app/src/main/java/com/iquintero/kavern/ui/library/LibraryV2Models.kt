package com.iquintero.kavern.ui.library

import androidx.annotation.StringRes
import com.iquintero.kavern.R
import com.iquintero.kavern.domain.model.Collection
import com.iquintero.kavern.domain.model.Library
import com.iquintero.kavern.domain.model.Person
import com.iquintero.kavern.domain.model.ReadingList
import com.iquintero.kavern.ui.common.DownloadState

/** Fraction of a series that has been read, or null when the server didn't report page counts. */
fun seriesProgress(
    pagesRead: Int?,
    pages: Int?,
): Float? {
    val total = pages?.takeIf { it > 0 } ?: return null
    return ((pagesRead ?: 0).toFloat() / total).coerceIn(0f, 1f)
}

enum class LibraryV2Section(
    @StringRes val labelRes: Int,
) {
    Series(R.string.library_section_series),
    Collections(R.string.library_section_collections),
    ReadingLists(R.string.library_section_reading_lists),
    People(R.string.library_section_people),
}

data class LibraryV2UiState(
    val libraries: List<Library> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val collections: List<Collection> = emptyList(),
    val isCollectionsLoading: Boolean = false,
    val collectionsError: String? = null,
    val selectedCollectionId: Int? = null,
    val selectedCollectionName: String? = null,
    val collectionSeries: List<com.iquintero.kavern.domain.model.Series> = emptyList(),
    val isCollectionSeriesLoading: Boolean = false,
    val collectionSeriesError: String? = null,
    val readingLists: List<ReadingList> = emptyList(),
    val isReadingListsLoading: Boolean = false,
    val readingListsError: String? = null,
    val people: List<Person> = emptyList(),
    val isPeopleLoading: Boolean = false,
    val peopleError: String? = null,
    val peoplePage: Int = 1,
    val canLoadMorePeople: Boolean = true,
    val isPeopleLoadingMore: Boolean = false,
    val selectedSection: LibraryV2Section = LibraryV2Section.Series,
    val downloadStates: Map<Int, DownloadState> = emptyMap(),
)
