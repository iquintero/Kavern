package com.iquintero.kavern.core.network.grimmory

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** A Grimmory client talking to a server that exists only in this test: [answer] says what each request gets. */
internal class FakeServer(
    private val answer: (Request) -> Triple<Int, String, Map<String, String>>,
) {
    val requests = mutableListOf<Request>()

    val client: OkHttpClient =
        OkHttpClient
            .Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(
                Interceptor { chain ->
                    val request = chain.request()
                    requests += request
                    val (code, body, headers) = answer(request)
                    Response
                        .Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message(code.toString())
                        .apply { headers.forEach { (k, v) -> header(k, v) } }
                        .body(body.toResponseBody())
                        .build()
                },
            ).build()

    val logins: Int get() = requests.count { it.url.encodedPath == "/api/v1/auth/login" }
}

/** A token store that remembers what happened to it, in order, alongside what the server was asked. */
internal class RecordingStore(
    var tokens: GrimmoryTokens? = null,
    private val log: MutableList<String> = mutableListOf(),
) : GrimmoryTokenStore {
    val events: List<String> get() = log
    var passwordDropped = false

    override fun load() = tokens

    override fun save(tokens: GrimmoryTokens) {
        this.tokens = tokens
        log += "save:${tokens.refreshToken}"
    }

    override fun clear() {
        tokens = null
        log += "clear"
    }

    override fun dropPassword() {
        passwordDropped = true
        log += "dropPassword"
    }
}
