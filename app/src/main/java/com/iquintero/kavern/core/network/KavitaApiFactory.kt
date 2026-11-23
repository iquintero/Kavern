package com.iquintero.kavern.core.network

import com.iquintero.kavern.core.storage.AppConfig
import com.iquintero.kavern.data.api.KavitaApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object KavitaApiFactory {
    private const val DEFAULT_TIMEOUT_SECONDS = 30L

    // An unreachable server (e.g. a home server on mobile data) should be noticed quickly.
    private const val CONNECT_TIMEOUT_SECONDS = 8L

    private fun baseRetrofitBuilder(
        baseUrl: String,
        client: Call.Factory,
    ): Retrofit.Builder {
        val moshi =
            Moshi
                .Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

        return Retrofit
            .Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .callFactory(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
    }

    fun createUnauthenticated(baseUrl: String): KavitaApi {
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(NetworkLoggingInterceptor)
                .callTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()

        val retrofit = baseRetrofitBuilder(baseUrl, client).build()
        return retrofit.create(KavitaApi::class.java)
    }

    fun createAuthenticated(
        config: AppConfig,
        followRedirects: Boolean = true,
    ): KavitaApi {
        val apiKeyInterceptor =
            Interceptor { chain ->
                val original = chain.request()
                val newRequest =
                    original
                        .newBuilder()
                        .header("x-api-key", config.apiKey)
                        .build()
                chain.proceed(newRequest)
            }

        val reachabilityInterceptor =
            Interceptor { chain ->
                try {
                    chain.proceed(chain.request()).also { ServerReachability.markReachable() }
                } catch (error: IOException) {
                    if (ServerReachability.isConnectFailure(error)) ServerReachability.markUnreachable()
                    throw error
                }
            }

        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(reachabilityInterceptor)
                .addInterceptor(apiKeyInterceptor)
                .addInterceptor(NetworkLoggingInterceptor)
                .followRedirects(followRedirects)
                .addNetworkInterceptor(CrossHostApiKeyGuard)
                .callTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()

        val retrofit = baseRetrofitBuilder(config.serverUrl, client.forKavita(config)).build()
        return retrofit.create(KavitaApi::class.java)
    }
}
