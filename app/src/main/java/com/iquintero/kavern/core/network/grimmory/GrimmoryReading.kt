package com.iquintero.kavern.core.network.grimmory

import com.iquintero.kavern.domain.model.ReaderTocItem
import org.jsoup.Jsoup
import org.jsoup.nodes.DataNode
import java.net.URI
import java.net.URLEncoder

/** Where a reader was in a book, as the server remembers it. */
internal data class GrimmoryPlace(
    /** The EPUB file the reader was in, as an index into its spine. */
    val page: Int,
    /** The element the reader was at inside that file. */
    val scrollId: String?,
    val updatedAtMs: Long,
)

/**
 * Reading a book from Grimmory: its parts, its contents, where the reader is, and moving that on. An EPUB's parts are
 * its spine files, so a part number is an index into the spine.
 */
internal class GrimmoryReading(
    private val server: GrimmoryServer,
) {
    private val client = server.client
    private val catalog = server.catalog
    private val epubs = server.epubs

    /** How many parts the book has, or null for a book the app cannot read. */
    fun parts(book: GrimmoryBook): Int? = epubs.pagesOf(book)

    /** The book's own table of contents, in reading order. */
    fun contents(bookId: Int): List<ReaderTocItem> {
        val epub = epubs.of(bookId)

        fun flatten(
            item: Json,
            depth: Int,
        ): List<ReaderTocItem> {
            val href = item.str("href").orEmpty()
            val title = item.str("label")?.trim().orEmpty()
            val self =
                if (title.isEmpty()) {
                    emptyList()
                } else {
                    listOf(
                        ReaderTocItem(
                            title = title,
                            page = epub.pageOf(href).coerceAtLeast(0),
                            anchor = href.substringAfter('#', "").ifEmpty { null },
                            depth = depth,
                        ),
                    )
                }
            return self + item.objects("children").flatMap { flatten(it, if (self.isEmpty()) depth else depth + 1) }
        }
        return epub.contents.flatMap { flatten(it, 0) }
    }

    /** One part of the book as a page of HTML, with everything it refers to pointing into the book. */
    fun page(
        bookId: Int,
        page: Int,
    ): String {
        val file = epubs.of(bookId).spine.getOrNull(page) ?: throw GrimmoryHttpException(NOT_FOUND)
        val html = client.open("api/v1/epub/$bookId/file/${encodePath(file)}").use { it.body?.string().orEmpty() }
        val document = Jsoup.parse(html)
        document.outputSettings().prettyPrint(false)
        document.getAllElements().forEach { element ->
            for (attribute in listOf("src", "href", "xlink:href", "poster")) {
                if (!element.hasAttr(attribute)) continue
                val value = element.attr(attribute).trim()
                val isLink = element.normalName() == "a"
                when {
                    value.startsWith("#") || value.startsWith("data:") -> Unit
                    SCHEME.containsMatchIn(value) -> element.removeAttr(attribute)
                    // Links to other files of the book are not followed yet.
                    isLink -> element.removeAttr(attribute)
                    else -> element.attr(attribute, resourceUrl(bookId, file, value))
                }
            }
            if (element.hasAttr("style")) element.attr("style", rewriteUrls(element.attr("style"), bookId, file))
            if (element.normalName() == "style") {
                element.empty().appendChild(DataNode(rewriteUrls(element.data(), bookId, file)))
            }
        }
        return document.outerHtml()
    }

    /** Where the server has the reader, or null when it has not seen the book read. */
    fun place(bookId: Int): GrimmoryPlace? {
        val book = catalog.book(bookId) ?: return null
        val answer = client.getObject("api/v1/app/books/$bookId/progress")
        val saved = answer.obj("epubProgress")
        val spine = epubs.spineOf(book)
        val byHref =
            saved
                ?.str("href")
                ?.takeIf { spine.isNotEmpty() }
                ?.let { epubs.of(bookId).pageOf(it) }
                ?.takeIf { it >= 0 }
        // Progress saved elsewhere may only be a share of the book, which is turned into the part it falls in.
        val share = saved?.num("percentage") ?: answer.num("readProgress")
        val byShare = share?.let { Math.round(it / WHOLE * spine.size).toInt().coerceIn(0, (spine.size - 1).coerceAtLeast(0)) }
        val page = byHref ?: byShare ?: return null
        val scroll = saved?.str("cfi")?.takeIf { it.startsWith(SCROLL_PREFIX) }?.removePrefix(SCROLL_PREFIX)
        val updated = saved?.str("updatedAt") ?: answer.str("lastReadTime")
        return GrimmoryPlace(page, scroll, updated?.let(::epochOf) ?: 0L)
    }

    /** Tells the server where the reader is. */
    fun save(
        bookId: Int,
        page: Int,
        scrollId: String?,
    ) {
        val book = catalog.book(bookId) ?: throw GrimmoryHttpException(NOT_FOUND)
        val spine = epubs.spineOf(book)
        val percent = if (spine.isEmpty()) 0f else (page.coerceIn(0, spine.size) * WHOLE / spine.size).toFloat()
        val fileId =
            client
                .getObject("api/v1/app/books/$bookId")
                .objects("files")
                .firstOrNull()
                ?.int("id") ?: bookId
        val progress = mutableMapOf<String, Any?>("bookFileId" to fileId, "progressPercent" to percent)
        spine.getOrNull(page)?.let { progress["positionHref"] = it }
        scrollId?.takeIf { it.isNotBlank() }?.let { progress["positionData"] = SCROLL_PREFIX + it }
        client.putJson("api/v1/app/books/$bookId/progress", mapOf("fileProgress" to progress))
        catalog.forget()
    }

    /** The id of the book before or after this one in its series, or null when there is none. */
    fun neighbour(
        bookId: Int,
        step: Int,
    ): Int? {
        val current = catalog.book(bookId) ?: return null
        val books = catalog.siblings(current)
        val index = books.indexOfFirst { it.id == bookId }
        return books.getOrNull(index + step)?.id?.takeIf { index >= 0 }
    }

    fun setStatus(
        bookIds: List<Int>,
        status: String,
    ) {
        bookIds.forEach { client.putJson("api/v1/app/books/$it/status", mapOf("status" to status)) }
        catalog.forget()
    }

    private fun resourceUrl(
        bookId: Int,
        from: String,
        value: String,
    ): String {
        val folder = from.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val resolved = runCatching { URI("/$folder").resolve(URI(value.replace(" ", "%20"))) }.getOrNull()
        val target = resolved?.path?.trimStart('/') ?: value
        val fragment = resolved?.rawFragment?.let { "#$it" }.orEmpty()
        return "api/v1/epub/$bookId/file/${encodePath(target)}$fragment"
    }

    private fun rewriteUrls(
        css: String,
        bookId: Int,
        from: String,
    ): String =
        css.replace(CSS_URL) { match ->
            val value = match.groupValues[2].trim()
            if (value.startsWith("data:") || value.startsWith("#") || SCHEME.containsMatchIn(value)) {
                match.value
            } else {
                "url(\"${resourceUrl(bookId, from, value)}\")"
            }
        }

    private fun encodePath(path: String): String = path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    private fun epochOf(text: String): Long =
        runCatching {
            java.time.OffsetDateTime
                .parse(
                    if (text.endsWith("Z") ||
                        text.contains('+')
                    ) {
                        text
                    } else {
                        "${text}Z"
                    },
                ).toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)

    companion object {
        const val SCROLL_PREFIX = "kavern-scroll:"
        private const val NOT_FOUND = 404
        private const val WHOLE = 100.0
        private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
        private val CSS_URL = Regex("""url\(\s*(["']?)([^"')]+)\1\s*\)""", RegexOption.IGNORE_CASE)
    }
}
