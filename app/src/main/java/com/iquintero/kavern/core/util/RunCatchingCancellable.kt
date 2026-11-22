package com.iquintero.kavern.core.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but coroutine cancellation is rethrown instead of being returned as a failure.
 *
 * Plain `runCatching` around a suspend call turns a cancelled request (the user left the screen, a newer request
 * replaced it) into an error the UI then shows. Use this wherever the block can suspend.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
