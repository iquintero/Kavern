package com.iquintero.kavern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Guards the string resources against the mistakes lint only warns about: leftovers, duplicates and broken formats. */
class StringResourcesTest {
    private val resDir = File("src/main/res")
    private val format = Regex("%(\\d+\\$)?[sdfx]")

    private fun strings(file: File): List<Pair<String, String>> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it).let { n -> n.attributes.getNamedItem("name").nodeValue to n.textContent } }
    }

    private val default = strings(File(resDir, "values/strings.xml"))
    private val translations =
        resDir
            .listFiles { f -> f.isDirectory && f.name.startsWith("values-") && File(f, "strings.xml").exists() }!!
            .associate { it.name to strings(File(it, "strings.xml")) }

    @Test
    fun `no string is defined twice in a file`() {
        (translations + ("values" to default)).forEach { (name, list) ->
            val duplicates = list.groupBy { it.first }.filterValues { it.size > 1 }.keys
            assertTrue("$name defines twice: $duplicates", duplicates.isEmpty())
        }
    }

    @Test
    fun `translations only translate strings that exist`() {
        val known = default.map { it.first }.toSet()
        translations.forEach { (name, list) ->
            val orphans = list.map { it.first }.filterNot { it in known }
            assertTrue("$name has strings with no default: $orphans", orphans.isEmpty())
        }
    }

    @Test
    fun `translations keep the same format placeholders as the default`() {
        val placeholders =
            default.associate { (key, text) ->
                key to
                    format
                        .findAll(text)
                        .map { it.value }
                        .sorted()
                        .toList()
            }
        translations.forEach { (name, list) ->
            list.forEach { (key, text) ->
                val translated =
                    format
                        .findAll(text)
                        .map { it.value }
                        .sorted()
                        .toList()
                assertEquals("$name/$key placeholders", placeholders[key], translated)
            }
        }
    }

    @Test
    fun `the default strings are not empty`() {
        val empty = default.filter { it.second.isBlank() }.map { it.first }
        assertTrue("empty strings: $empty", empty.isEmpty())
    }
}
