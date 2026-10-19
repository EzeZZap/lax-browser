package com.javzzap.laxbrowser.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = with(LightBrand) {
    lightColorScheme(
        primary = primary, onPrimary = onPrimary, primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = secondary, onSecondary = onSecondary, secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary, onTertiary = onTertiary, tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
        background = surface, onBackground = onSurface, surface = surface, onSurface = onSurface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant, surfaceTint = primary,
        surfaceContainerLowest = surfaceContainerLowest, surfaceContainerLow = surfaceContainerLow, surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh, surfaceContainerHighest = surfaceContainerHighest,
        outline = outline, outlineVariant = outlineVariant,
    )
}

private val DarkColorScheme = with(DarkBrand) {
    darkColorScheme(
        primary = primary, onPrimary = onPrimary, primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = secondary, onSecondary = onSecondary, secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary, onTertiary = onTertiary, tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
        background = surface, onBackground = onSurface, surface = surface, onSurface = onSurface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant, surfaceTint = primary,
        surfaceContainerLowest = surfaceContainerLowest, surfaceContainerLow = surfaceContainerLow, surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh, surfaceContainerHighest = surfaceContainerHighest,
        outline = outline, outlineVariant = outlineVariant,
    )
}

private val PrivateColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondaryContainer = Color(0xFF4A3D6E),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF17122A),
    surface = Color(0xFF17122A),
    surfaceVariant = Color(0xFF2E2547),
    surfaceContainerLow = Color(0xFF1D1733),
    surfaceContainer = Color(0xFF231C3C),
    surfaceContainerHigh = Color(0xFF2B2346),
    onSurface = Color(0xFFE7E0F5),
    onSurfaceVariant = Color(0xFFCBC2DB),
)

/** Gives private browsing a distinct dark purple look so it is never mistaken for a normal Space. */
@Composable
fun PrivateBrowsingTheme(enabled: Boolean, content: @Composable () -> Unit) {
    if (enabled) {
        MaterialTheme(colorScheme = PrivateColorScheme, typography = Typography, content = content)
    } else {
        content()
    }
}

/**
 * Lax's palette, from the icon's lavender, sky, and mint. [wallpaperColors] switches to Android
 * 12+ dynamic colors instead.
 */
@Composable
fun LaxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    wallpaperColors: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        wallpaperColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

/**
 * Re-tints the accent colors with the active Space's color, crossfading when the Space changes.
 * A null [spaceColor] keeps the surrounding theme.
 */
@Composable
fun SpaceAccentTheme(spaceColor: Int?, content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    val dark = base.surface.luminance() < 0.5f
    val accent = remember(spaceColor, dark) { spaceColor?.let { SpaceAccent.colors(it, dark) } }
    val spec = tween<Color>(ACCENT_FADE_MS)
    val primary by animateColorAsState(accent?.primary?.let(::Color) ?: base.primary, spec, label = "primary")
    val onPrimary by animateColorAsState(accent?.onPrimary?.let(::Color) ?: base.onPrimary, spec, label = "onPrimary")
    val primaryContainer by animateColorAsState(accent?.primaryContainer?.let(::Color) ?: base.primaryContainer, spec, label = "primaryContainer")
    val onPrimaryContainer by animateColorAsState(accent?.onPrimaryContainer?.let(::Color) ?: base.onPrimaryContainer, spec, label = "onPrimaryContainer")
    val secondaryContainer by animateColorAsState(accent?.secondaryContainer?.let(::Color) ?: base.secondaryContainer, spec, label = "secondaryContainer")
    val onSecondaryContainer by animateColorAsState(
        accent?.onSecondaryContainer?.let(::Color) ?: base.onSecondaryContainer, spec, label = "onSecondaryContainer",
    )
    MaterialTheme(
        colorScheme = base.copy(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            surfaceTint = primary,
        ),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

private const val ACCENT_FADE_MS = 450
