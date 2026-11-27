package com.iquintero.kavern.core.network.grimmory

import com.iquintero.kavern.core.network.NetworkLoggingInterceptor
import com.iquintero.kavern.core.network.forServerCa
import com.iquintero.kavern.core.storage.AppConfig
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Hands out the calls for a Grimmory server. One translator is kept per server and login, so that the sign-in and the
 * loaded library are shared by everything in the app that talks to it instead of starting over on every request.
 */
internal object GrimmoryServers {
    private const val TIMEOUT_SECONDS = 30L
    private const val CONNECT_TIMEOUT_SECONDS = 8L

    private data class Key(
        val url: String,
        val login: GrimmoryCredentials,
        val ca: String?,
    )

    private val servers = ConcurrentHashMap<Key, GrimmoryInterceptor>()

    fun callsFor(
        app: OkHttpClient,
        config: AppConfig,
        credentials: GrimmoryCredentials,
    ): Call.Factory {
        val translator =
            servers.getOrPut(Key(config.serverUrl, credentials, config.caCertificate)) {
                // Grimmory is called with its own client, so nothing the app adds for Kavita (the API key header)
                // is ever sent to it.
                val plain =
                    OkHttpClient
                        .Builder()
                        .addInterceptor(NetworkLoggingInterceptor)
                        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .build()
                val once = plain.newBuilder().retryOnConnectionFailure(false).build()
                val client = GrimmoryClient(plain.forServerCa(config), once.forServerCa(config), config.serverUrl.toHttpUrl(), credentials)
                GrimmoryInterceptor(client, credentials)
            }
        val translating = app.newBuilder().addInterceptor(translator).build()
        return Call.Factory { translating.newCall(it) }
    }

    /** Signs in to check the address and the login before they are saved. */
    fun verify(
        config: AppConfig,
        credentials: GrimmoryCredentials,
    ) {
        val plain = OkHttpClient.Builder().connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
        val calls = plain.forServerCa(config)
        GrimmoryClient(calls, calls, config.serverUrl.toHttpUrl(), credentials).verifyLogin()
    }
}
