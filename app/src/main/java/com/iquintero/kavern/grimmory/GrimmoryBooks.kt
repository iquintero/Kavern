package com.iquintero.kavern.grimmory

import com.iquintero.kavern.core.network.ServerReachability
import com.iquintero.kavern.core.network.grimmory.GrimmoryBook
import com.iquintero.kavern.core.network.grimmory.GrimmoryServer
import com.iquintero.kavern.core.network.grimmory.GrimmoryServers
import com.iquintero.kavern.core.network.grimmory.GrimmorySession
import com.iquintero.kavern.core.network.grimmory.num
import com.iquintero.kavern.core.network.grimmory.str
import com.iquintero.kavern.core.network.grimmory.strings
import com.iquintero.kavern.core.storage.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A library on the server. */
internal data class GrimmoryLibraryInfo(
    val id: Int,
    val name: String,
)

/** What the server knows about a book beyond what lists carry. */
internal data class GrimmoryBookDetails(
    val description: String?,
    val publisher: String?,
    val year: String?,
    val language: String?,
    /** The pages of the printed book, when the server knows them. */
    val pageCount: Int?,
    val categories: List<String>,
    val readStatus: String?,
)

/**
 * The books on a Grimmory server, as the Grimmory screens use them. Books are what the app is about: there is no series,
 * volume or chapter to go through to reach one.
 */
internal class GrimmoryBooks(
    private val server: GrimmoryServer,
    private val serverUrl: String,
) {
    /** True while the lists come from what was kept the last time the server answered, because it cannot be reached. */
    val isOffline: Boolean get() = server.catalog.fromStore || ServerReachability.isUnreachable()

    suspend fun all(): List<GrimmoryBook> = io { server.catalog.books() }

    /** The books that were started and not finished, the most recently read first. */
    suspend fun inProgress(): List<GrimmoryBook> = all().filter { it.inProgress }.sortedByDescending { it.lastReadTime }

    suspend fun recentlyAdded(limit: Int = RECENT): List<GrimmoryBook> = all().sortedByDescending { it.addedOn }.take(limit)

    suspend fun book(id: Int): GrimmoryBook? = io { server.catalog.book(id) }

    /** The books of the series [book] belongs to, in reading order; just itself when it has none. */
    suspend fun siblings(book: GrimmoryBook): List<GrimmoryBook> = io { server.catalog.siblings(book) }

    /** The books of the series called [name], in reading order. */
    suspend fun inSeries(name: String): List<GrimmoryBook> = all().filter { it.seriesName == name }.sortedBy { it.seriesNumber ?: Float.MAX_VALUE }

    suspend fun libraries(): List<GrimmoryLibraryInfo> =
        io {
            server.catalog.libraries().mapNotNull { library ->
                val id = library.num("id")?.toInt() ?: return@mapNotNull null
                GrimmoryLibraryInfo(id, library.str("name") ?: "Library $id")
            }
        }

    suspend fun details(id: Int): GrimmoryBookDetails =
        io {
            val body = server.client.getObject("api/v1/app/books/$id")
            GrimmoryBookDetails(
                description = body.str("description"),
                publisher = body.str("publisher"),
                year = body.str("publishedDate")?.take(YEAR_LENGTH) ?: body.num("publishedYear")?.toInt()?.toString(),
                language = body.str("language"),
                pageCount = body.num("pageCount")?.toInt()?.takeIf { it > 0 },
                categories = body.strings("categories"),
                readStatus = body.str("readStatus"),
            )
        }

    /** Marks the book read or unread on the server. */
    suspend fun setRead(
        id: Int,
        read: Boolean,
    ) = io {
        server.client.putJson("api/v1/app/books/$id/status", mapOf("status" to if (read) "READ" else "UNREAD"))
        server.catalog.forget()
    }

    /** How many parts [book] is downloaded in, or null when the server cannot read it (it is not an EPUB, or is broken). */
    suspend fun parts(book: GrimmoryBook): Int? = io { server.epubs.pagesOf(book) }

    /** The reading sessions of the books that have been read, newest first. */
    suspend fun sessions(): List<GrimmorySession> = io { server.sessions.history() }

    /** Starts the next load of the library from the server. */
    fun refresh() = server.catalog.forget()

    fun coverUrl(id: Int): String = "${serverUrl.trimEnd('/')}/api/v1/media/book/$id/cover"

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    companion object {
        private const val RECENT = 20
        private const val YEAR_LENGTH = 4

        fun of(config: AppConfig): GrimmoryBooks = GrimmoryBooks(GrimmoryServers.serverFor(config), config.serverUrl)
    }
}
