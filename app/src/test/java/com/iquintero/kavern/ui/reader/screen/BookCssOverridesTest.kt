package com.iquintero.kavern.ui.reader.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookCssOverridesTest {
    // Rules copied from the stylesheet of a real book, where each paragraph class overrides the reader's settings.
    private val realBookCss =
        ".book-content p.TX{font-weight:normal;font-size:0.96em;line-height:1.24;text-align:justify;text-indent:1.38em;margin:0}" +
            ".book-content p.BMH{font-size:1.5em;line-height:1.2;text-align:center;text-indent:0;margin:0 0 1.38em}" +
            ".book-content p.AUSIG{font-size:0.96em;text-align:right;margin:0 0 0 1.38em}"

    @Test
    fun bodyTextLosesItsSizeLineHeightAndJustification() {
        val out = withoutReaderOwnedStyles(realBookCss)
        val body = out.substringAfter("p.TX{").substringBefore("}")
        assertFalse(body, body.contains("font-size"))
        assertFalse(body, body.contains("line-height"))
        assertFalse(body, body.contains("text-align"))
    }

    @Test
    fun theBooksOwnTypographyIsKept() {
        val body = withoutReaderOwnedStyles(realBookCss).substringAfter("p.TX{").substringBefore("}")
        assertTrue(body, body.contains("text-indent:1.38em"))
        assertTrue(body, body.contains("margin:0"))
        assertTrue(body, body.contains("font-weight:normal"))
    }

    @Test
    fun titlesKeepTheirSizeAndCenteringButNotTheirLineHeight() {
        val title = withoutReaderOwnedStyles(realBookCss).substringAfter("p.BMH{").substringBefore("}")
        assertTrue(title, title.contains("font-size:1.5em"))
        assertTrue(title, title.contains("text-align:center"))
        assertFalse(title, title.contains("line-height"))
    }

    @Test
    fun rightAlignedParagraphsStayRightAligned() {
        assertTrue(withoutReaderOwnedStyles(realBookCss).substringAfter("p.AUSIG{").substringBefore("}").contains("text-align:right"))
    }

    @Test
    fun sizesNearBodySizeAreTheReadersToSetOthersAreTheBooks() {
        listOf("1em", "0.9em", "0.96em", "1.04em", "1.1em", "100%", "95%", "medium", "inherit", "1em !important").forEach {
            assertTrue(it, isReaderOwnedDeclaration("font-size", it))
        }
        listOf("0.7em", "0.85em", "1.5em", "3em", "150%", "12px", "large").forEach {
            assertFalse(it, isReaderOwnedDeclaration("font-size", it))
        }
    }

    @Test
    fun fontFamiliesAreTheReadersExceptForCode() {
        assertTrue(isReaderOwnedDeclaration("font-family", "Georgia, serif"))
        assertTrue(isReaderOwnedDeclaration("font-family", "'Palatino Linotype'"))
        assertFalse(isReaderOwnedDeclaration("font-family", "monospace"))
        assertFalse(isReaderOwnedDeclaration("font-family", "'Courier New', Courier"))
        assertFalse(isReaderOwnedDeclaration("font-family", "Consolas"))
    }

    @Test
    fun fontFaceDefinitionsAreLeftAlone() {
        val css = "@font-face{font-family:'Book Serif';src:url(a.ttf);font-style:normal}p.x{font-family:'Book Serif'}"
        val out = withoutReaderOwnedStyles(css)
        assertTrue(out, out.startsWith("@font-face{font-family:'Book Serif';src:url(a.ttf);font-style:normal}"))
        assertFalse(out, out.substringAfter("p.x{").contains("font-family"))
    }

    @Test
    fun rulesInsideMediaQueriesAreHandledToo() {
        val out = withoutReaderOwnedStyles("@media screen{p.a{text-align:justify;color:red}}")
        assertEquals("@media screen{p.a{color:red}}", out)
    }

    @Test
    fun inlineStyleAttributesAreFilteredLikeRules() {
        assertEquals("color:red;text-indent:2em", withoutReaderOwnedDeclarations("text-align: justify; color:red; line-height:1.2; text-indent:2em"))
        assertEquals("", withoutReaderOwnedDeclarations("text-align:left"))
    }

    @Test
    fun valuesWithSemicolonsInsideParenthesesAreNotSplit() {
        val out = withoutReaderOwnedDeclarations("background:url(data:image/png;base64,AAAA);line-height:1.2")
        assertEquals("background:url(data:image/png;base64,AAAA)", out)
    }

    @Test
    fun theWholeBookPageIsRewrittenWhenItIsPrepared() {
        val html =
            "<html><head><style>.book-content p.TX{font-size:0.96em;text-align:justify;text-indent:1.38em}</style></head>" +
                "<body><p class=\"TX\" style=\"line-height:1.2;color:red\">Hello</p></body></html>"
        val document = org.jsoup.Jsoup.parse(html)
        document.select("style").forEach { rewriteStyleElement(it) { url -> url } }
        document.select("[style]").forEach { it.attr("style", withoutReaderOwnedDeclarations(it.attr("style"))) }
        assertEquals(".book-content p.TX{text-indent:1.38em}", document.selectFirst("style")!!.data())
        assertEquals("color:red", document.selectFirst("p")!!.attr("style"))
    }
}
