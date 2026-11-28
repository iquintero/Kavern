package com.iquintero.kavern.core.network.grimmory

import org.junit.Assert.assertFalse
import org.junit.Test

class GrimmoryServersTest {
    @Test
    fun `the client never follows a redirect on its own`() {
        val client = GrimmoryServers.plainClient()
        assertFalse("the password would go wherever a redirect says", client.followRedirects)
        assertFalse("a redirect to plain http would send everything in the clear", client.followSslRedirects)
    }
}
