package com.iquintero.kavern

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Guards manifest settings that are easy to lose and only show up on a real device. */
class ManifestTest {
    private val manifest = File("src/main/AndroidManifest.xml")
    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun mainActivity(): Element {
        val document =
            DocumentBuilderFactory
                .newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(manifest)
        val activities = document.getElementsByTagName("activity")
        return (0 until activities.length)
            .map { activities.item(it) as Element }
            .single { it.getAttributeNS(androidNs, "name") == ".MainActivity" }
    }

    // Without it, opening the app from the launcher after it was started some other way (an installer, adb) opens a
    // second copy on top, which always starts on Home instead of returning to the screen that was open.
    @Test
    fun launchingTheAppAgainReusesTheOpenWindow() {
        assertEquals("singleTask", mainActivity().getAttributeNS(androidNs, "launchMode"))
    }
}
