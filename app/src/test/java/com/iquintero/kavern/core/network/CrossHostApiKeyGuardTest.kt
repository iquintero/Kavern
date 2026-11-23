package com.iquintero.kavern.core.network

import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Proxy

class CrossHostApiKeyGuardTest {
    private fun sentHeader(
        originalUrl: String,
        hopUrl: String,
    ): String? {
        val original =
            Request
                .Builder()
                .url(originalUrl)
                .header(API_KEY_HEADER, "secret")
                .build()
        val hop =
            Request
                .Builder()
                .url(hopUrl)
                .header(API_KEY_HEADER, "secret")
                .build()
        var sent: Request? = null
        val call =
            Proxy.newProxyInstance(Call::class.java.classLoader, arrayOf(Call::class.java)) { _, method, _ ->
                if (method.name == "request") original else error("unexpected ${method.name}")
            } as Call
        val chain =
            Proxy.newProxyInstance(Interceptor.Chain::class.java.classLoader, arrayOf(Interceptor.Chain::class.java)) { _, method, args ->
                when (method.name) {
                    "request" -> hop
                    "call" -> call
                    "proceed" -> {
                        val forwarded = args[0] as Request
                        sent = forwarded
                        Response
                            .Builder()
                            .request(forwarded)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .build()
                    }
                    else -> error("unexpected ${method.name}")
                }
            } as Interceptor.Chain
        CrossHostApiKeyGuard.intercept(chain)
        return sent!!.header(API_KEY_HEADER)
    }

    @Test
    fun keepsTheKeyOnTheSameHost() {
        assertEquals("secret", sentHeader("http://kavita.local/api/a", "https://kavita.local/api/a"))
    }

    @Test
    fun dropsTheKeyWhenRedirectedToAnotherHost() {
        assertNull(sentHeader("https://kavita.local/api/a", "https://cdn.example.com/file"))
    }

    @Test
    fun hostComparisonIgnoresCase() {
        assertEquals("secret", sentHeader("https://Kavita.Local/api/a", "https://kavita.local/api/b"))
    }
}
