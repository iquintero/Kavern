package com.iquintero.kavern.core.network.grimmory

import com.iquintero.kavern.core.network.NetworkLoggingInterceptor
import com.iquintero.kavern.core.network.ServerReachability
import com.iquintero.kavern.core.network.forServerCa
import com.iquintero.kavern.core.storage.AppConfig
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
    val account: GrimmoryCredentials,
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
        val login: GrimmoryCredentials,
        val ca: String?,
    )

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
        val credentials = requireNotNull(config.grimmory) { "Not a Grimmory server" }
        return servers.getOrPut(Key(config.serverUrl, credentials, config.caCertificate)) {
            // Grimmory is called with its own client, so nothing the app adds for Kavita (the API key header)
            // is ever sent to it.
            val plain = plainClient()
            val once = plain.newBuilder().retryOnConnectionFailure(false).build()
            val client = GrimmoryClient(plain.forServerCa(config), once.forServerCa(config), config.serverUrl.toHttpUrl(), credentials)
            val name = "books-" + Integer.toHexString((config.serverUrl + "|" + credentials.username).hashCode()) + ".json"
            GrimmoryServer(client, credentials, storage?.resolve(name))
        }
    }

    fun callsFor(
        app: OkHttpClient,
        config: AppConfig,
        credentials: GrimmoryCredentials,
    ): Call.Factory {
        val translating = app.newBuilder().addInterceptor(GrimmoryInterceptor(serverFor(config))).build()
        return Call.Factory { translating.newCall(it) }
    }

    /** Signs in to check the address and the login before they are saved. */
    fun verify(
        config: AppConfig,
        credentials: GrimmoryCredentials,
    ) {
        val plain =
            OkHttpClient
                .Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        val calls = plain.forServerCa(config)
        GrimmoryClient(calls, calls, config.serverUrl.toHttpUrl(), credentials).verifyLogin()
    }
}
