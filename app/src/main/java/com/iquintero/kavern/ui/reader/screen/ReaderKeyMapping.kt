package com.iquintero.kavern.ui.reader.screen

import android.view.KeyEvent

/**
 * Which way a hardware key turns the page: true for the next page, false for the previous one, or null when the key
 * is not ours. The e-reader's page buttons always turn pages. The volume keys only do when [volumeKeysTurnPages] is on,
 * and [volumeKeysReversed] swaps which one goes forward (the default is volume down for next, as on most phones).
 */
internal fun pageTurnForKey(
    keyCode: Int,
    volumeKeysTurnPages: Boolean,
    volumeKeysReversed: Boolean,
): Boolean? =
    when (keyCode) {
        KeyEvent.KEYCODE_PAGE_DOWN -> true
        KeyEvent.KEYCODE_PAGE_UP -> false
        KeyEvent.KEYCODE_VOLUME_DOWN -> if (volumeKeysTurnPages) !volumeKeysReversed else null
        KeyEvent.KEYCODE_VOLUME_UP -> if (volumeKeysTurnPages) volumeKeysReversed else null
        else -> null
    }
