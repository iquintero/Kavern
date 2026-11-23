package com.iquintero.kavern.ui.common

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyPressTrackerTest {
    private val pageDown = KeyEvent.KEYCODE_PAGE_DOWN
    private val pageUp = KeyEvent.KEYCODE_PAGE_UP
    private val down = KeyEvent.ACTION_DOWN
    private val up = KeyEvent.ACTION_UP

    @Test
    fun anOrdinaryPressCountsOnceOnKeyDown() {
        val keys = KeyPressTracker()
        assertTrue(keys.isNewPress(pageDown, down, 0))
        assertFalse(keys.isNewPress(pageDown, up, 0))
    }

    @Test
    fun aHeldKeyDoesNotRepeat() {
        val keys = KeyPressTracker()
        assertTrue(keys.isNewPress(pageDown, down, 0))
        assertFalse(keys.isNewPress(pageDown, down, 1))
        assertFalse(keys.isNewPress(pageDown, down, 2))
        assertFalse(keys.isNewPress(pageDown, up, 0))
    }

    @Test
    fun aPressWhoseKeyDownWasSwallowedStillCountsOnRelease() {
        val keys = KeyPressTracker()
        assertTrue(keys.isNewPress(pageDown, up, 0))
    }

    @Test
    fun theNextPressAfterALostKeyDownIsStillOrdinary() {
        val keys = KeyPressTracker()
        assertTrue(keys.isNewPress(pageDown, up, 0))
        assertTrue(keys.isNewPress(pageDown, down, 0))
        assertFalse(keys.isNewPress(pageDown, up, 0))
    }

    @Test
    fun differentKeysAreTrackedSeparately() {
        val keys = KeyPressTracker()
        assertTrue(keys.isNewPress(pageDown, down, 0))
        assertTrue(keys.isNewPress(pageUp, up, 0))
        assertFalse(keys.isNewPress(pageDown, up, 0))
    }

    @Test
    fun otherActionsAreIgnored() {
        assertFalse(KeyPressTracker().isNewPress(pageDown, KeyEvent.ACTION_MULTIPLE, 0))
    }
}
