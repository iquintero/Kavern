package com.iquintero.kavern.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.resume

/**
 * Remembers that the Kavita server could not be connected to, e.g. a home server while on mobile data.
 *
 * The phone then still reports "online", so without this every server call would wait for its full timeout.
 * While the server is marked unreachable the reader skips server calls and uses downloaded pages straight away.
 * The mark expires after [COOLDOWN_MS], is cleared by any successful call, and is reset when the connection
 * changes (e.g. back on home wifi).
 */
object ServerReachability {
    const val COOLDOWN_MS = 60_000L

    @Volatile
    private var failedAtMillis = NEVER

    @Volatile
    private var clock: () -> Long = System::currentTimeMillis

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val unreachableState = MutableStateFlow(false)

    /** Observable form of [isUnreachable] for UI such as the "reading offline" label. */
    val unreachable: StateFlow<Boolean> = unreachableState

    private val verifiedState = MutableStateFlow(false)

    /** True once a call or probe has got a verdict since startup or the last network change. */
    val verified: StateFlow<Boolean> = verifiedState
    private val checkingState = MutableStateFlow(false)

    /** True while a probe runs for a connection that has no verdict yet, e.g. for a pulsing icon. */
    val checking: StateFlow<Boolean> = checkingState

    fun markUnreachable() {
        verifiedState.value = true
        failedAtMillis = clock()
        unreachableState.value = true
        // Let observers know when the cooldown is over, unless another failure has extended it.
        scope.launch {
            delay(COOLDOWN_MS)
            unreachableState.value = isUnreachable()
        }
    }

    fun markReachable() {
        verifiedState.value = true
        failedAtMillis = NEVER
        unreachableState.value = false
    }

    /**
     * Actively checks whether the server answers, so the app notices a lost server (e.g. leaving the home
     * network) without waiting for the user to trigger a request. Unlike regular calls, every failure to
     * get a healthy answer counts as unreachable: connect errors, timeouts, TLS errors and 5xx replies
     * (which is what a proxy in front of an offline server returns).
     */
    suspend fun probe(
        calls: Call.Factory,
        serverUrl: String,
    ) {
        val request = runCatching { Request.Builder().url("${serverUrl.trimEnd('/')}/api/health").build() }.getOrNull() ?: return
        // Routine re-checks of a verified connection stay silent so the status does not keep flashing.
        checkingState.value = !verifiedState.value
        try {
            var healthy = calls.newCall(request).awaitStatusCode().isHealthy()
            if (!healthy) {
                // A single slow or dropped answer is common on mobile data; only a second miss counts.
                delay(RECHECK_DELAY_MS)
                healthy = calls.newCall(request).awaitStatusCode().isHealthy()
            }
            if (healthy) markReachable() else markUnreachable()
        } finally {
            checkingState.value = false
        }
    }

    private fun Int?.isHealthy() = this != null && this < SERVER_ERROR_MIN_CODE

    /** The HTTP status, or null when no response arrived. Cancelling the coroutine cancels the call. */
    private suspend fun Call.awaitStatusCode(): Int? =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) = continuation.resume(null)

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) = continuation.resume(response.use { it.code })
                },
            )
        }

    /** Forget a previous failure, e.g. because the device switched networks. */
    fun reset() {
        verifiedState.value = false
        failedAtMillis = NEVER
        unreachableState.value = false
    }

    fun isUnreachable(): Boolean {
        val failedAt = failedAtMillis
        if (failedAt == NEVER) return false
        return clock() - failedAt < COOLDOWN_MS
    }

    /**
     * True for failures that mean "could not reach the server" rather than a slow response:
     * refused or unroutable connections, unresolvable host names and connect timeouts.
     * Read timeouts are not counted because the server was reachable.
     */
    fun isConnectFailure(error: IOException): Boolean =
        when (error) {
            is UnknownHostException, is NoRouteToHostException -> true
            is SocketTimeoutException -> error.message.orEmpty().contains("connect", ignoreCase = true)
            is ConnectException -> true
            else -> false
        }

    internal fun setClockForTesting(source: () -> Long) {
        clock = source
        failedAtMillis = NEVER
        unreachableState.value = false
    }

    private const val NEVER = Long.MIN_VALUE
    private const val SERVER_ERROR_MIN_CODE = 500
    private const val RECHECK_DELAY_MS = 2_000L
}
