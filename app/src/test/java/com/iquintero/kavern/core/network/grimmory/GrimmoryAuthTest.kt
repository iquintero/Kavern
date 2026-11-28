package com.iquintero.kavern.core.network.grimmory

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrimmoryAuthTest {
    private val origin: HttpUrl = "https://books.test/".toHttpUrl()
    private var time = 10_000_000L
    private val required = mutableListOf<Boolean>()

    private fun answer(
        request: okhttp3.Request,
        refresh: (okhttp3.Request) -> Triple<Int, String, Map<String, String>> = { Triple(200, tokens("access-2", "refresh-2"), emptyMap()) },
        login: Triple<Int, String, Map<String, String>> = Triple(200, tokens("access-1", "refresh-1"), emptyMap()),
    ): Triple<Int, String, Map<String, String>> =
        when (request.url.encodedPath) {
            "/api/v1/auth/login" -> login
            "/api/v1/auth/refresh" -> refresh(request)
            else -> Triple(200, "{}", emptyMap())
        }

    private fun tokens(
        access: String,
        refresh: String?,
        expires: Int = 7200,
    ) = """{"accessToken":"$access",${refresh?.let { "\"refreshToken\":\"$it\"," } ?: ""}"expires":$expires}"""

    private fun client(
        server: FakeServer,
        store: GrimmoryTokenStore,
        account: GrimmoryAccount = GrimmoryAccount("reader"),
    ) = GrimmoryClient(server.client, server.client, origin, account, store, { time }, { required += it })

    private fun GrimmoryClient.books(): Int? =
        try {
            getObject("api/v1/app/books")
            null
        } catch (e: GrimmoryHttpException) {
            e.code
        }

    private fun FakeServer.paths() = requests.map { it.url.encodedPath }

    @Test
    fun `a password signs in once and only the tokens are kept`() {
        val store = RecordingStore()
        val server = FakeServer { answer(it) }
        val client = client(server, store, GrimmoryAccount("reader", "secret"))
        repeat(3) { client.books() }
        assertEquals(1, server.logins)
        assertEquals("refresh-1", store.tokens?.refreshToken)
        assertTrue("the older save's password is dropped once there are tokens", store.passwordDropped)
    }

    @Test
    fun `an expired access token is renewed with the long token and the new one is saved before it is used`() {
        val store = RecordingStore(GrimmoryTokens("refresh-1", "access-old", accessExpiresAt = time - 1, issuedAt = time - 3_600_000))
        val server = FakeServer { answer(it) }
        client(server, store).books()
        assertEquals(listOf("/api/v1/auth/refresh", "/api/v1/app/books"), server.paths())
        assertEquals("Bearer access-2", server.requests.last().header("Authorization"))
        assertEquals("refresh-2", store.tokens?.refreshToken)
        assertEquals("saved by the time the books were asked for", listOf("save:refresh-2"), store.events)
        assertEquals("the long token was sent to be replaced", """{"refreshToken":"refresh-1"}""", bodyOf(server.requests.first()))
    }

    @Test
    fun `after a restart a still valid access token is used without asking the server for another`() {
        val store = RecordingStore(GrimmoryTokens("refresh-1", "access-1", accessExpiresAt = time + 3_600_000, issuedAt = time - 60_000))
        val server = FakeServer { answer(it) }
        client(server, store).books()
        assertEquals(listOf("/api/v1/app/books"), server.paths())
    }

    @Test
    fun `an access token about to run out is renewed first`() {
        val store = RecordingStore(GrimmoryTokens("refresh-1", "access-1", accessExpiresAt = time + 30_000, issuedAt = time - 3_600_000))
        val server = FakeServer { answer(it) }
        client(server, store).books()
        assertEquals("/api/v1/auth/refresh", server.paths().first())
    }

    @Test
    fun `a refused long token asks for the password and nothing is tried again`() {
        val store = RecordingStore(GrimmoryTokens("refresh-1", null, accessExpiresAt = 0, issuedAt = time - 30 * 86_400_000L))
        val server = FakeServer { answer(it, refresh = { Triple(401, "{}", emptyMap()) }) }
        val client = client(server, store)
        repeat(5) { assertEquals(401, client.books()) }
        assertEquals("one refused refresh, then no more asking", 1, server.paths().count { it == "/api/v1/auth/refresh" })
        assertTrue(client.signInRequired)
        assertEquals(listOf(true), required)
        assertEquals("nothing is thrown away: the queued reading waits for the sign-in", "refresh-1", store.tokens?.refreshToken)
    }

    @Test
    fun `typing the password again signs back in`() {
        val store = RecordingStore(GrimmoryTokens("refresh-1", null, accessExpiresAt = 0, issuedAt = time - 30 * 86_400_000L))
        var refused = true
        val server = FakeServer { answer(it, refresh = { if (refused) Triple(401, "{}", emptyMap()) else Triple(200, tokens("a", "r"), emptyMap()) }) }
        val client = client(server, store)
        client.books()
        assertTrue(client.signInRequired)
        refused = false
        client.signIn("new password")
        assertFalse(client.signInRequired)
        assertEquals(listOf(true, false), required)
        assertEquals("refresh-1", store.tokens?.refreshToken)
        assertNull(client.books())
    }

    @Test
    fun `a long token that is too new is not taken for a refused one`() {
        // Grimmory does not accept a long token for the first two minutes.
        val store = RecordingStore(GrimmoryTokens("refresh-1", null, accessExpiresAt = 0, issuedAt = time - 30_000))
        val server = FakeServer { answer(it, refresh = { Triple(401, "{}", emptyMap()) }) }
        val client = client(server, store)
        repeat(4) { client.books() }
        assertFalse("not asked to sign in again", client.signInRequired)
        assertEquals("and not asked over and over", 1, server.paths().count { it == "/api/v1/auth/refresh" })
        time += 200_000L
        client.books()
        assertEquals("tried again once it is old enough", 2, server.paths().count { it == "/api/v1/auth/refresh" })
    }

    @Test
    fun `a request refused for its token gets one new token, not one per request`() {
        val store = RecordingStore()
        val server = FakeServer { answer(it) }
        val auth = GrimmoryAuth(server.client, origin, GrimmoryAccount("reader", "p"), store, { time }, {})
        val first = auth.accessToken()
        auth.rejected(first)
        // A second request that carried the same old token is refused after the first one has been dealt with.
        val renewed = auth.accessToken()
        auth.rejected(first)
        assertEquals(renewed, auth.accessToken())
        assertEquals("only one sign-in beyond the first", 2, server.logins + server.paths().count { it == "/api/v1/auth/refresh" })
    }

    @Test
    fun `a sign-in that gives no long token is not accepted when it was just typed`() {
        val store = RecordingStore()
        val server = FakeServer { answer(it, login = Triple(200, tokens("access-1", null), emptyMap())) }
        val client = client(server, store)
        try {
            client.signIn("secret")
            org.junit.Assert.fail("a sign-in without a way to renew it must not be saved")
        } catch (e: GrimmoryHttpException) {
            assertEquals(502, e.code)
        }
        assertNull(store.tokens)
    }

    @Test
    fun `a lost connection while renewing keeps the tokens and asks for nothing`() {
        val store = RecordingStore(GrimmoryTokens("refresh-1", null, accessExpiresAt = 0, issuedAt = time - 3_600_000))
        val server = FakeServer { answer(it, refresh = { Triple(503, "", emptyMap()) }) }
        val client = client(server, store)
        assertEquals(503, client.books())
        assertFalse(client.signInRequired)
        assertEquals("refresh-1", store.tokens?.refreshToken)
    }

    @Test
    fun `the saved account never holds a password`() {
        assertEquals("grimmory:reader", grimmorySecret("reader"))
        assertEquals(GrimmoryAccount("reader"), GrimmoryAccount.decode("grimmory:reader"))
        assertEquals(GrimmoryAccount("me@x.com", "p:ss/word"), GrimmoryAccount.decode(grimmoryPasswordSecret("me@x.com", "p:ss/word")))
        assertNull(GrimmoryAccount.decode("grimmory:"))
        assertNull(GrimmoryAccount.decode("kavita-key"))
        assertNull(GrimmoryAccount.decode("grimmory:a:b:c"))
    }

    @Test
    fun `tokens survive being written and read`() {
        val tokens = GrimmoryTokens("r.e.f", "a.c.c", 1_234_567_890_123L, 987_654_321_098L)
        assertEquals(tokens, GrimmoryTokens.decode(tokens.encode()))
    }

    private fun bodyOf(request: okhttp3.Request): String {
        val buffer = okio.Buffer()
        request.body?.writeTo(buffer)
        return buffer.readUtf8()
    }
}
