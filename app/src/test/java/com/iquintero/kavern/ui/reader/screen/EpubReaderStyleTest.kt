package com.iquintero.kavern.ui.reader.screen

import com.iquintero.kavern.core.storage.ReaderThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class EpubReaderStyleTest {
    private val settings =
        ReaderCssSettings(
            fontFaceCss = "@font-face{font-family:Serif}",
            fontFamily = "Serif",
            fontSize = 18f,
            lineHeight = 1.5f,
            paddingPx = 40f,
            textAlignCss = "justify",
            backgroundColor = "#121212",
            textColor = "#E0E0E0",
            paged = false,
        )

    @Test
    fun `reader css carries the font, sizes, alignment and theme`() {
        val css = readerCss(settings)
        assertTrue(css.contains("@font-face{font-family:Serif}"))
        assertTrue(css.contains("font-family: 'Serif' !important"))
        assertTrue(css.contains("font-size: 18.0px !important"))
        assertTrue(css.contains("line-height: 1.5 !important"))
        assertTrue(css.contains("text-align: justify !important"))
        assertTrue(css.contains("background: #121212 !important"))
        assertTrue(css.contains("color: #E0E0E0 !important"))
    }

    @Test
    fun `top and bottom padding are half the side padding`() {
        val css = readerCss(settings)
        assertTrue(css.contains("padding-left: 40.0px !important"))
        assertTrue(css.contains("padding-right: 40.0px !important"))
        assertTrue(css.contains("padding-top: 20.0px !important"))
        assertTrue(css.contains("padding-bottom: 20.0px !important"))
    }

    @Test
    fun `reader css keeps the book's page box and links out of the layout`() {
        val css = readerCss(settings)
        assertTrue(css.contains(bookContentLayoutCss("justify")))
        assertTrue(css.contains("pointer-events: none"))
    }

    @Test
    fun `code wraps only in paged mode`() {
        assertFalse(readerCss(settings).contains("pre-wrap"))
        assertTrue(readerCss(settings.copy(paged = true)).contains("white-space: pre-wrap !important"))
    }

    @Test
    fun `every theme has a distinct readable colour pair`() {
        val pairs = ReaderThemeMode.entries.map { themeColorsFor(it) }
        assertEquals(ReaderThemeMode.entries.size, pairs.size)
        pairs.forEach { (background, text) ->
            assertTrue(background.matches(Regex("#[0-9A-F]{6}")))
            assertTrue(text.matches(Regex("#[0-9A-F]{6}")))
            assertFalse(background == text)
        }
        assertEquals(pairs.size, pairs.toSet().size)
    }

    @Test
    fun `scroll anchor script reports both position formats the restore script understands`() {
        assertTrue(EPUB_SCROLL_ANCHOR_JS.contains("'idx:' + bestIdx"))
        assertTrue(EPUB_SCROLL_ANCHOR_JS.contains("'id(\\\"' + best.id"))
        assertNotNull(EPUB_SCROLL_ANCHOR_JS.trim().takeIf { it.startsWith("(function()") && it.endsWith("})();") })
    }

    @Test
    fun `page text joins the position and the page title`() {
        assertEquals("Chapter 3 / 10 – Intro", pageText(2, 10, "Intro", "Chapter"))
        assertEquals("Chapter 3 / 10", pageText(2, 10, null, "Chapter"))
        assertEquals("Chapter 3 / 10", pageText(2, 10, "  ", "Chapter"))
        assertEquals("Intro", pageText(0, 0, "Intro", "Chapter"))
        assertEquals("", pageText(0, 0, null, "Chapter"))
    }

    @Test
    fun `time left is formatted with one decimal, and is absent without an estimate`() {
        assertEquals("Time left: 2.8 h", formatTimeLeft("Time left", 2.76f, Locale.US))
        assertEquals("Time left: 0.5 h", formatTimeLeft("Time left", 0.5, Locale.US))
        assertEquals("Time left: 2,8 h", formatTimeLeft("Time left", 2.8f, Locale.GERMANY))
        assertEquals(null, formatTimeLeft("Time left", null))
    }

    @Test
    fun selectionIsInverseVideoOnlyWhenAsked() {
        val on = readerCss(settings.copy(solidSelection = true))
        assertTrue(on, on.contains("::selection { background: #E0E0E0 !important; color: #121212 !important; }"))
        assertFalse(readerCss(settings).contains("::selection"))
    }
}
