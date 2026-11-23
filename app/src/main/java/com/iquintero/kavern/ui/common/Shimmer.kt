package com.iquintero.kavern.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import com.iquintero.kavern.ui.theme.LocalEinkMode

@Composable
fun ShimmerBox(
    modifier: Modifier,
    progress: Float,
    height: Dp? = null,
) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
    val brush =
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(x = -600f + (1200f * progress), y = 0f),
            end = Offset(x = 0f + (1200f * progress), y = 300f),
        )
    val baseModifier =
        if (height != null) {
            modifier.height(height)
        } else {
            modifier
        }
    Box(
        modifier = baseModifier.background(brush),
    )
}

@Composable
fun rememberShimmerProgress(enabled: Boolean): Float {
    // A moving highlight keeps redrawing an e-ink panel; there the placeholders stay still.
    if (!enabled || LocalEinkMode.current) return 0f
    val transition = rememberInfiniteTransition(label = "shimmer")
    return transition
        .animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1700),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "shimmer_progress",
        ).value
}

@Composable
fun ShimmerBoxAnimated(
    modifier: Modifier,
    height: Dp? = null,
) {
    val progress = rememberShimmerProgress(enabled = true)
    ShimmerBox(
        modifier = modifier,
        progress = progress,
        height = height,
    )
}
