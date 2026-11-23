package com.iquintero.kavern.ui.theme

import androidx.compose.animation.core.SnapSpec
import org.junit.Assert.assertTrue
import org.junit.Test

class EinkMotionSchemeTest {
    @Test
    fun everyKindOfMaterialMotionIsAnInstantChange() {
        val specs =
            listOf(
                EinkMotionScheme.defaultSpatialSpec<Float>(),
                EinkMotionScheme.fastSpatialSpec(),
                EinkMotionScheme.slowSpatialSpec(),
                EinkMotionScheme.defaultEffectsSpec(),
                EinkMotionScheme.fastEffectsSpec(),
                EinkMotionScheme.slowEffectsSpec(),
            )
        specs.forEach { assertTrue("$it", it is SnapSpec) }
    }
}
