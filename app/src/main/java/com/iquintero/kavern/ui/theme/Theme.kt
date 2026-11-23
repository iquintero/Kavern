package com.iquintero.kavern.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Slightly squarer corners than the default, so e-ink panels draw crisp outlines. */
private val EinkShapes =
    Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(6.dp),
        large = RoundedCornerShape(8.dp),
        extraLarge = RoundedCornerShape(12.dp),
    )

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun KavernTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    einkMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme: ColorScheme =
        when {
            einkMode -> if (darkTheme) EinkDarkColors else EinkLightColors

            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context).softenedForLight()
            }

            darkTheme -> KavernDarkColors

            else -> KavernLightColors.softenedForLight()
        }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        // E-ink panels refresh slowly, so motion would only smear.
        motionScheme = if (einkMode) EinkMotionScheme else MotionScheme.expressive(),
        shapes = if (einkMode) EinkShapes else Shapes(),
        typography = Typography,
    ) {
        CompositionLocalProvider(
            LocalEinkMode provides einkMode,
            // A press ripple is an animation too; the button's own pressed state is enough feedback.
            LocalRippleConfiguration provides (if (einkMode) null else LocalRippleConfiguration.current),
            content = content,
        )
    }
}
