package com.iquintero.kavern.core.network.grimmory

import com.iquintero.kavern.core.logging.LoggingManager
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

/**
 * Lets the parts of the app that fetch by URL, the image loader and the reader's web view, reach a Grimmory server: they
 * ask for its covers and the files of a book, and this adds the login the app holds for it. Nothing else of Grimmory goes
 * through here, since the Grimmory screens talk to the server directly.
 */
internal class GrimmoryInterceptor(
    server: GrimmoryServer,
) : Interceptor {
    private val client = server.client

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return try {
            when {
                request.url.encodedPath.startsWith("/api/v1/") -> passThrough(request)
                // Whether the server can be reached is asked of the server itself.
                request.url.encodedPath == "/api/health" -> reply(request, client.ping())
                else -> reply(request, NOT_FOUND)
            }
        } catch (e: GrimmoryHttpException) {
            LoggingManager.w("Grimmory", "${request.url.encodedPath}: HTTP ${e.code}")
            reply(request, if (e.code == 401 || e.code == 403 || e.code == 404) e.code else BAD_GATEWAY)
        } catch (e: IOException) {
            throw e
        } catch (e: RuntimeException) {
            LoggingManager.e("Grimmory", "${request.url.encodedPath} could not be answered", e)
            throw IOException("Grimmory answer could not be built", e)
        }
    }

    private fun passThrough(request: Request): Response {
        // The app adds its Kavita-style key to image and file URLs; the secret behind it is never passed on.
        val query =
            request.url
                .newBuilder()
                .removeAllQueryParameters("apiKey")
                .build()
                .encodedQuery
        val upstream = client.openEncoded(request.url.encodedPath, query)
        val body = upstream.body ?: return reply(request, NOT_FOUND)
        return Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(OK)
            .message("OK")
            .header("Content-Type", upstream.header("Content-Type") ?: "application/octet-stream")
            .body(body)
            .build()
    }

    private fun reply(
        request: Request,
        code: Int,
    ): Response =
        Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(code.toString())
            .body("".toResponseBody())
            .build()

    private companion object {
        const val OK = 200
        const val NOT_FOUND = 404
        const val BAD_GATEWAY = 502
    }
}
