package com.javzzap.laxbrowser.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.javzzap.laxbrowser.R
import androidx.compose.ui.res.stringResource
import com.javzzap.laxbrowser.ui.theme.BrandColors
import com.javzzap.laxbrowser.ui.theme.SpaceGrotesk

/** The icon's gradient, deepened on light backgrounds so it stays readable. */
@Composable
fun brandGradientColors(): List<Color> =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) BrandColors.gradientOnDark else BrandColors.gradientOnLight

/** "Lax" set in Space Grotesk with the icon's gradient. */
@Composable
fun LaxWordmark(modifier: Modifier = Modifier, fontSize: TextUnit = 56.sp) {
    Text(
        stringResource(R.string.app_name),
        modifier = modifier.semantics { heading() },
        style = TextStyle(
            brush = Brush.linearGradient(brandGradientColors()),
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            letterSpacing = (-0.03).em,
        ),
    )
}
