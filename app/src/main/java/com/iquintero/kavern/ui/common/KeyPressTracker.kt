package com.iquintero.kavern.ui.common

import android.view.KeyEvent

/**
 * Tells a fresh hardware key press from its repeats and its release.
 *
 * Android sometimes swallows the key-down of the first press after a touch, so only the key-up arrives. Acting on key
 * down alone would lose that press, which on an e-reader means the first page-button press after touching the screen
 * does nothing. A press therefore counts on its key-down, or on its key-up when no key-down came before it.
 */
internal class KeyPressTracker {
    private val down = mutableSetOf<Int>()

    /** True when this event starts a press that should be acted on. */
    fun isNewPress(
        keyCode: Int,
        action: Int,
        repeatCount: Int,
    ): Boolean =
        when (action) {
            KeyEvent.ACTION_DOWN -> {
                if (repeatCount == 0) {
                    down += keyCode
                    true
                } else {
                    false
                }
            }
            // The key-down was lost if it was never seen: this release is the press.
            KeyEvent.ACTION_UP -> !down.remove(keyCode)
            else -> false
        }
}
