package com.iquintero.kavern.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

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

private val EinkOutlineWidth = 1.dp

/** A thin text-colored outline in E-reader mode, otherwise none. Gray fills dither on e-ink, so outlines carry the structure. */
@Composable
fun einkBorder(): BorderStroke? = if (LocalEinkMode.current) BorderStroke(EinkOutlineWidth, MaterialTheme.colorScheme.onSurface) else null

/** [einkBorder] as a modifier, for things that are not a Surface (cover images, avatars, text fields). */
@Composable
fun Modifier.einkOutline(shape: Shape): Modifier = einkBorder()?.let { border(it, shape) } ?: this
