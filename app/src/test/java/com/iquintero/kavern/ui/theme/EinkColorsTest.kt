package com.iquintero.kavern.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EinkColorsTest {
    private fun contrast(
        a: Color,
        b: Color,
    ): Float = (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)

    private val schemes: Map<String, ColorScheme> = mapOf("light" to EinkLightColors, "dark" to EinkDarkColors)

    private fun ColorScheme.surfaces() = listOf(background, surface, surfaceContainerLow, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest)

    @Test
    fun everySurfaceIsPlainBlackOrWhite() {
        schemes.forEach { (name, scheme) ->
            scheme.surfaces().forEach { surface ->
                assertTrue("$name $surface", surface == Color.Black || surface == Color.White)
            }
        }
    }

    @Test
    fun allSurfacesMatchThePageSoOutlinesCarryTheStructure() {
        schemes.forEach { (name, scheme) -> assertEquals(name, 1, scheme.surfaces().toSet().size) }
    }

    @Test
    fun selectedAndTonalStatesInvertForMaximumContrast() {
        schemes.forEach { (name, scheme) ->
            assertEquals("$name secondary", 21f, contrast(scheme.secondaryContainer, scheme.onSecondaryContainer), 0.01f)
            assertEquals("$name primary", 21f, contrast(scheme.primaryContainer, scheme.onPrimaryContainer), 0.01f)
            // Inverted means different from the page, so a selected item stands out from an unselected one.
            assertTrue(name, scheme.secondaryContainer != scheme.surface)
        }
    }

    @Test
    fun textAndOutlinesAreFullContrastAgainstThePage() {
        schemes.forEach { (name, scheme) ->
            assertEquals("$name text", 21f, contrast(scheme.onSurface, scheme.surface), 0.01f)
            assertEquals("$name outline", 21f, contrast(scheme.outline, scheme.surface), 0.01f)
        }
    }
}
