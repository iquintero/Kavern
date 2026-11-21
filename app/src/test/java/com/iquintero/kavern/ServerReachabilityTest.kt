package com.iquintero.kavern

import com.iquintero.kavern.core.network.KavitaApiFactory
import com.iquintero.kavern.core.network.ServerReachability
import com.iquintero.kavern.core.storage.AppConfig
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ServerReachabilityTest {
    private var now = 1_000_000L

    @Before
    fun setUp() {
        ServerReachability.setClockForTesting { now }
    }

    @After
    fun tearDown() {
        ServerReachability.setClockForTesting(System::currentTimeMillis)
    }

    private fun client(respond: (Interceptor.Chain) -> Response) = OkHttpClient.Builder().addInterceptor(respond).build()

    private fun reply(
        chain: Interceptor.Chain,
        code: Int,
    ) = Response
        .Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("test")
        .body("".toResponseBody())
        .build()

    @Test
    fun probeMarksTheServerReachableOnAnyHealthyAnswer() {
        ServerReachability.markUnreachable()
        runBlocking { ServerReachability.probe(client { reply(it, 200) }, "https://kavita.example") }
        assertFalse(ServerReachability.isUnreachable())
        assertTrue(ServerReachability.verified.value)
    }

    @Test
    fun probeMarksTheServerUnreachableWhenTheCallTimesOut() {
        runBlocking {
            ServerReachability.probe(client { throw InterruptedIOException("timeout") }, "https://kavita.example")
        }
        assertTrue(ServerReachability.isUnreachable())
        assertTrue(ServerReachability.verified.value)
    }

    @Test
    fun probeMarksTheServerUnreachableOnAProxyErrorPage() {
        runBlocking { ServerReachability.probe(client { reply(it, 502) }, "https://kavita.example") }
        assertTrue(ServerReachability.isUnreachable())
    }

    @Test
    fun startsReachable() {
        assertFalse(ServerReachability.isUnreachable())
    }

    @Test
    fun unreachableUntilTheCooldownEnds() {
        ServerReachability.markUnreachable()
        assertTrue(ServerReachability.isUnreachable())
        now += ServerReachability.COOLDOWN_MS - 1
        assertTrue(ServerReachability.isUnreachable())
        now += 1
        assertFalse(ServerReachability.isUnreachable())
    }

    @Test
    fun aSuccessfulCallClearsTheMark() {
        ServerReachability.markUnreachable()
        ServerReachability.markReachable()
        assertFalse(ServerReachability.isUnreachable())
    }

    @Test
    fun resetClearsTheMarkWhenTheNetworkChanges() {
        ServerReachability.markUnreachable()
        ServerReachability.reset()
        assertFalse(ServerReachability.isUnreachable())
    }

    @Test
    fun theObservableStateFollowsTheMark() {
        assertFalse(ServerReachability.unreachable.value)
        ServerReachability.markUnreachable()
        assertTrue(ServerReachability.unreachable.value)
        ServerReachability.markReachable()
        assertFalse(ServerReachability.unreachable.value)
        ServerReachability.markUnreachable()
        ServerReachability.reset()
        assertFalse(ServerReachability.unreachable.value)
    }

    @Test
    fun aNewFailureRestartsTheCooldown() {
        ServerReachability.markUnreachable()
        now += ServerReachability.COOLDOWN_MS - 10
        ServerReachability.markUnreachable()
        now += ServerReachability.COOLDOWN_MS - 10
        assertTrue(ServerReachability.isUnreachable())
    }

    @Test
    fun connectFailuresCountAsUnreachable() {
        assertTrue(ServerReachability.isConnectFailure(ConnectException("Connection refused")))
        assertTrue(ServerReachability.isConnectFailure(UnknownHostException("kavita.home")))
        assertTrue(ServerReachability.isConnectFailure(NoRouteToHostException("No route to host")))
        assertTrue(
            ServerReachability.isConnectFailure(
                SocketTimeoutException("failed to connect to /192.168.1.10 (port 5000) after 8000ms"),
            ),
        )
    }

    @Test
    fun aRefusedConnectionMarksTheServerUnreachableQuickly() {
        ServerReachability.setClockForTesting(System::currentTimeMillis)
        val api =
            KavitaApiFactory.createAuthenticated(
                AppConfig(serverUrl = "http://127.0.0.1:1", apiKey = "key", imageApiKey = "key", userId = 1),
            )
        val started = System.nanoTime()
        val failed = runCatching { runBlocking { api.getBookInfo(1) } }.isFailure
        val seconds = (System.nanoTime() - started) / 1_000_000_000.0
        assertTrue(failed)
        assertTrue("expected a fast failure but took $seconds s", seconds < 5)
        assertTrue(ServerReachability.isUnreachable())
    }

    @Test
    fun slowResponsesAndOtherErrorsDoNotCountAsUnreachable() {
        assertFalse(ServerReachability.isConnectFailure(SocketTimeoutException("timeout")))
        assertFalse(ServerReachability.isConnectFailure(SocketTimeoutException("Read timed out")))
        assertFalse(ServerReachability.isConnectFailure(IOException("Canceled")))
    }
}
