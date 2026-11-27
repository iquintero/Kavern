package com.iquintero.kavern.core.network.grimmory

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** The parts of an EPUB the app needs: its spine files, which are its pages, and its table of contents. */
internal class GrimmoryEpub(
    val spine: List<String>,
    /** Entries with a label, an href into the spine and children. */
    val contents: List<Json>,
) {
    /** The index of the spine file that [href] points into (an anchor is ignored), or -1. */
    fun pageOf(href: String): Int {
        val file = href.substringBefore('#')
        return spine.indexOfFirst { it == file || it.endsWith("/$file") || file.endsWith("/$it") }
    }
}

/** Reads and remembers the structure of the EPUBs on a Grimmory server. */
internal class GrimmoryEpubs(
    private val client: GrimmoryClient,
) {
    private val epubs = ConcurrentHashMap<Int, GrimmoryEpub>()

    fun of(bookId: Int): GrimmoryEpub = epubs.getOrPut(bookId) { load(bookId) }

    /** The spine of [book] when it is an EPUB, else nothing to read. */
    fun spineOf(book: GrimmoryBook): List<String> = if (book.format == GrimmoryBook.FORMAT_EPUB) of(book.id).spine else emptyList()

    /** How many pages an EPUB has, or null for other formats or when the server cannot read it. */
    fun pagesOf(book: GrimmoryBook): Int? =
        try {
            spineOf(book).size.takeIf { it > 0 }
        } catch (_: IOException) {
            null
        }

    private fun load(bookId: Int): GrimmoryEpub {
        val info = client.getObject("api/v1/epub/$bookId/info")
        val root = info.str("rootPath").orEmpty().trim('/')
        val spine = info.objects("spine").mapNotNull { it.str("href") }.map { if (root.isEmpty() || it.startsWith("$root/")) it else "$root/$it" }
        // Grimmory's own table of contents repeats the book title for every entry, so the book's own is read.
        return GrimmoryEpub(spine, readContents(bookId, info) ?: info.obj("toc")?.objects("children").orEmpty())
    }

    /** The EPUB's real table of contents, from its nav document (EPUB 3) or NCX (EPUB 2). */
    private fun readContents(
        bookId: Int,
        info: Json,
    ): List<Json>? {
        val manifest = info.objects("manifest")
        val nav = manifest.firstOrNull { it.str("properties")?.split(' ')?.contains("nav") == true }
        val ncx = manifest.firstOrNull { it.str("mediaType") == "application/x-dtbncx+xml" }
        val source = nav ?: ncx ?: return null
        val href = source.str("href") ?: return null
        return try {
            val text = client.open("api/v1/epub/$bookId/file/${href.trimStart('/')}").use { it.body?.string().orEmpty() }
            val xml = Jsoup.parse(text, "", Parser.xmlParser())
            val base = href.substringBeforeLast('/', "")
            val items = if (source === nav) navItems(xml, base) else ncxItems(xml.selectFirst("navMap"), base)
            items.takeIf { it.isNotEmpty() }
        } catch (_: IOException) {
            null
        }
    }

    private fun ncxItems(
        parent: Element?,
        base: String,
    ): List<Json> =
        parent
            ?.children()
            ?.filter { it.tagName() == "navPoint" }
            ?.map { point ->
                mapOf(
                    "label" to
                        point
                            .selectFirst("navLabel > text")
                            ?.text()
                            .orEmpty()
                            .trim(),
                    "href" to joinPath(base, point.selectFirst("content")?.attr("src").orEmpty()),
                    "children" to ncxItems(point, base),
                )
            }.orEmpty()

    private fun navItems(
        xml: Document,
        base: String,
    ): List<Json> {
        val toc = xml.select("nav").firstOrNull { it.attr("epub:type") == "toc" } ?: xml.selectFirst("nav")
        return listItems(toc?.selectFirst("ol"), base)
    }

    private fun listItems(
        list: Element?,
        base: String,
    ): List<Json> =
        list
            ?.children()
            ?.filter { it.tagName() == "li" }
            ?.map { item ->
                val link = item.children().firstOrNull { it.tagName() == "a" || it.tagName() == "span" }
                mapOf(
                    "label" to link?.text().orEmpty().trim(),
                    "href" to joinPath(base, link?.attr("href").orEmpty()),
                    "children" to listItems(item.selectFirst("ol"), base),
                )
            }.orEmpty()

    private fun joinPath(
        base: String,
        path: String,
    ): String = if (path.isEmpty() || path.startsWith("/") || base.isEmpty() || path.startsWith("$base/")) path.trimStart('/') else "$base/$path"
}
