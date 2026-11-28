package com.iquintero.kavern.core.network.grimmory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GrimmoryCatalogTest {
    private val origin =
        okhttp3.HttpUrl
            .Builder()
            .scheme("https")
            .host("books.test")
            .build()

    private fun catalog(
        server: FakeServer,
        store: File?,
    ) = GrimmoryCatalog(GrimmoryClient(server.client, server.client, origin, GrimmoryAccount("reader", "x")), store)

    @Test
    fun `a held book is found in the kept list without asking the server`() {
        val store = File.createTempFile("books", ".json").apply { writeText("""[{"id":8,"title":"Red Rising","readProgress":19}]""") }
        val server = FakeServer { Triple(500, "", emptyMap()) }
        val book = catalog(server, store).heldBook(8)
        assertEquals("Red Rising", book?.title)
        assertTrue("the server was not asked", server.requests.isEmpty())
        store.delete()
    }

    @Test
    fun `a book that is not held is not found and the server is still not asked`() {
        val server = FakeServer { Triple(500, "", emptyMap()) }
        assertNull(catalog(server, null).heldBook(8))
        assertTrue(server.requests.isEmpty())
    }
}
