package com.iquintero.kavern.core.network.grimmory

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GrimmoryClientTest {
    private val origin = "https://books.test/".toHttpUrlForTest()
    private var time = 1_000_000L

    private fun clientOn(server: FakeServer) = GrimmoryClient(server.client, server.client, origin, GrimmoryAccount("reader", "secret"), now = { time })

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

    private fun redirecting(location: String?): FakeServer =
        FakeServer { request ->
            when {
                request.url.encodedPath.endsWith("/login") -> Triple(200, """{"accessToken":"abc"}""", emptyMap())
                request.url.encodedPath == "/api/v1/app/books" -> Triple(302, "", location?.let { mapOf("Location" to it) } ?: emptyMap())
                else -> Triple(200, "{}", emptyMap())
            }
        }

    @Test
    fun `a redirect within the server is followed`() {
        val server = redirecting("https://books.test/api/v1/elsewhere")
        clientOn(server).getObject("api/v1/app/books")
        assertEquals(
            "/api/v1/elsewhere",
            server.requests
                .last()
                .url.encodedPath,
        )
    }

    @Test
    fun `a redirect to another address is refused before anything is sent there`() {
        for (target in listOf("https://other.test/steal", "http://books.test/api/v1/downgrade", "https://books.test:8443/x")) {
            val server = redirecting(target)
            assertEquals(302, clientOn(server).tryBooks())
            assertTrue("nothing went to $target", server.requests.none { it.url.toString() == target })
        }
    }

    @Test
    fun `a redirect without a place to go is refused`() {
        assertEquals(302, clientOn(redirecting(null)).tryBooks())
    }

    @Test
    fun `a redirect that never ends is given up on`() {
        val server = redirecting("https://books.test/api/v1/app/books")
        assertEquals(508, clientOn(server).tryBooks())
    }

    @Test
    fun `the login is never sent on to where it is redirected`() {
        val server = FakeServer { Triple(307, "", mapOf("Location" to "http://evil.test/collect")) }
        assertEquals(307, clientOn(server).tryBooks())
        assertEquals(1, server.requests.size)
        assertEquals(
            "/api/v1/auth/login",
            server.requests
                .single()
                .url.encodedPath,
        )
    }

    @Test
    fun `a wrong login throws the server's answer`() {
        val server = FakeServer { Triple(401, "{}", emptyMap()) }
        try {
            clientOn(server).signIn("secret")
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
