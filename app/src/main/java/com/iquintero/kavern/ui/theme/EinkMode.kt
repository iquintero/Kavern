package com.iquintero.kavern.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True while E-reader mode is on. Screens read it to drop what e-ink panels do badly: anything that moves, fades or
 * relies on faint gray fills. Provided once, by [KavernTheme].
 */
val LocalEinkMode = staticCompositionLocalOf { false }

/**
 * Material's own components (switches, chips, sheets, menus) animate through the theme's motion scheme. This one
 * answers every request with an instant change, so none of them leaves a half-drawn frame on an e-ink panel.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal object EinkMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}
