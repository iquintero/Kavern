package com.iquintero.kavern.ui.library

import com.iquintero.kavern.domain.model.filter.KavitaSortField
import com.iquintero.kavern.domain.usecase.AppliedFilter
import com.iquintero.kavern.domain.usecase.ReadStatusFilter

/**
 * How many kinds of filter are narrowing the Series list, for the badge on the filter button.
 * The library chips are left out because they show their own selection, and several values of one
 * kind (three genres, say) count once.
 */
fun activeFilterCount(filter: AppliedFilter): Int =
    listOf(
        filter.statusFilter != ReadStatusFilter.Any,
        filter.minYear.isNotBlank() || filter.maxYear.isNotBlank(),
        filter.genres.isNotEmpty(),
        filter.tags.isNotEmpty(),
        filter.languages.isNotEmpty(),
        filter.ageRatings.isNotEmpty(),
        filter.publication.any { it.value },
        filter.collections.isNotEmpty(),
        filter.special != null,
        filter.smartFilterId != null,
        filter.sortField != KavitaSortField.SortName || filter.sortDesc,
    ).count { it }
