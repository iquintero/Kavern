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
    private val origin: HttpUrl,
    private val credentials: GrimmoryCredentials,
) {
    @Volatile private var token: String? = null

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
    ) {
        val request =
            Request
                .Builder()
                .url(url(path))
                .header("Authorization", "Bearer ${accessToken()}")
                .put(GrimmoryJson.write(body).toRequestBody(jsonType))
                .build()
        calls.newCall(request).execute().use { response ->
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
            if (!response.isSuccessful) throw GrimmoryHttpException(response.code)
            val parsed = GrimmoryJson.parse(response.body?.string().orEmpty()) as? Json
            return parsed?.str("accessToken") ?: throw GrimmoryHttpException(response.code)
        }
    }
}
