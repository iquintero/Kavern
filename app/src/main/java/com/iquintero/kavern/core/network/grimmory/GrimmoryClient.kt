package com.iquintero.kavern.core.network.grimmory

import com.iquintero.kavern.core.network.isSameOrigin
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
    account: GrimmoryAccount,
    store: GrimmoryTokenStore = MemoryTokenStore(),
    now: () -> Long = System::currentTimeMillis,
    onSignInRequired: (Boolean) -> Unit = {},
) {
    private val auth = GrimmoryAuth(calls, origin, account, store, now, onSignInRequired)
    private val jsonType = "application/json".toMediaType()

    /** Whether the server no longer accepts the saved sign-in, so that the user has to enter the password again. */
    val signInRequired: Boolean get() = auth.signInRequired

    /** Signs in with a password the user has typed, and keeps the tokens it gives. */
    fun signIn(password: String) = auth.signIn(password)

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
        return get(target, accept)
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
        return get(target, null)
    }

    /** A GET with the token, tried again once with a new token when the server says the one it had has run out. */
    private fun get(
        target: HttpUrl,
        accept: String?,
    ): Response {
        var response = send(target, accept)
        if (response.code == 401) {
            val used = response.request.bearerToken()
            response.close()
            auth.rejected(used)
            response = send(target, accept)
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
                .header("Authorization", "Bearer ${auth.accessToken()}")
                .method(if (put) "PUT" else "POST", payload)
                .build()
        val caller = if (retry) calls else onceCalls
        caller.newCall(request).execute().use { response ->
            if (response.code == 401) auth.rejected(request.bearerToken())
            if (!response.isSuccessful) throw GrimmoryHttpException(response.code)
        }
    }

    /** A GET with the token. A redirect is followed only to the same address, so the token never goes anywhere else. */
    private fun send(
        target: HttpUrl,
        accept: String?,
    ): Response {
        var url = target
        repeat(MAX_REDIRECTS + 1) {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Authorization", "Bearer ${auth.accessToken()}")
                    .apply { if (accept != null) header("Accept", accept) }
                    .build()
            val response = calls.newCall(request).execute()
            if (!response.isRedirect) return response
            val next = response.header("Location")?.let(request.url::resolve)
            val code = response.code
            response.close()
            if (next == null || !next.isSameOrigin(origin)) throw GrimmoryHttpException(code)
            url = next
        }
        throw GrimmoryHttpException(HTTP_LOOP_DETECTED)
    }

    private fun Request.bearerToken(): String = header("Authorization").orEmpty().removePrefix("Bearer ")

    private companion object {
        const val HTTP_LOOP_DETECTED = 508
        const val MAX_REDIRECTS = 3
    }
}
