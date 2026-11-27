package com.iquintero.kavern.core.network.grimmory

import com.iquintero.kavern.core.storage.AppConfig
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Runs the Grimmory client against a real server. Only runs when GRIMMORY_URL, GRIMMORY_USER and GRIMMORY_PASS are set.
 * It prints what the server answers; it changes nothing but writing back the place it read.
 */
class GrimmoryLiveTest {
    private fun server(): GrimmoryServer {
        val url = System.getenv("GRIMMORY_URL")
        val user = System.getenv("GRIMMORY_USER")
        val pass = System.getenv("GRIMMORY_PASS")
        assumeTrue("GRIMMORY_URL, GRIMMORY_USER and GRIMMORY_PASS are not set", !url.isNullOrBlank() && !user.isNullOrBlank() && !pass.isNullOrBlank())
        return GrimmoryServers.serverFor(
            AppConfig(
                serverUrl = url!!,
                apiKey =
                    com.iquintero.kavern.core.network.grimmory
                        .grimmorySecret(user!!, pass!!),
                imageApiKey = "",
                userId = 0,
            ),
        )
    }

    @Test
    fun browse() {
        val server = server()
        val books = server.catalog.books()
        books.forEach { println("book ${it.id} ${it.title} progress=${it.progress} format=${it.fileType} library=${it.libraryId} series=${it.seriesName} #${it.seriesNumber}") }
        println("libraries=" + server.catalog.libraries().map { it.str("name") })
        println("sessions=" + server.sessions.history().size)
        println("in progress=" + books.filter { it.inProgress }.map { it.title })
    }

    @Test
    fun read() {
        val server = server()
        val book = server.catalog.books().firstOrNull { it.format == GrimmoryBook.FORMAT_EPUB && it.progress > 0f } ?: return
        val reading = server.reading
        val parts = reading.parts(book)
        println("${book.title}: parts=$parts contents=" + reading.contents(book.id).take(5).map { it.title })
        val place = reading.place(book.id)
        println("place=$place")
        println("part ${place?.page ?: 0} has " + reading.page(book.id, place?.page ?: 0).length + " characters")
        // Writes back where the reader already was, so that nothing changes.
        if (place != null) reading.save(book.id, place.page, place.scrollId)
        println("next=" + reading.neighbour(book.id, 1) + " previous=" + reading.neighbour(book.id, -1))
    }
}
