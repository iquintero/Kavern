package com.iquintero.kavern.ui.reader.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubPagedJsTest {
    @Test
    fun `runtime lets the app set the real view height and prefers it to innerHeight`() {
        assertTrue(PAGED_RUNTIME_JS.contains("ik.setViewHeight = function"))
        assertTrue(PAGED_RUNTIME_JS.contains("forcedHeight || window.innerHeight"))
    }

    @Test
    fun `start locks the document so only script can move the page`() {
        val start = PAGED_RUNTIME_JS.substringAfter("ik.start = function")
        assertTrue(start.contains("'overflow', 'hidden', 'important'"))
        assertTrue(start.contains("overflow: 'visible', height: 'auto'"))
        assertTrue(start.contains("'touch-action', 'none', 'important'"))
    }

    @Test
    fun `runtime reports the bridge name used by the app`() {
        assertTrue(PAGED_RUNTIME_JS.contains("window.$PAGED_BRIDGE_NAME.onPage"))
        assertFalse(PAGED_RUNTIME_JS.contains("\$PAGED_BRIDGE_NAME"))
    }

    @Test
    fun `start script installs the runtime then places the reader`() {
        val js = pagedStartJs(scrollToken = null, atEnd = true, enterDirection = 1, animate = false)
        assertTrue(js.contains("ik.animate = false;"))
        assertTrue(js.contains("var ik = window.__ik;"))
        assertTrue(js.contains("if (true) ik.last(); else ik.go(0);"))
        assertTrue(js.contains("if (1 !== 0) ik.enter(1);"))
    }

    @Test
    fun `paged css wraps code and normal mode adds nothing`() {
        val css = pagedContentCss(paged = true)
        assertTrue(css.contains("white-space: pre-wrap !important"))
        assertTrue(css.contains("overflow-wrap: anywhere !important"))
        assertEquals("", pagedContentCss(paged = false))
    }
}
