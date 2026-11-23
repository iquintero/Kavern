package com.iquintero.kavern.ui.browse

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs an action once things have gone quiet: every [schedule] call restarts the wait, so typing
 * a word triggers one search after the last key instead of one per letter.
 */
internal class Debouncer(
    private val scope: CoroutineScope,
    private val delayMillis: Long,
) {
    private var job: Job? = null

    fun schedule(action: suspend () -> Unit) {
        job?.cancel()
        job =
            scope.launch {
                delay(delayMillis)
                action()
            }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
