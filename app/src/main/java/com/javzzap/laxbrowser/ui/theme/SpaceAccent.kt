package com.javzzap.laxbrowser.ui.theme

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Theme colors derived from a Space's color (ARGB ints). */
data class SpaceAccentColors(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
)

/**
 * Turns any Space color into accent tones that stay readable: the hue is kept, the lightness is
 * moved until text reaches WCAG AA contrast. Pure math, so it is unit-tested.
 */
object SpaceAccent {
    private const val MIN_CONTRAST = 4.5
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val DARK_SURFACE = 0xFF13111A.toInt()

    fun colors(spaceColor: Int, dark: Boolean): SpaceAccentColors {
        val (hue, saturation, _) = toHsl(spaceColor)
        // Greys stay grey; everything else gets enough saturation to read as a color.
        val s = if (saturation < 0.08f) saturation else saturation.coerceIn(0.35f, 0.85f)
        return if (dark) {
            var l = 0.62f
            while (l < 0.92f && contrast(fromHsl(hue, s, l), DARK_SURFACE) < MIN_CONTRAST + 1.5) l += 0.02f
            SpaceAccentColors(
                primary = fromHsl(hue, s, l),
                onPrimary = fromHsl(hue, s * 0.6f, 0.14f),
                primaryContainer = fromHsl(hue, s * 0.55f, 0.30f),
                onPrimaryContainer = fromHsl(hue, s, 0.90f),
                secondaryContainer = fromHsl(hue, s * 0.4f, 0.26f),
                onSecondaryContainer = fromHsl(hue, s * 0.5f, 0.90f),
            )
        } else {
            var l = 0.50f
            while (l > 0.12f && contrast(fromHsl(hue, s, l), WHITE) < MIN_CONTRAST) l -= 0.02f
            SpaceAccentColors(
                primary = fromHsl(hue, s, l),
                onPrimary = WHITE,
                primaryContainer = fromHsl(hue, s, 0.90f),
                onPrimaryContainer = fromHsl(hue, s, 0.14f),
                secondaryContainer = fromHsl(hue, s * 0.5f, 0.89f),
                onSecondaryContainer = fromHsl(hue, s * 0.5f, 0.14f),
            )
        }
    }

    /** WCAG contrast ratio between two opaque colors. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((color shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun toHsl(color: Int): Triple<Float, Float, Float> {
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val l = (maxC + minC) / 2f
        val d = maxC - minC
        if (d == 0f) return Triple(0f, 0f, l)
        val s = d / (1f - abs(2f * l - 1f))
        val h = when (maxC) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return Triple((h + 360f) % 360f, s.coerceIn(0f, 1f), l)
    }

    private fun fromHsl(h: Float, s: Float, l: Float): Int {
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r, g, b) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun byte(v: Float) = ((v + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (byte(r) shl 16) or (byte(g) shl 8) or byte(b)
    }
}
