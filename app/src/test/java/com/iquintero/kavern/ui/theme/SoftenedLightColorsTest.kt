package com.iquintero.kavern.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftenedLightColorsTest {
    private val original = KavernLightColors
    private val soft = KavernLightColors.softenedForLight()

    /** WCAG contrast ratio between two colors. */
    private fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val hi = maxOf(a.luminance(), b.luminance())
        val lo = minOf(a.luminance(), b.luminance())
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun thePageIsDarkerThanBefore() {
        assertTrue(soft.background.luminance() < original.background.luminance())
        assertEquals(soft.background, soft.surface)
    }

    @Test
    fun cardsAreLighterThanThePageTheySitOn() {
        assertTrue(soft.surfaceContainerLow.luminance() > soft.background.luminance())
    }

    @Test
    fun theContainerLevelsKeepTheirOrderFromLightToDark() {
        val levels =
            listOf(
                soft.surfaceContainerLowest,
                soft.surfaceContainerLow,
                soft.surface,
                soft.surfaceContainer,
                soft.surfaceContainerHigh,
                soft.surfaceContainerHighest,
                soft.surfaceDim,
            ).map { it.luminance() }
        assertEquals(levels.sortedDescending(), levels)
    }

    @Test
    fun cardsSeparateFromThePageMoreThanBefore() {
        val before = contrast(original.surfaceContainerLow, original.background)
        val after = contrast(soft.surfaceContainerLow, soft.background)
        assertTrue("before=$before after=$after", after > before)
    }

    @Test
    fun textStaysReadableOnEverySurface() {
        listOf(soft.background, soft.surfaceContainerLow, soft.surfaceContainer, soft.surfaceContainerHigh, soft.surfaceContainerHighest).forEach {
            assertTrue("body text on $it", contrast(soft.onSurface, it) >= 7f)
            assertTrue("secondary text on $it", contrast(soft.onSurfaceVariant, it) >= 4.5f)
        }
        assertTrue("hints on page", contrast(soft.outline, soft.background) >= 3f)
    }

    @Test
    fun outlinesAreStrongerThanBefore() {
        assertTrue(contrast(soft.outlineVariant, soft.background) > contrast(original.outlineVariant, original.background))
    }
}
