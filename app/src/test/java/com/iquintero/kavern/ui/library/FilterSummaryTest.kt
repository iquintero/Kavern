package com.iquintero.kavern.ui.library

import com.iquintero.kavern.domain.model.filter.KavitaSortField
import com.iquintero.kavern.domain.model.filter.TriState
import com.iquintero.kavern.domain.usecase.AppliedFilter
import com.iquintero.kavern.domain.usecase.ReadStatusFilter
import com.iquintero.kavern.domain.usecase.SpecialFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class FilterSummaryTest {
    @Test
    fun noFiltersMeansNoBadge() {
        assertEquals(0, activeFilterCount(AppliedFilter()))
    }

    @Test
    fun theLibraryChipsDoNotCount() {
        assertEquals(0, activeFilterCount(AppliedFilter(libraries = mapOf(2 to TriState.Include))))
    }

    @Test
    fun severalValuesOfOneKindCountOnce() {
        val filter = AppliedFilter(genres = mapOf(1 to TriState.Include, 2 to TriState.Exclude, 3 to TriState.Include))
        assertEquals(1, activeFilterCount(filter))
    }

    @Test
    fun eachKindOfFilterCounts() {
        val filter =
            AppliedFilter(
                statusFilter = ReadStatusFilter.Unread,
                minYear = "1990",
                tags = mapOf(4 to TriState.Include),
                special = SpecialFilter.WantToRead,
                sortField = KavitaSortField.ReleaseYear,
            )
        assertEquals(5, activeFilterCount(filter))
    }

    @Test
    fun aSortDirectionChangeCounts() {
        assertEquals(1, activeFilterCount(AppliedFilter(sortDesc = true)))
    }
}
