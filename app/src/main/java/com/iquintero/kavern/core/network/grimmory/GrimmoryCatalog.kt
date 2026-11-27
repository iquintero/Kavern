package com.iquintero.kavern.core.network.grimmory

import java.io.File
import java.io.IOException

/** One Grimmory book, as the app lists it. */
internal data class GrimmoryBook(
    val id: Int,
    val title: String,
    val authors: List<String>,
    val seriesName: String?,
    val seriesNumber: Float?,
    val libraryId: Int?,
    val addedOn: String?,
    val lastReadTime: String?,
    /** 0 to 100. */
    val progress: Float,
    val fileType: String?,
) {
    /** Started and not yet finished. */
    val inProgress: Boolean get() = progress > 0f && progress < FINISHED

    /** Kavita's format ids: 1 archive, 2 unknown, 3 epub, 4 pdf. */
    val format: Int
        get() =
            when (fileType?.uppercase()) {
                "EPUB" -> FORMAT_EPUB
                "PDF" -> FORMAT_PDF
                "CBX", "CBZ", "CBR", "CB7" -> FORMAT_ARCHIVE
                else -> FORMAT_UNKNOWN
            }

    companion object {
        const val FORMAT_ARCHIVE = 1
        const val FORMAT_UNKNOWN = 2
        const val FORMAT_EPUB = 3
        const val FORMAT_PDF = 4

        /** From this progress on a book counts as read. */
        const val FINISHED = 99f

        /** The pages a series reports: its progress in hundredths. */
        const val UNKNOWN_PAGES = 100

        fun from(json: Json): GrimmoryBook? {
            val id = json.int("id") ?: return null
            return GrimmoryBook(
                id = id,
                title = json.str("title") ?: "Book $id",
                authors = json.strings("authors"),
                seriesName = json.str("seriesName"),
                seriesNumber = json.num("seriesNumber")?.toFloat(),
                libraryId = json.int("libraryId"),
                addedOn = json.str("addedOn"),
                lastReadTime = json.str("lastReadTime"),
                progress = (json.num("readProgress") ?: 0.0).toFloat().coerceIn(0f, 100f),
                fileType = json.str("primaryFileType"),
            )
        }
    }
}

/**
 * The library as the app sees it. Books are loaded once and kept for a short while, since every list on the home page
 * is drawn from the same few hundred of them.
 */
internal class GrimmoryCatalog(
    private val client: GrimmoryClient,
    /** Where the last answer is kept, so that the books can still be listed without the server. */
    private val store: File? = null,
) {
    private var cached: List<GrimmoryBook> = emptyList()
    private var loadedAt = 0L

    /** True while the books shown are the ones kept from the last time the server answered. */
    @Volatile
    var fromStore: Boolean = false
        private set

    @Synchronized
    fun books(): List<GrimmoryBook> {
        val now = System.currentTimeMillis()
        if (cached.isEmpty() || now - loadedAt > CACHE_MS) {
            cached = loadAll()
            loadedAt = now
        }
        return cached
    }

    @Synchronized
    fun forget() {
        cached = emptyList()
    }

    fun book(id: Int): GrimmoryBook? = books().firstOrNull { it.id == id } ?: GrimmoryBook.from(client.getObject("api/v1/app/books/$id"))

    /** The books of the series this one belongs to, in reading order, or just itself when it has none. */
    fun siblings(book: GrimmoryBook): List<GrimmoryBook> {
        val name = book.seriesName ?: return listOf(book)
        return books()
            .filter { it.seriesName == name }
            .sortedBy { it.seriesNumber ?: Float.MAX_VALUE }
            .ifEmpty { listOf(book) }
    }

    fun libraries(): List<Json> = client.getList("api/v1/libraries")

    private fun loadAll(): List<GrimmoryBook> =
        try {
            val raw = mutableListOf<Json>()
            var page = 0
            while (true) {
                val body = client.getObject("api/v1/app/books", mapOf("page" to page.toString(), "size" to PAGE_SIZE.toString()))
                raw += body.objects("content")
                if (body["hasNext"] != true || page >= MAX_PAGES) break
                page++
            }
            fromStore = false
            runCatching { store?.also { it.parentFile?.mkdirs() }?.writeText(GrimmoryJson.write(raw)) }
            raw.mapNotNull { GrimmoryBook.from(it) }
        } catch (e: GrimmoryHttpException) {
            // A server that answers with an error is not one the app is offline from: only a lost connection falls back.
            throw e
        } catch (e: IOException) {
            val kept = readStore() ?: throw e
            fromStore = true
            kept
        }

    @Suppress("UNCHECKED_CAST")
    private fun readStore(): List<GrimmoryBook>? =
        runCatching {
            val text = store?.takeIf { it.isFile }?.readText() ?: return null
            (GrimmoryJson.parse(text) as? List<Any?>).orEmpty().filterIsInstance<Map<String, Any?>>().mapNotNull { GrimmoryBook.from(it) }
        }.getOrNull()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val PAGE_SIZE = 50
        const val MAX_PAGES = 200
        const val CACHE_MS = 60_000L
    }
}
