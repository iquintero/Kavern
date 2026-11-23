package com.iquintero.kavern.ui.reader.screen

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderKeyMappingTest {
    private val volUp = KeyEvent.KEYCODE_VOLUME_UP
    private val volDown = KeyEvent.KEYCODE_VOLUME_DOWN

    @Test
    fun pageButtonsAlwaysTurnPagesWhateverTheVolumeSettings() {
        listOf(false to false, false to true, true to false, true to true).forEach { (turn, reversed) ->
            assertEquals(true, pageTurnForKey(KeyEvent.KEYCODE_PAGE_DOWN, turn, reversed))
            assertEquals(false, pageTurnForKey(KeyEvent.KEYCODE_PAGE_UP, turn, reversed))
        }
    }

    @Test
    fun volumeKeysAreLeftAloneUnlessTheyAreEnabled() {
        assertNull(pageTurnForKey(volUp, volumeKeysTurnPages = false, volumeKeysReversed = false))
        assertNull(pageTurnForKey(volDown, volumeKeysTurnPages = false, volumeKeysReversed = true))
    }

    @Test
    fun byDefaultVolumeDownIsNextAndVolumeUpIsPrevious() {
        assertEquals(true, pageTurnForKey(volDown, volumeKeysTurnPages = true, volumeKeysReversed = false))
        assertEquals(false, pageTurnForKey(volUp, volumeKeysTurnPages = true, volumeKeysReversed = false))
    }

    @Test
    fun reversedSwapsTheTwoVolumeKeys() {
        assertEquals(false, pageTurnForKey(volDown, volumeKeysTurnPages = true, volumeKeysReversed = true))
        assertEquals(true, pageTurnForKey(volUp, volumeKeysTurnPages = true, volumeKeysReversed = true))
    }

    @Test
    fun otherKeysAreNotClaimed() {
        assertNull(pageTurnForKey(KeyEvent.KEYCODE_SPACE, true, false))
    }
}
