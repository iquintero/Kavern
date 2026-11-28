package com.iquintero.kavern.core.network.grimmory

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** A Grimmory client talking to a server that exists only in this test: [answer] says what each request gets. */
private class FakeServer(
    private val answer: (Request) -> Triple<Int, String, Map<String, String>>,
) {
    val requests = mutableListOf<Request>()

    val client: OkHttpClient =
        OkHttpClient
            .Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(
                Interceptor { chain ->
                    val request = chain.request()
                    requests += request
                    val (code, body, headers) = answer(request)
                    Response
                        .Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message(code.toString())
                        .apply { headers.forEach { (k, v) -> header(k, v) } }
                        .body(body.toResponseBody())
                        .build()
                },
            ).build()

    val logins: Int get() = requests.count { it.url.encodedPath == "/api/v1/auth/login" }
}

class GrimmoryClientTest {
    private val origin = "https://books.test/".toHttpUrlForTest()
    private var time = 1_000_000L

    private fun clientOn(server: FakeServer) = GrimmoryClient(server.client, server.client, origin, GrimmoryCredentials("reader", "secret"), now = { time })

    private fun GrimmoryClient.tryBooks(): Int? =
        try {
            getObject("api/v1/app/books")
            null
        } catch (e: GrimmoryHttpException) {
            e.code
        }

    @Test
    fun `a refused login is not tried again on every request`() {
        val server = FakeServer { Triple(401, "{}", emptyMap()) }
        val client = clientOn(server)
        repeat(10) { assertEquals(401, client.tryBooks()) }
        assertEquals("the server counts every failed sign-in", 1, server.logins)
    }

    @Test
    fun `the login is tried again once the pause has passed`() {
        val server = FakeServer { Triple(401, "{}", emptyMap()) }
        val client = clientOn(server)
        client.tryBooks()
        time += 9 * 60_000L
        client.tryBooks()
        assertEquals(1, server.logins)
        time += 2 * 60_000L
        client.tryBooks()
        assertEquals(2, server.logins)
    }

    @Test
    fun `a lockout answer is respected for as long as the server says`() {
        val server = FakeServer { Triple(429, "{}", mapOf("Retry-After" to "120")) }
        val client = clientOn(server)
        assertEquals(429, client.tryBooks())
        time += 60_000L
        assertEquals("still locked out", 429, client.tryBooks())
        assertEquals(1, server.logins)
        time += 61_000L
        client.tryBooks()
        assertEquals(2, server.logins)
    }

    @Test
    fun `a connection that fails does not block the login`() {
        var down = true
        val server =
            FakeServer { request ->
                when {
                    request.url.encodedPath.endsWith("/login") -> if (down) Triple(503, "", emptyMap()) else Triple(200, """{"accessToken":"t"}""", emptyMap())
                    else -> Triple(200, """{"content":[]}""", emptyMap())
                }
            }
        val client = clientOn(server)
        assertEquals(503, client.tryBooks())
        down = false
        assertEquals(null, client.tryBooks())
        assertEquals(2, server.logins)
    }

    @Test
    fun `a working login signs in once and keeps the token`() {
        val server =
            FakeServer { request ->
                if (request.url.encodedPath.endsWith("/login")) Triple(200, """{"accessToken":"abc"}""", emptyMap()) else Triple(200, "{}", emptyMap())
            }
        val client = clientOn(server)
        repeat(3) { client.getObject("api/v1/app/books") }
        assertEquals(1, server.logins)
        assertTrue(server.requests.filter { !it.url.encodedPath.endsWith("/login") }.all { it.header("Authorization") == "Bearer abc" })
    }

    @Test
    fun `a wrong login throws the server's answer`() {
        val server = FakeServer { Triple(401, "{}", emptyMap()) }
        try {
            clientOn(server).verifyLogin()
            fail("expected a refusal")
        } catch (e: GrimmoryHttpException) {
            assertEquals(401, e.code)
        }
    }
}

private fun String.toHttpUrlForTest() =
    okhttp3.HttpUrl
        .Builder()
        .scheme("https")
        .host(removePrefix("https://").trimEnd('/'))
        .build()
