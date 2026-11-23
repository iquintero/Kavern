package com.iquintero.kavern.ui.reader.screen

import android.view.KeyEvent
import com.iquintero.kavern.ui.common.KeyPressTracker

/**
 * Lets the open reader claim hardware page-turn keys (the page buttons on e-readers, optionally the volume
 * keys). The activity asks [handle] before the system acts on a key, so a claimed key does not change the volume.
 */
internal object ReaderKeyEvents {
    /** Set by the reader while it is on screen: given a key code and whether it is a fresh press, returns true if claimed. */
    private val presses = KeyPressTracker()

    @Volatile
    var onKey: ((keyCode: Int, isNewPress: Boolean) -> Boolean)? = null

    fun handle(event: KeyEvent): Boolean {
        val handler = onKey ?: return false
        return handler(event.keyCode, presses.isNewPress(event.keyCode, event.action, event.repeatCount))
    }
}
