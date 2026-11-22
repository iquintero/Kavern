package com.iquintero.kavern.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormatBytesTest {
    @Test
    fun `zero and negative sizes read 0 B`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("0 B", formatBytes(-5))
    }

    @Test
    fun `small sizes stay in bytes`() {
        assertEquals("1.0 B", formatBytes(1, Locale.US))
        assertEquals("1023.0 B", formatBytes(1023, Locale.US))
    }

    @Test
    fun `sizes move up a unit at each 1024`() {
        assertEquals("1.0 KB", formatBytes(1024, Locale.US))
        assertEquals("1.5 KB", formatBytes(1536, Locale.US))
        assertEquals("1.0 MB", formatBytes(1024L * 1024, Locale.US))
        assertEquals("2.5 GB", formatBytes((2.5 * 1024 * 1024 * 1024).toLong(), Locale.US))
    }

    @Test
    fun `gigabytes are the largest unit`() {
        assertEquals("2048.0 GB", formatBytes(2L * 1024 * 1024 * 1024 * 1024, Locale.US))
    }

    @Test
    fun `the decimal separator follows the locale`() {
        assertEquals("1,5 KB", formatBytes(1536, Locale.GERMANY))
    }
}
