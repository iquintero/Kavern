package com.iquintero.kavern.core.network.grimmory

import com.iquintero.kavern.core.network.NetworkLoggingInterceptor
import com.iquintero.kavern.core.network.ServerReachability
import com.iquintero.kavern.core.network.forServerCa
import com.iquintero.kavern.core.storage.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Everything the app holds for one Grimmory server and login: the connection, the loaded library and what is known of its EPUBs. */
internal class GrimmoryServer(
    val client: GrimmoryClient,
    val account: GrimmoryAccount,
    store: java.io.File? = null,
) {
    val catalog = GrimmoryCatalog(client, store)
    val epubs = GrimmoryEpubs(client)
    val reading by lazy { GrimmoryReading(this) }
    val sessions by lazy { GrimmorySessions(client, catalog) }
}

/**
 * Hands out the servers and the calls for them. One [GrimmoryServer] is kept per server and login, so that the sign-in
 * and the loaded library are shared by everything in the app that talks to it instead of starting over on every request.
 */
internal object GrimmoryServers {
    private const val TIMEOUT_SECONDS = 30L
    private const val CONNECT_TIMEOUT_SECONDS = 8L

    /** What every call to the server shows about whether it can be reached, so the icon and the lists agree with the calls. */
    private val ReportReachability =
        Interceptor { chain ->
            try {
                chain.proceed(chain.request()).also { ServerReachability.markReachable() }
            } catch (error: IOException) {
                if (ServerReachability.isConnectFailure(error)) ServerReachability.markUnreachable()
                throw error
            }
        }

    private data class Key(
        val url: String,
        val username: String,
        val ca: String?,
    )

    /**
     * Where the tokens of an account are kept, set when the app starts. The tokens change every time they are renewed, so they
     * are not part of the saved configuration: it holds only who is signed in, and the servers here are found by that.
     */
    @Volatile
    var tokenStores: ((serverUrl: String, username: String, dropPassword: () -> Unit) -> GrimmoryTokenStore)? = null

    private val signInNeeded = MutableStateFlow<String?>(null)

    /** The user name of the account whose saved sign-in is no longer accepted, so that the app can ask for the password. */
    val signInRequired: StateFlow<String?> = signInNeeded

    private fun storeFor(
        serverUrl: String,
        username: String,
        dropPassword: () -> Unit = {},
    ): GrimmoryTokenStore = tokenStores?.invoke(serverUrl, username, dropPassword) ?: MemoryTokenStore()

    private val servers = ConcurrentHashMap<Key, GrimmoryServer>()

    /** A folder the app can keep what it has learned of the servers in. Set when the app starts. */
    @Volatile
    var storage: java.io.File? = null

    /** The client every call to a Grimmory server is made with. */
    internal fun plainClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(ReportReachability)
            .addInterceptor(NetworkLoggingInterceptor)
            // A redirect is never followed by the client itself: the login and the files carry the user's credentials.
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    /** The server [config] points to, which must be a Grimmory one. */
    fun serverFor(config: AppConfig): GrimmoryServer {
        val account = requireNotNull(config.grimmory) { "Not a Grimmory server" }
        return servers.getOrPut(Key(config.serverUrl.trimEnd('/'), account.username, config.caCertificate)) {
            // Grimmory is called with its own client, so nothing the app adds for Kavita (the API key header)
            // is ever sent to it.
            val plain = plainClient()
            val once = plain.newBuilder().retryOnConnectionFailure(false).build()
            val client =
                GrimmoryClient(
                    calls = plain.forServerCa(config),
                    onceCalls = once.forServerCa(config),
                    origin = config.serverUrl.toHttpUrl(),
                    account = account,
                    store = storeFor(config.serverUrl, account.username) { dropSavedPassword(config.serverUrl, account.username) },
                    onSignInRequired = { required -> signInNeeded.value = if (required) account.username else signInNeeded.value.takeUnless { it == account.username } },
                )
            val name = "books-" + Integer.toHexString((config.serverUrl + "|" + account.username).hashCode()) + ".json"
            GrimmoryServer(client, account, storage?.resolve(name))
        }
    }

    /** Called when an older save's password has been used to sign in once, and can be removed from what is saved. */
    @Volatile
    var passwordDropped: ((serverUrl: String, username: String) -> Unit)? = null

    private fun dropSavedPassword(
        serverUrl: String,
        username: String,
    ) {
        passwordDropped?.invoke(serverUrl, username)
    }

    fun callsFor(
        app: OkHttpClient,
        config: AppConfig,
    ): Call.Factory {
        val translating = app.newBuilder().addInterceptor(GrimmoryInterceptor(serverFor(config))).build()
        return Call.Factory { translating.newCall(it) }
    }

    /**
     * Signs in with a password, for checking an address and a login before they are saved or for signing in again, and keeps the
     * tokens that come of it. What is held for the account is then started afresh, so that it uses them.
     */
    fun signIn(
        config: AppConfig,
        username: String,
        password: String,
    ) {
        val plain =
            OkHttpClient
                .Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        val calls = plain.forServerCa(config)
        GrimmoryClient(calls, calls, config.serverUrl.toHttpUrl(), GrimmoryAccount(username), storeFor(config.serverUrl, username)).signIn(password)
        forget(config.serverUrl, username, keepTokens = true)
        if (signInNeeded.value == username) signInNeeded.value = null
    }

    /** Checks that what is saved still signs in, without a password: the tokens may have to be renewed to find out. */
    fun checkSignedIn(config: AppConfig) {
        val server = serverFor(config)
        if (server.client.signInRequired) throw GrimmoryHttpException(HTTP_UNAUTHORIZED)
        server.client.open("api/v1/app/users/me").close()
    }

    /** Lets go of an account: what is held in memory for it and, unless [keepTokens], the tokens that are kept. */
    fun forget(
        serverUrl: String,
        username: String,
        keepTokens: Boolean = false,
    ) {
        servers.keys.filter { it.url == serverUrl.trimEnd('/') && it.username == username }.forEach { servers.remove(it) }
        if (!keepTokens) storeFor(serverUrl, username).clear()
        if (signInNeeded.value == username) signInNeeded.value = null
    }

    private const val HTTP_UNAUTHORIZED = 401
}
