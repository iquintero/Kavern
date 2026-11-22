package com.iquintero.kavern.core.util

import java.util.Locale

private val BYTE_UNITS = arrayOf("B", "KB", "MB", "GB")
private const val BYTES_PER_UNIT = 1024

/** A size in bytes as text, for example `1.5 MB`. Sizes of zero or less read `0 B`. */
fun formatBytes(
    value: Long,
    locale: Locale = Locale.getDefault(),
): String {
    if (value <= 0) return "0 B"
    var size = value.toDouble()
    var unit = 0
    while (size >= BYTES_PER_UNIT && unit < BYTE_UNITS.lastIndex) {
        size /= BYTES_PER_UNIT
        unit++
    }
    return String.format(locale, "%.1f %s", size, BYTE_UNITS[unit])
}
