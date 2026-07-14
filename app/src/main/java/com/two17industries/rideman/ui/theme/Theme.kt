package com.two17industries.rideman.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import com.two17industries.rideman.data.ThemeChoice

val LocalAccent = staticCompositionLocalOf { Amber }

/**
 * All-square shape scheme. The design mandates zero rounded corners anywhere; without this,
 * stock Material shapes stay in force and any future Material component (Card, Switch, Chip,
 * Button) could silently reintroduce rounding.
 *
 * `Shapes` requires `CornerBasedShape`, so a literal `RectangleShape` doesn't type-check here;
 * `RoundedCornerShape(0.dp)` is the CornerBasedShape equivalent — zero corner radius, visually
 * identical square corners.
 */
private val SquareCorners = RoundedCornerShape(0.dp)
private val SquareShapes = Shapes(
    extraSmall = SquareCorners,
    small = SquareCorners,
    medium = SquareCorners,
    large = SquareCorners,
    extraLarge = SquareCorners,
)

@Composable
fun RidemanTheme(theme: ThemeChoice, content: @Composable () -> Unit) {
    val accent = accentFor(theme)
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Background,
        background = Background,
        onBackground = TextPrimary,
        surface = Background,
        onSurface = TextPrimary,
    )
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(
            colorScheme = scheme,
            typography = RidemanTypography,
            shapes = SquareShapes,
            content = content,
        )
    }
}
