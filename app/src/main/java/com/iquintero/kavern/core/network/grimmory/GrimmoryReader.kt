package com.iquintero.kavern.core.network.grimmory

import com.iquintero.kavern.data.api.dto.BookChapterItemDto
import com.iquintero.kavern.data.api.dto.BookInfoDto
import com.iquintero.kavern.data.api.dto.MarkMultipleDto
import com.iquintero.kavern.data.api.dto.MarkSeriesDto
import com.iquintero.kavern.data.api.dto.MarkVolumeReadDto
import com.iquintero.kavern.data.api.dto.ReaderProgressDto
import com.iquintero.kavern.data.api.dto.TimeLeftDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URI
import java.net.URLEncoder

/**
 * The reading side of the translation: a book's pages, its contents, where the reader is, and the file itself.
 *
 * An EPUB's "pages" are its spine files, as they are in Kavita, so a page number is an index into the spine.
 */
internal class GrimmoryReader(
    private val client: GrimmoryClient,
    private val catalog: GrimmoryCatalog,
    private val epubs: GrimmoryEpubs,
) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    fun answer(
        request: Request,
        json: (Request, Any) -> Response,
        reply: (Request, Int, String) -> Response,
    ): Response? {
        val path =
            request.url.encodedPath
                .lowercase()
                .trimEnd('/')
        val segments = path.split('/')

        fun int(name: String) = request.url.queryParameter(name)?.toIntOrNull()
        val bookRoute = segments.getOrNull(2) == "book" && segments.size == 5
        val bookId = if (bookRoute) segments[3].toIntOrNull() else null
        return when {
            bookId != null && segments[4] == "book-info" -> json(request, bookInfo(bookId))
            bookId != null && segments[4] == "chapters" -> json(request, contents(bookId))
            bookId != null && segments[4] == "book-page" -> reply(request, OK, page(bookId, int("page") ?: 0))
            bookId != null && segments[4] == "book-resources" -> resource(request, bookId, request.url.queryParameter("file").orEmpty())
            path == "/api/reader/get-progress" -> int("chapterId")?.let { json(request, progress(it)) }
            path == "/api/reader/progress" -> saveProgress(request, reply)
            path == "/api/download/chapter" -> int("chapterId")?.let { download(request, it) }
            path == "/api/reader/next-chapter" -> json(request, neighbour(int("currentChapterId"), 1))
            path == "/api/reader/prev-chapter" -> json(request, neighbour(int("currentChapterId"), -1))
            path == "/api/reader/time-left-for-chapter" -> json(request, TimeLeftDto())
            path == "/api/reader/mark-read" -> mark(request, reply, "READ")
            path == "/api/reader/mark-unread" -> mark(request, reply, "UNREAD")
            path == "/api/reader/mark-volume-read" -> mark(request, reply, "READ")
            path == "/api/reader/mark-volume-unread" -> mark(request, reply, "UNREAD")
            path == "/api/reader/mark-multiple-read" -> mark(request, reply, "READ")
            path == "/api/reader/mark-multiple-unread" -> mark(request, reply, "UNREAD")
            else -> null
        }
    }

    private fun bookInfo(bookId: Int): BookInfoDto {
        val book = catalog.book(bookId) ?: throw GrimmoryHttpException(NOT_FOUND)
        return BookInfoDto(
            pages = if (book.format == GrimmoryBook.FORMAT_EPUB) epubs.of(bookId).spine.size else null,
            seriesId = book.id,
            volumeId = book.id,
            libraryId = book.libraryId,
            bookTitle = book.title,
            chapterTitle = book.title,
            seriesName = book.seriesName ?: book.title,
        )
    }

    private fun contents(bookId: Int): List<BookChapterItemDto> {
        val epub = epubs.of(bookId)

        fun convert(item: Json): BookChapterItemDto {
            val href = item.str("href").orEmpty()
            return BookChapterItemDto(
                title = item.str("label"),
                part = href.substringAfter('#', "").ifEmpty { null },
                page = epub.pageOf(href).coerceAtLeast(0),
                children = item.objects("children").map(::convert),
            )
        }
        return epub.contents.map(::convert)
    }

    /** The spine file as one page, with everything it refers to pointing at [resource]. */
    private fun page(
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
                val css = element.data()
                element.empty().appendChild(org.jsoup.nodes.DataNode(rewriteUrls(css, bookId, file)))
            }
        }
        return document.outerHtml()
    }

    private fun resource(
        request: Request,
        bookId: Int,
        file: String,
    ): Response? {
        if (file.isEmpty() || file.split('/').any { it == ".." }) return null
        val upstream = client.open("api/v1/epub/$bookId/file/${encodePath(file)}")
        val type = upstream.header("Content-Type")
        val isCss = file.endsWith(".css", ignoreCase = true) || type?.contains("css") == true
        val body = upstream.body ?: return null
        if (!isCss) return answerWith(request, type, body)
        // The URLs inside a stylesheet are relative to the stylesheet, so they are pointed at the book here.
        val css = rewriteUrls(body.use { it.string() }, bookId, file)
        return answerWith(request, "text/css", css.toResponseBody("text/css".toMediaType()))
    }

    private fun answerWith(
        request: Request,
        type: String?,
        body: okhttp3.ResponseBody,
    ): Response =
        Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(OK)
            .message("OK")
            .apply { if (type != null) header("Content-Type", type) }
            .body(body)
            .build()

    private fun resourceUrl(
        bookId: Int,
        from: String,
        value: String,
    ): String {
        val resolved = runCatching { URI("/" + from.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }).resolve(URI(value.replace(" ", "%20"))) }.getOrNull()
        val target = resolved?.path?.trimStart('/') ?: value
        val fragment = resolved?.rawFragment?.let { "#$it" }.orEmpty()
        return "api/book/$bookId/book-resources?file=${URLEncoder.encode(target, "UTF-8")}$fragment"
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

    private fun progress(bookId: Int): ReaderProgressDto {
        val book = catalog.book(bookId) ?: throw GrimmoryHttpException(NOT_FOUND)
        val answer = client.getObject("api/v1/app/books/$bookId/progress")
        val saved = answer.obj("epubProgress")
        val spine = epubs.spineOf(book)
        val byHref =
            saved
                ?.str("href")
                ?.takeIf { spine.isNotEmpty() }
                ?.let { epubs.of(bookId).pageOf(it) }
                ?.takeIf { it >= 0 }
        // Progress saved elsewhere may only be a share of the book, which is turned into the page it falls on.
        val share = saved?.num("percentage") ?: answer.num("readProgress")
        val byPercent = share?.let { Math.round(it / PERCENT * spine.size).toInt().coerceIn(0, (spine.size - 1).coerceAtLeast(0)) }
        return ReaderProgressDto(
            chapterId = bookId,
            pageNum = byHref ?: byPercent ?: 0,
            bookScrollId = saved?.str("cfi")?.removePrefix(SCROLL_PREFIX)?.takeIf { it != saved.str("cfi") },
            seriesId = book.id,
            volumeId = book.id,
            libraryId = book.libraryId,
            lastModifiedUtc = saved?.str("updatedAt") ?: answer.str("lastReadTime")?.removeSuffix("Z"),
        )
    }

    private fun saveProgress(
        request: Request,
        reply: (Request, Int, String) -> Response,
    ): Response {
        val buffer = Buffer().also { request.body?.writeTo(it) }
        val body = moshi.adapter(ReaderProgressDto::class.java).fromJson(buffer.readUtf8()) ?: return reply(request, BAD_REQUEST, "")
        val bookId = body.chapterId
        val book = catalog.book(bookId) ?: throw GrimmoryHttpException(NOT_FOUND)
        val spine = epubs.spineOf(book)
        val page = body.pageNum ?: 0
        val percent = if (spine.isEmpty()) 0f else (page.coerceIn(0, spine.size) * PERCENT / spine.size).toFloat()
        val fileId =
            client
                .getObject("api/v1/app/books/$bookId")
                .objects("files")
                .firstOrNull()
                ?.int("id") ?: bookId
        val progress =
            mutableMapOf<String, Any?>("bookFileId" to fileId, "progressPercent" to percent)
        spine.getOrNull(page)?.let { progress["positionHref"] = it }
        body.bookScrollId?.takeIf { it.isNotBlank() }?.let { progress["positionData"] = SCROLL_PREFIX + it }
        client.putJson("api/v1/app/books/$bookId/progress", mapOf("fileProgress" to progress))
        catalog.forget()
        return reply(request, OK, "")
    }

    private fun download(
        request: Request,
        bookId: Int,
    ): Response? {
        val upstream = client.open("api/v1/books/$bookId/download")
        val body = upstream.body ?: return null
        return Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(OK)
            .message("OK")
            .apply {
                upstream.header("Content-Type")?.let { header("Content-Type", it) }
                upstream.header("Content-Disposition")?.let { header("Content-Disposition", it) }
                upstream.header("Content-Length")?.let { header("Content-Length", it) }
            }.body(body)
            .build()
    }

    /** The id of the book before or after this one in its series, or -1 when there is none. */
    private fun neighbour(
        bookId: Int?,
        step: Int,
    ): Int {
        val current = bookId?.let { catalog.book(it) } ?: return NO_NEIGHBOUR
        val books = catalog.siblings(current)
        val index = books.indexOfFirst { it.id == bookId }
        return books.getOrNull(index + step)?.id?.takeIf { index >= 0 } ?: NO_NEIGHBOUR
    }

    private fun mark(
        request: Request,
        reply: (Request, Int, String) -> Response,
        status: String,
    ): Response {
        val text = Buffer().also { request.body?.writeTo(it) }.readUtf8()
        val ids =
            when {
                "chapterIds" in text ->
                    moshi
                        .adapter(MarkMultipleDto::class.java)
                        .fromJson(text)
                        ?.let { it.chapterIds + it.volumeIds }
                        .orEmpty()
                "volumeId" in text -> listOfNotNull(moshi.adapter(MarkVolumeReadDto::class.java).fromJson(text)?.volumeId)
                else ->
                    moshi
                        .adapter(MarkSeriesDto::class.java)
                        .fromJson(text)
                        ?.let { listOf(it.seriesId) }
                        .orEmpty()
            }
        ids.forEach { client.putJson("api/v1/app/books/$it/status", mapOf("status" to status)) }
        catalog.forget()
        return reply(request, OK, "")
    }

    private fun encodePath(path: String): String = path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    private companion object {
        const val SCROLL_PREFIX = "kavern-scroll:"
        const val OK = 200
        const val BAD_REQUEST = 400
        const val NOT_FOUND = 404
        const val NO_NEIGHBOUR = -1
        const val PERCENT = 100.0
        val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
        val CSS_URL = Regex("""url\(\s*(["']?)([^"')]+)\1\s*\)""", RegexOption.IGNORE_CASE)
    }
}
