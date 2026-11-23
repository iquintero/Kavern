package com.iquintero.kavern.core.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

const val API_KEY_HEADER = "x-api-key"

/**
 * OkHttp only drops `Authorization` when a redirect leaves the original host, so a custom
 * `x-api-key` header would follow the redirect to a third party. As a network interceptor this
 * sees every hop, and removes the key from any hop whose host differs from the original request.
 */
object CrossHostApiKeyGuard : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val original = chain.call().request().url
        val guarded =
            if (request.header(API_KEY_HEADER) != null && !request.url.host.equals(original.host, ignoreCase = true)) {
                request.newBuilder().removeHeader(API_KEY_HEADER).build()
            } else {
                request
            }
        return chain.proceed(guarded)
    }
}

/** Keeps the Kavita API key from being sent to another host after a redirect. */
fun OkHttpClient.Builder.guardApiKey(): OkHttpClient.Builder = addNetworkInterceptor(CrossHostApiKeyGuard)
