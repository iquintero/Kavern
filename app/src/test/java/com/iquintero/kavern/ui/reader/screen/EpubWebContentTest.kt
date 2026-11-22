package com.iquintero.kavern.ui.reader.screen

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubWebContentTest {
    @Test
    fun `wraps everything in the body in one book-content element, in order`() {
        val document = Jsoup.parse("<div class=\" \"><style>.book-content h2{color:red}</style><h2>A</h2><p>B</p></div>text")
        wrapInBookContent(document)

        val children = document.body().children()
        assertEquals(1, children.size)
        assertEquals("book-content", children[0].className())
        assertEquals("A", document.selectFirst(".book-content h2")?.text())
        assertEquals("A B text", document.body().text())
    }

    @Test
    fun `an empty body still gets the wrapper`() {
        val document = Jsoup.parse("")
        wrapInBookContent(document)
        assertEquals(1, document.select("body > div.book-content").size)
    }

    @Test
    fun `layout css neutralises the book's page box and applies the chosen alignment to plain paragraphs`() {
        val css = bookContentLayoutCss("justify")
        listOf("width: auto", "max-width: none", "margin: 0", "background: transparent").forEach {
            assertTrue(css.contains("$it !important"))
        }
        assertTrue(css.contains(".book-content p:not([class]) { text-align: justify !important; }"))
    }

    @Test
    fun `style elements get their urls rewritten and other elements are untouched`() {
        val document =
            Jsoup.parse(
                "<style>@font-face{src:url(//ok/f.otf)}</style><style>p{background:url(//no/x.png)}</style><p>url(//ok/p)</p>",
            )
        document.select("style, p").forEach { rewriteStyleElement(it) { url -> if (url.startsWith("//ok")) "mapped" else null } }

        val styles = document.select("style").map { it.data() }
        assertEquals("@font-face{src:url(mapped)}", styles[0])
        assertEquals("p{background:url(//no/x.png)}", styles[1])
        assertEquals("url(//ok/p)", document.selectFirst("p")?.text())
    }

    @Test
    fun `withoutApiKey drops only the api key`() {
        val url = "https://h/api/book/1/book-resources?apiKey=secret&file=img%2Fa.png&APIKEY=x".toHttpUrl()
        assertEquals("https://h/api/book/1/book-resources?file=img%2Fa.png", url.withoutApiKey().toString())
    }
}
