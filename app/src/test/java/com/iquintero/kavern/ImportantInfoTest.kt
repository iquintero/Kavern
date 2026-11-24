package com.iquintero.kavern

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportantInfoTest {
    @Test
    fun aNewInstallSeesNoNotesBecauseTheShippedNotesAreVersionZero() {
        // A fresh install has seen nothing, which the app stores as 0.
        assertFalse(shouldShowImportantInfo(current = IMPORTANT_INFO_VERSION, lastSeen = 0))
    }

    @Test
    fun anExistingInstallSeesNoNotesEither() {
        assertFalse(shouldShowImportantInfo(current = IMPORTANT_INFO_VERSION, lastSeen = 11))
    }

    @Test
    fun newNotesShowOnceToAnyoneWhoHasNotSeenThem() {
        assertTrue(shouldShowImportantInfo(current = 13, lastSeen = 12))
        assertFalse(shouldShowImportantInfo(current = 13, lastSeen = 13))
    }
}
