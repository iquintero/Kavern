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

    @Test
    fun cacheKeyChangesWhenTheListedFormatsChange() {
        val key = browseCacheKey("", "filter", 1)
        assertTrue(key.contains("fmt=3,4,1,0"))
        assertFalse(key == "s=|f=filter|p=1")
    }
}
