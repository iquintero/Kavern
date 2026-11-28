package com.iquintero.kavern.core.network.grimmory

import com.squareup.moshi.Moshi
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

internal class GrimmoryHttpException(
    val code: Int,
    /** How many seconds the server asked to wait before trying again, when it said. */
    val retryAfterSeconds: Long? = null,
) : IOException("Grimmory answered HTTP $code")

/** Plain JSON from Grimmory: objects are maps, arrays are lists. */
internal typealias Json = Map<String, Any?>

internal object GrimmoryJson {
    private val moshi = Moshi.Builder().build()
    private val any = moshi.adapter(Any::class.java)

    @Suppress("UNCHECKED_CAST")
    fun parse(text: String): Any? = any.fromJson(text)

    fun write(value: Any?): String = any.toJson(value)
}

internal fun Json.str(key: String): String? = (this[key] as? String)?.takeIf { it.isNotBlank() }

internal fun Json.num(key: String): Double? = (this[key] as? Number)?.toDouble()

internal fun Json.int(key: String): Int? = num(key)?.toInt()

@Suppress("UNCHECKED_CAST")
internal fun Json.obj(key: String): Json? = this[key] as? Json

@Suppress("UNCHECKED_CAST")
internal fun Json.list(key: String): List<Any?> = (this[key] as? List<Any?>).orEmpty()

@Suppress("UNCHECKED_CAST")
internal fun Json.objects(key: String): List<Json> = list(key).filterIsInstance<Map<String, Any?>>()

@Suppress("UNCHECKED_CAST")
internal fun Json.strings(key: String): List<String> = list(key).filterIsInstance<String>()

/**
 * Talks to one Grimmory server as one user. Signs in on first use and again when the server says the token ran out.
 * Calls block, since they run inside an OkHttp interceptor.
 */
internal class GrimmoryClient(
    private val calls: Call.Factory,
    /** Makes calls that are never sent again after a lost connection, for requests that must not be doubled. */
    private val onceCalls: Call.Factory,
    private val origin: HttpUrl,
    private val credentials: GrimmoryCredentials,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Volatile private var token: String? = null

    /** Until when the server is not asked to sign in again, and the answer given instead; both are only used inside [lock]. */
    private var loginBlockedUntil = 0L
    private var loginBlockedCode = 0

    private val lock = Any()
    private val jsonType = "application/json".toMediaType()

    fun url(path: String): HttpUrl =
        origin
            .newBuilder()
            .addPathSegments(path.trimStart('/'))
            .build()

    /** A GET that returns the response unread, for covers, books and anything else that is not JSON. */
    fun open(
        path: String,
        query: Map<String, String> = emptyMap(),
        accept: String? = null,
    ): Response {
        val target = url(path).newBuilder().apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        var response = send(target, accept)
        if (response.code == 401) {
            response.close()
            synchronized(lock) { token = null }
            response = send(target, accept)
        }
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw GrimmoryHttpException(code)
        }
        return response
    }

    /** A GET of a path the app already built and encoded, returned unread. Used to hand covers and book files to image and web views. */
    fun openEncoded(
        encodedPath: String,
        encodedQuery: String?,
    ): Response {
        val target =
            origin
                .newBuilder()
                .addEncodedPathSegments(encodedPath.trimStart('/'))
                .encodedQuery(encodedQuery)
                .build()
        var response = send(target, null)
        if (response.code == 401) {
            response.close()
            synchronized(lock) { token = null }
            response = send(target, null)
        }
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw GrimmoryHttpException(code)
        }
        return response
    }

    /** The HTTP status the server answers a plain request with, to tell whether it can be reached at all. */
    fun ping(): Int = calls.newCall(Request.Builder().url(url("api/health")).build()).execute().use { it.code }

    fun getJson(
        path: String,
        query: Map<String, String> = emptyMap(),
    ): Any? =
        open(path, query).use { response ->
            GrimmoryJson.parse(response.body?.string().orEmpty())
        }

    @Suppress("UNCHECKED_CAST")
    fun getObject(
        path: String,
        query: Map<String, String> = emptyMap(),
    ): Json = getJson(path, query) as? Json ?: emptyMap()

    @Suppress("UNCHECKED_CAST")
    fun getList(
        path: String,
        query: Map<String, String> = emptyMap(),
    ): List<Json> = (getJson(path, query) as? List<Any?>).orEmpty().filterIsInstance<Map<String, Any?>>()

    fun putJson(
        path: String,
        body: Any?,
    ) = write(path, body, put = true, retry = true)

    /** Posts once: a request that was delivered but whose answer was lost must not be sent again behind the caller's back. */
    fun postJson(
        path: String,
        body: Any?,
    ) = write(path, body, put = false, retry = false)

    private fun write(
        path: String,
        body: Any?,
        put: Boolean,
        retry: Boolean,
    ) {
        val payload = GrimmoryJson.write(body).toRequestBody(jsonType)
        val request =
            Request
                .Builder()
                .url(url(path))
                .header("Authorization", "Bearer ${accessToken()}")
                .method(if (put) "PUT" else "POST", payload)
                .build()
        val caller = if (retry) calls else onceCalls
        caller.newCall(request).execute().use { response ->
            if (response.code == 401) synchronized(lock) { token = null }
            if (!response.isSuccessful) throw GrimmoryHttpException(response.code)
        }
    }

    /** Signs in and returns the user name the server knows, so that a wrong password is found when connecting. */
    fun verifyLogin() {
        synchronized(lock) { token = login() }
    }

    private fun send(
        target: HttpUrl,
        accept: String?,
    ): Response {
        val request =
            Request
                .Builder()
                .url(target)
                .header("Authorization", "Bearer ${accessToken()}")
                .apply { if (accept != null) header("Accept", accept) }
                .build()
        return calls.newCall(request).execute()
    }

    private fun accessToken(): String = token ?: synchronized(lock) { token ?: login().also { token = it } }

    private fun login(): String {
        // The server counts every failed sign-in and locks the user name for a quarter of an hour after five, which would
        // shut the account out of its own web page too. A login it has refused is not asked again until a pause has passed.
        if (now() < loginBlockedUntil) throw GrimmoryHttpException(loginBlockedCode)
        try {
            return signIn()
        } catch (e: GrimmoryHttpException) {
            blockLogins(e)
            throw e
        }
    }

    private fun signIn(): String {
        val body =
            GrimmoryJson
                .write(mapOf("username" to credentials.username, "password" to credentials.password))
                .toRequestBody(jsonType)
        val request =
            Request
                .Builder()
                .url(url("api/v1/auth/login"))
                .post(body)
                .build()
        calls.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw GrimmoryHttpException(response.code, response.header("Retry-After")?.toLongOrNull())
            val parsed = GrimmoryJson.parse(response.body?.string().orEmpty()) as? Json
            return parsed?.str("accessToken") ?: throw GrimmoryHttpException(response.code)
        }
    }

    private fun blockLogins(refusal: GrimmoryHttpException) {
        val pause =
            when (refusal.code) {
                HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> LOGIN_PAUSE_MS
                HTTP_TOO_MANY_REQUESTS -> refusal.retryAfterSeconds?.times(MS_PER_SECOND)?.coerceIn(MS_PER_SECOND, LOCKOUT_MS) ?: LOCKOUT_MS
                else -> return
            }
        loginBlockedUntil = now() + pause
        loginBlockedCode = refusal.code
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val MS_PER_SECOND = 1000L

        /** Two tries in the server's fifteen minutes of counting, so a wrong password cannot use up its five. */
        const val LOGIN_PAUSE_MS = 10 * 60_000L

        /** How long the server locks a user name out. */
        const val LOCKOUT_MS = 15 * 60_000L
    }
}
