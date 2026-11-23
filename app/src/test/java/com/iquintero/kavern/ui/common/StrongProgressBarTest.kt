package com.iquintero.kavern.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class StrongProgressBarTest {
    @Test
    fun valuesInRangeAreKept() {
        assertEquals(0f, progressFraction(0f))
        assertEquals(0.23f, progressFraction(0.23f))
        assertEquals(1f, progressFraction(1f))
    }

    @Test
    fun valuesOutsideTheRangeAreClamped() {
        assertEquals(0f, progressFraction(-0.5f))
        assertEquals(1f, progressFraction(1.7f))
    }

    @Test
    fun anUnknownValueDrawsAnEmptyBarInsteadOfCrashing() {
        assertEquals(0f, progressFraction(Float.NaN))
    }
}
