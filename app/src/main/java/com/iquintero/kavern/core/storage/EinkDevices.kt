package com.iquintero.kavern.core.storage

/** Makers whose Android devices have e-ink screens (their phones and tablets are not sold under these names). */
private val EINK_MAKERS = listOf("onyx", "boox", "bigme", "meebook", "likebook", "ireader", "inkpalm", "dasung")

/**
 * Whether a device with this [manufacturer] and [brand] is a known e-ink reader. It only chooses the starting value of
 * E-reader mode; anyone can turn the mode on or off in Settings.
 */
fun isEinkDevice(
    manufacturer: String?,
    brand: String?,
): Boolean {
    val names = listOfNotNull(manufacturer, brand).map { it.trim().lowercase() }
    return names.any { name -> EINK_MAKERS.any { maker -> name == maker || name.startsWith("$maker ") } }
}
