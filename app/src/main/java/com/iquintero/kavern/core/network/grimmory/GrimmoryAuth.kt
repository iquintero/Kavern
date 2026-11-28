package com.iquintero.kavern.core.network.grimmory

import com.iquintero.kavern.core.logging.LoggingManager
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** What a sign-in gave: a token for requests, short lived, and one that gets a new one, long lived. */
internal data class GrimmoryTokens(
    val refreshToken: String,
    val accessToken: String?,
    /** When the access token stops working, in epoch milliseconds. */
    val accessExpiresAt: Long,
    /** When the pair was given. The server does not accept a long token until two minutes after that. */
    val issuedAt: Long = 0L,
) {
    fun encode(): String =
        GrimmoryJson.write(
            mapOf("refresh" to refreshToken, "access" to accessToken, "expires" to accessExpiresAt.toDouble(), "issued" to issuedAt.toDouble()),
        )

    companion object {
        fun decode(text: String): GrimmoryTokens? {
            val json = (GrimmoryJson.parse(text) as? Json) ?: return null
            val refresh = json.str("refresh") ?: return null
            return GrimmoryTokens(refresh, json.str("access"), json.num("expires")?.toLong() ?: 0L, json.num("issued")?.toLong() ?: 0L)
        }
    }
}

/** Where the tokens of one signed-in account are kept between runs of the app. */
internal interface GrimmoryTokenStore {
    fun load(): GrimmoryTokens?

    fun save(tokens: GrimmoryTokens)

    fun clear()

    /** The password an older save still holds is no longer needed, now that there are tokens. */
    fun dropPassword()
}

/** Keeps the tokens only for as long as the app runs. */
internal class MemoryTokenStore : GrimmoryTokenStore {
    private var tokens: GrimmoryTokens? = null

    override fun load() = tokens

    override fun save(tokens: GrimmoryTokens) {
        this.tokens = tokens
    }

    override fun clear() {
        tokens = null
    }

    override fun dropPassword() = Unit
}

/**
 * Keeps one account signed in. The password is used once, to sign in; from then on only tokens are kept: the short one is used
 * for requests and, when it runs out, the long one gets a new pair. If the long one is no longer accepted (it has been 30 days,
 * or the password changed, or the account signed out everywhere) nothing is tried again until the user signs in anew.
 */
internal class GrimmoryAuth(
    private val calls: Call.Factory,
    private val origin: HttpUrl,
    private val account: GrimmoryAccount,
    private val store: GrimmoryTokenStore,
    private val now: () -> Long,
    private val onSignInRequired: (Boolean) -> Unit,
) {
    private val lock = Any()
    private val jsonType = "application/json".toMediaType()
    private var tokens: GrimmoryTokens? = null
    private var tokensLoaded = false
    private var password: String? = account.password

    /** Until when the server is not asked again for what it refused, and what it said; used only inside [lock]. */
    private var blockedUntil = 0L
    private var blockedCode = 0

    /** Whether the long token was refused: the user has to sign in again. */
    @Volatile
    var signInRequired = false
        private set

    /** A token that works now, from the last sign-in, or a new one. */
    fun accessToken(): String =
        synchronized(lock) {
            val held = held()
            val usable = held?.accessToken?.takeIf { now() < held.accessExpiresAt - SKEW_MS }
            usable ?: renew(held)
        }

    /**
     * The server did not accept [used]: the next request gets a new token. If another request has already got one, nothing
     * is done, since a second renewal so soon would find the new long token not yet accepted.
     */
    fun rejected(used: String) =
        synchronized(lock) {
            val current = held()
            if (current?.accessToken == used) tokens = current.copy(accessToken = null)
        }

    /** Signs in with a password the user has just typed. Not held back by an earlier refusal. */
    fun signIn(password: String) =
        synchronized(lock) {
            blockedUntil = 0L
            takeFrom(postTokens("api/v1/auth/login", mapOf("username" to account.username, "password" to password)), requireRefresh = true)
            changeSignInRequired(false)
        }

    private fun held(): GrimmoryTokens? {
        if (!tokensLoaded) {
            tokens = store.load()
            tokensLoaded = true
        }
        return tokens
    }

    private fun renew(held: GrimmoryTokens?): String {
        // The server counts every refused attempt and locks the user name for a quarter of an hour after five.
        heldBack()?.let { throw GrimmoryHttpException(it) }
        return try {
            if (held != null) refresh(held) else signInOlderSave()
        } catch (e: GrimmoryHttpException) {
            refused(e, hadTokens = held != null, issuedAt = held?.issuedAt ?: 0L)
            throw e
        }
    }

    /** What to answer without asking the server, when asking would be pointless or harmful, or null when it is fine to ask. */
    private fun heldBack(): Int? =
        when {
            signInRequired -> HTTP_UNAUTHORIZED
            now() < blockedUntil -> blockedCode
            else -> null
        }

    private fun refresh(held: GrimmoryTokens): String = takeFrom(postTokens("api/v1/auth/refresh", mapOf("refreshToken" to held.refreshToken)), requireRefresh = true)

    /** A save from before tokens holds the password: it signs in once, and is then forgotten. */
    private fun signInOlderSave(): String {
        val saved = password ?: throw GrimmoryHttpException(HTTP_UNAUTHORIZED)
        val token = takeFrom(postTokens("api/v1/auth/login", mapOf("username" to account.username, "password" to saved)), requireRefresh = false)
        if (tokens?.refreshToken?.isNotEmpty() == true) {
            password = null
            runCatching { store.dropPassword() }.onFailure { LoggingManager.e("Grimmory", "The old password could not be dropped", it) }
        }
        return token
    }

    private fun refused(
        refusal: GrimmoryHttpException,
        hadTokens: Boolean,
        issuedAt: Long,
    ) {
        val tooNew = now() < issuedAt + NOT_BEFORE_MS
        val pause =
            when (refusal.code) {
                HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> {
                    when {
                        // A long token that is not yet two minutes old is refused only for being too new.
                        hadTokens && tooNew -> {
                            blockedUntil = issuedAt + NOT_BEFORE_MS
                            blockedCode = HTTP_UNAVAILABLE
                            return
                        }
                        // A refused long token cannot work again; a refused password is not tried again for a while.
                        hadTokens -> changeSignInRequired(true)
                        else -> blockedUntil = now() + PASSWORD_PAUSE_MS
                    }
                    PASSWORD_PAUSE_MS
                }
                HTTP_TOO_MANY_REQUESTS -> refusal.retryAfterSeconds?.times(MS_PER_SECOND)?.coerceIn(MS_PER_SECOND, LOCKOUT_MS) ?: LOCKOUT_MS
                else -> return
            }
        if (refusal.code == HTTP_TOO_MANY_REQUESTS) blockedUntil = now() + pause
        blockedCode = refusal.code
    }

    private fun changeSignInRequired(required: Boolean) {
        if (signInRequired == required) return
        signInRequired = required
        onSignInRequired(required)
    }

    private fun postTokens(
        path: String,
        body: Map<String, String>,
    ): Json {
        val request =
            Request
                .Builder()
                .url(origin.newBuilder().addPathSegments(path).build())
                .post(GrimmoryJson.write(body).toRequestBody(jsonType))
                .build()
        return calls.newCall(request).execute().use { response -> answerOf(response) }
    }

    private fun answerOf(response: Response): Json {
        if (!response.isSuccessful) throw GrimmoryHttpException(response.code, response.header("Retry-After")?.toLongOrNull())
        return (GrimmoryJson.parse(response.body?.string().orEmpty()) as? Json) ?: throw GrimmoryHttpException(response.code)
    }

    /** Keeps the tokens in an answer, saved before they are used, and returns the one for requests. */
    private fun takeFrom(
        answer: Json,
        requireRefresh: Boolean,
    ): String {
        val access = answer.str("accessToken") ?: throw GrimmoryHttpException(HTTP_BAD_GATEWAY)
        val refresh = answer.str("refreshToken")
        if (refresh == null && requireRefresh) throw GrimmoryHttpException(HTTP_BAD_GATEWAY)
        val lasts = (answer.num("expires") ?: DEFAULT_ACCESS_SECONDS).toLong() * MS_PER_SECOND
        val fresh = GrimmoryTokens(refresh.orEmpty(), access, now() + lasts, now())
        tokens = fresh
        tokensLoaded = true
        if (refresh != null) {
            // The server has already replaced the long token it was given, so the new one is saved before anything else happens.
            runCatching { store.save(fresh) }.onFailure { LoggingManager.e("Grimmory", "The sign-in could not be saved", it) }
        }
        return access
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_UNAVAILABLE = 503
        const val HTTP_BAD_GATEWAY = 502
        const val MS_PER_SECOND = 1000L
        const val DEFAULT_ACCESS_SECONDS = 7200.0

        /** Grimmory accepts a long token two minutes after it is issued, and a little more is allowed for clocks. */
        const val NOT_BEFORE_MS = 150_000L

        /** A token this close to its end is replaced at once rather than used. */
        const val SKEW_MS = 60_000L

        /** Two tries in the server's fifteen minutes of counting, so a wrong password cannot use up its five. */
        const val PASSWORD_PAUSE_MS = 10 * 60_000L

        /** How long the server locks a user name out. */
        const val LOCKOUT_MS = 15 * 60_000L
    }
}
