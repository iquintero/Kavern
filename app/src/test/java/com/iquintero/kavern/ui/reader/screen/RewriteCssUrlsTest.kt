package com.iquintero.kavern.ui.reader.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class RewriteCssUrlsTest {
    private val toLocal: (String) -> String? = { if (it.startsWith("//ok")) "https://local/$it" else null }

    @Test
    fun `rewrites quoted and unquoted urls and keeps the rest of the css`() {
        val css = "@font-face{font-family:A;src:url(\"//ok/a.otf\")}b{background:url( '//ok/b.png' ) no-repeat}c{d:url(//ok/c)}"
        assertEquals(
            "@font-face{font-family:A;src:url(\"https://local///ok/a.otf\")}b{background:url('https://local///ok/b.png') no-repeat}" +
                "c{d:url(https://local///ok/c)}",
            rewriteCssUrls(css, toLocal),
        )
    }

    @Test
    fun `leaves urls the mapper rejects`() {
        val css = "a{background:url(https://elsewhere.example/x.png)}"
        assertEquals(css, rewriteCssUrls(css, toLocal))
    }

    @Test
    fun `css without urls is unchanged`() {
        assertEquals("p{color:red}", rewriteCssUrls("p{color:red}", toLocal))
    }
}
