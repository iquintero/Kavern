package com.iquintero.kavern.ui.browse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseErrorTest {
    @Test
    fun errorShowsWhenThereIsNothingToShow() {
        assertTrue(showBrowseError("Offline", hasItems = false))
    }

    @Test
    fun cachedResultsStayVisibleWhenTheRefreshFails() {
        assertFalse(showBrowseError("Offline", hasItems = true))
    }

    @Test
    fun noErrorNoBanner() {
        assertFalse(showBrowseError(null, hasItems = false))
    }
}
