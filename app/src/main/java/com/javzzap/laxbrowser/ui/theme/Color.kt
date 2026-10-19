package com.javzzap.laxbrowser.ui.theme

import androidx.compose.ui.graphics.Color

/** The icon's gradient (docs/icon/lax-icon.svg): lavender, sky blue, mint. */
object BrandColors {
    val Lavender = Color(0xFFC4A6FB)
    val Sky = Color(0xFFA3BDE8)
    val Mint = Color(0xFF66E8B8)
    val Violet = Color(0xFF8F7CF6)
    val Ink = Color(0xFF3B2F63)

    /** The icon gradient, for dark backgrounds. */
    val gradientOnDark = listOf(Lavender, Sky, Mint)

    /** Deeper versions of the gradient that stay readable on light backgrounds. */
    val gradientOnLight = listOf(Color(0xFF7B5CF0), Color(0xFF4F7FD9), Color(0xFF13A57A))
}

internal object LightBrand {
    val primary = Color(0xFF6A4FD8)
    val onPrimary = Color(0xFFFFFFFF)
    val primaryContainer = Color(0xFFE9DDFF)
    val onPrimaryContainer = Color(0xFF22005D)
    val secondary = Color(0xFF4F6A9A)
    val onSecondary = Color(0xFFFFFFFF)
    val secondaryContainer = Color(0xFFDDE5FA)
    val onSecondaryContainer = Color(0xFF0A1C3B)
    val tertiary = Color(0xFF00876A)
    val onTertiary = Color(0xFFFFFFFF)
    val tertiaryContainer = Color(0xFFB6F5DC)
    val onTertiaryContainer = Color(0xFF002117)
    val surface = Color(0xFFFCF9FF)
    val onSurface = Color(0xFF1C1A24)
    val surfaceVariant = Color(0xFFE6E0F0)
    val onSurfaceVariant = Color(0xFF4A4558)
    val surfaceContainerLowest = Color(0xFFFFFFFF)
    val surfaceContainerLow = Color(0xFFF7F3FD)
    val surfaceContainer = Color(0xFFF1EDF8)
    val surfaceContainerHigh = Color(0xFFEBE7F3)
    val surfaceContainerHighest = Color(0xFFE5E1EE)
    val outline = Color(0xFF7A7489)
    val outlineVariant = Color(0xFFCBC4D6)
}

internal object DarkBrand {
    val primary = Color(0xFFCDBDFF)
    val onPrimary = Color(0xFF36208F)
    val primaryContainer = Color(0xFF4E37B5)
    val onPrimaryContainer = Color(0xFFE9DDFF)
    val secondary = Color(0xFFB3C7EE)
    val onSecondary = Color(0xFF1D3157)
    val secondaryContainer = Color(0xFF34476F)
    val onSecondaryContainer = Color(0xFFDDE5FA)
    val tertiary = Color(0xFF7FDDBB)
    val onTertiary = Color(0xFF00382A)
    val tertiaryContainer = Color(0xFF00513E)
    val onTertiaryContainer = Color(0xFFB6F5DC)
    val surface = Color(0xFF13111A)
    val onSurface = Color(0xFFE6E1EE)
    val surfaceVariant = Color(0xFF2E2A39)
    val onSurfaceVariant = Color(0xFFCAC4D4)
    val surfaceContainerLowest = Color(0xFF0E0C14)
    val surfaceContainerLow = Color(0xFF1B1823)
    val surfaceContainer = Color(0xFF1F1C28)
    val surfaceContainerHigh = Color(0xFF2A2633)
    val surfaceContainerHighest = Color(0xFF35313E)
    val outline = Color(0xFF948EA0)
    val outlineVariant = Color(0xFF49454F)
}
