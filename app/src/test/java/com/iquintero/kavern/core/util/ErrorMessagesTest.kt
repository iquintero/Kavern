package com.iquintero.kavern.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorMessagesTest {
    @Test
    fun unreachableServerFailuresAreConnectivityErrors() {
        assertTrue(UnknownHostException("demo.example.com").isConnectivityError())
        assertTrue(ConnectException("refused").isConnectivityError())
        assertTrue(NoRouteToHostException("no route").isConnectivityError())
        assertTrue(SocketTimeoutException("timeout").isConnectivityError())
    }

    @Test
    fun otherFailuresAreNot() {
        assertFalse(IOException("disk full").isConnectivityError())
        assertFalse(IllegalStateException("boom").isConnectivityError())
    }
}
