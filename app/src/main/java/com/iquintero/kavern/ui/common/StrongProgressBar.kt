package com.iquintero.kavern.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DefaultBarHeight = 10.dp
private val OutlineWidth = 1.5.dp

/** [progress] limited to 0..1, so a bad value from the server can never draw past the ends of the bar. */
fun progressFraction(progress: Float): Float = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)

/**
 * A progress bar that stays readable on any screen, including grayscale e-ink panels.
 *
 * Material's own bar colors the fill with the theme's accent and the empty track with a pale tint of it. On a
 * grayscale display those two grays are almost the same, so the bar reads as one pale strip whatever the progress.
 * This one draws the fill in the text color and the track as an empty box with a text-colored outline, so the filled
 * part is always dark on light (or light on dark) and the empty part is clearly empty.
 */
@Composable
fun StrongProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = DefaultBarHeight,
) {
    val fraction = progressFraction(progress)
    val shape = RoundedCornerShape(percent = 50)
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) }
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(OutlineWidth, ink, shape),
    ) {
        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction).background(ink))
    }
}
