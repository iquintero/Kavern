package com.iquintero.kavern.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Kavern's own palette, seeded from the launcher green. Used when dynamic color is off or unavailable. */
internal val KavernLightColors: ColorScheme =
    lightColorScheme(
        primary = Color(0xFF1B6C46),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFA4F4C0),
        onPrimaryContainer = Color(0xFF002111),
        inversePrimary = Color(0xFF88D7A5),
        secondary = Color(0xFF4E6355),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD0E8D6),
        onSecondaryContainer = Color(0xFF0B1F14),
        tertiary = Color(0xFF3B6470),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFBFE9F7),
        onTertiaryContainer = Color(0xFF001F27),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF6FBF4),
        onBackground = Color(0xFF171D19),
        surface = Color(0xFFF6FBF4),
        onSurface = Color(0xFF171D19),
        surfaceVariant = Color(0xFFDCE5DC),
        onSurfaceVariant = Color(0xFF404942),
        surfaceTint = Color(0xFF1B6C46),
        inverseSurface = Color(0xFF2C322D),
        inverseOnSurface = Color(0xFFEDF2EB),
        outline = Color(0xFF707971),
        outlineVariant = Color(0xFFC0C9C0),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFF6FBF4),
        surfaceDim = Color(0xFFD6DBD4),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF0F5EE),
        surfaceContainer = Color(0xFFEAEFE8),
        surfaceContainerHigh = Color(0xFFE5EAE3),
        surfaceContainerHighest = Color(0xFFDFE4DD),
    )

internal val KavernDarkColors: ColorScheme =
    darkColorScheme(
        primary = Color(0xFF88D7A5),
        onPrimary = Color(0xFF003920),
        primaryContainer = Color(0xFF005230),
        onPrimaryContainer = Color(0xFFA4F4C0),
        inversePrimary = Color(0xFF1B6C46),
        secondary = Color(0xFFB4CCBA),
        onSecondary = Color(0xFF203529),
        secondaryContainer = Color(0xFF374B3E),
        onSecondaryContainer = Color(0xFFD0E8D6),
        tertiary = Color(0xFFA3CDDA),
        onTertiary = Color(0xFF023640),
        tertiaryContainer = Color(0xFF214C57),
        onTertiaryContainer = Color(0xFFBFE9F7),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0F1511),
        onBackground = Color(0xFFDFE4DD),
        surface = Color(0xFF0F1511),
        onSurface = Color(0xFFDFE4DD),
        surfaceVariant = Color(0xFF404942),
        onSurfaceVariant = Color(0xFFC0C9C0),
        surfaceTint = Color(0xFF88D7A5),
        inverseSurface = Color(0xFFDFE4DD),
        inverseOnSurface = Color(0xFF2C322D),
        outline = Color(0xFF8A938A),
        outlineVariant = Color(0xFF404942),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF343B36),
        surfaceDim = Color(0xFF0F1511),
        surfaceContainerLowest = Color(0xFF0A0F0C),
        surfaceContainerLow = Color(0xFF171D19),
        surfaceContainer = Color(0xFF1B211D),
        surfaceContainerHigh = Color(0xFF262B27),
        surfaceContainerHighest = Color(0xFF303632),
    )

/**
 * Pure black and white for e-ink panels: no tonal colors, containers separated by outlines
 * instead of fills, so nothing dithers.
 */
internal val EinkLightColors: ColorScheme =
    lightColorScheme(
        primary = Color.Black,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE6E6E6),
        onPrimaryContainer = Color.Black,
        inversePrimary = Color.White,
        secondary = Color.Black,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE6E6E6),
        onSecondaryContainer = Color.Black,
        tertiary = Color.Black,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE6E6E6),
        onTertiaryContainer = Color.Black,
        error = Color.Black,
        onError = Color.White,
        errorContainer = Color(0xFFE6E6E6),
        onErrorContainer = Color.Black,
        background = Color.White,
        onBackground = Color.Black,
        surface = Color.White,
        onSurface = Color.Black,
        surfaceVariant = Color.White,
        onSurfaceVariant = Color.Black,
        surfaceTint = Color.Transparent,
        inverseSurface = Color.Black,
        inverseOnSurface = Color.White,
        outline = Color.Black,
        outlineVariant = Color(0xFF808080),
        scrim = Color.Black,
        surfaceBright = Color.White,
        surfaceDim = Color.White,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color.White,
        surfaceContainer = Color.White,
        surfaceContainerHigh = Color(0xFFF2F2F2),
        surfaceContainerHighest = Color(0xFFE6E6E6),
    )

internal val EinkDarkColors: ColorScheme =
    darkColorScheme(
        primary = Color.White,
        onPrimary = Color.Black,
        primaryContainer = Color(0xFF262626),
        onPrimaryContainer = Color.White,
        inversePrimary = Color.Black,
        secondary = Color.White,
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF262626),
        onSecondaryContainer = Color.White,
        tertiary = Color.White,
        onTertiary = Color.Black,
        tertiaryContainer = Color(0xFF262626),
        onTertiaryContainer = Color.White,
        error = Color.White,
        onError = Color.Black,
        errorContainer = Color(0xFF262626),
        onErrorContainer = Color.White,
        background = Color.Black,
        onBackground = Color.White,
        surface = Color.Black,
        onSurface = Color.White,
        surfaceVariant = Color.Black,
        onSurfaceVariant = Color.White,
        surfaceTint = Color.Transparent,
        inverseSurface = Color.White,
        inverseOnSurface = Color.Black,
        outline = Color.White,
        outlineVariant = Color(0xFF808080),
        scrim = Color.Black,
        surfaceBright = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainer = Color.Black,
        surfaceContainerHigh = Color(0xFF131313),
        surfaceContainerHighest = Color(0xFF262626),
    )

/** How far the dividers and outlines move toward the stronger outline color. */
private const val OUTLINE_STRENGTHEN = 0.35f

/** How far the darkest surface moves toward black, so it still stands out from the page. */
private const val DIM_DEEPEN = 0.08f

/**
 * Makes a light scheme easier on the eyes. Material's light surfaces are almost white, and the containers
 * above them are only a few percent darker, so a screen of cards looks like one white sheet.
 *
 * The page takes the middle container tone and the cards keep the original bright surface, so cards are
 * lighter than the page behind them. Every container steps one level down to make room, and the outlines
 * get a little stronger. Text colors are unchanged, so text contrast stays as high as before.
 */
internal fun ColorScheme.softenedForLight(): ColorScheme =
    copy(
        background = surfaceContainer,
        surface = surfaceContainer,
        surfaceBright = surface,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceContainerLow = surface,
        surfaceContainer = surfaceContainerHigh,
        surfaceContainerHigh = surfaceContainerHighest,
        surfaceContainerHighest = surfaceDim,
        surfaceDim = lerp(surfaceDim, Color.Black, DIM_DEEPEN),
        outlineVariant = lerp(outlineVariant, outline, OUTLINE_STRENGTHEN),
    )
