package com.iquintero.kavern.ui.reader.screen

import android.webkit.WebResourceResponse
import com.iquintero.kavern.core.logging.LoggingManager
import com.iquintero.kavern.core.network.forKavita
import com.iquintero.kavern.core.network.isSameOrigin
import com.iquintero.kavern.core.storage.AppConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.GeneralSecurityException

internal class KavitaWebResources(
    private val config: AppConfig,
) {
    private val origin = config.serverUrl.toHttpUrl()
    private val calls = client.forKavita(config)

    fun fetch(url: HttpUrl): WebResourceResponse? {
        if (!origin.isSameOrigin(url)) return null
        val clean = url.withoutApiKey()
        val request = Request.Builder().url(clean)
        if (clean.pathSegments.lastOrNull() == "book-resources") {
            // Keep compatibility with Kavita versions that read the key from the query.
            request
                .url(clean.newBuilder().addQueryParameter("apiKey", config.apiKey).build())
                .header("x-api-key", config.apiKey)
        }
        return try {
            val response = calls.newCall(request.build()).execute()
            val body = response.body
            if (!response.isSuccessful || body == null) {
                LoggingManager.w("BookResources", "Could not load ${clean.encodedPath}: HTTP ${response.code}")
                response.close()
                null
            } else {
                val type = body.contentType()
                WebResourceResponse(type?.let { "${it.type}/${it.subtype}" }, type?.charset()?.name(), body.byteStream())
            }
        } catch (e: IOException) {
            LoggingManager.w("BookResources", "Could not load ${clean.encodedPath}: ${e.javaClass.simpleName}")
            null
        } catch (e: GeneralSecurityException) {
            LoggingManager.w("BookResources", "Could not load ${clean.encodedPath}: ${e.javaClass.simpleName}")
            null
        }
    }

    private companion object {
        val client =
            OkHttpClient
                .Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
    }
}
