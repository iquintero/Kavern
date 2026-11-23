package com.iquintero.kavern.core.logging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class LoggingManagerScrubTest {
    @Test
    fun scrubRemovesKeysAndHosts() {
        val out = LoggingManager.scrub("GET https://kavita.example.com/api/x?apiKey=SECRET123 from 192.168.1.10:5000")
        assertFalse(out, out.contains("SECRET123"))
        assertFalse(out, out.contains("kavita.example.com"))
        assertFalse(out, out.contains("192.168.1.10"))
    }

    @Test
    fun stackTracesAreScrubbedToo() {
        val error = IOException("failed https://kavita.example.com/api/book/1?apiKey=SECRET123")
        val out = LoggingManager.scrubbedMessage("Request failed", error)
        assertTrue(out.startsWith("Request failed"))
        assertTrue(out.contains("IOException"))
        assertFalse(out, out.contains("SECRET123"))
        assertFalse(out, out.contains("kavita.example.com"))
    }

    @Test
    fun messageWithoutThrowableIsJustScrubbed() {
        val out = LoggingManager.scrubbedMessage("token=abc", null)
        assertFalse(out.contains("abc"))
    }
}
