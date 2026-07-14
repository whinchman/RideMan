package com.two17industries.rideman.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.two17industries.rideman.data.ThemeChoice

val LocalAccent = staticCompositionLocalOf { Amber }

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
        MaterialTheme(colorScheme = scheme, typography = RidemanTypography, content = content)
    }
}
