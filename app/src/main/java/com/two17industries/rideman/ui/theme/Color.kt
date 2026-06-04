package com.two17industries.rideman.ui.theme

import androidx.compose.ui.graphics.Color
import com.two17industries.rideman.data.ThemeChoice

val Background = Color(0xFF050505)

val AcidGreen = Color(0xFF39FF14)
val ElectricCyan = Color(0xFF00F0FF)
val HotMagenta = Color(0xFFFF2D95)
val Amber = Color(0xFFFFC400)

fun accentFor(choice: ThemeChoice): Color = when (choice) {
    ThemeChoice.AMBER -> Amber
    ThemeChoice.ACID_GREEN -> AcidGreen
    ThemeChoice.ELECTRIC_CYAN -> ElectricCyan
    ThemeChoice.HOT_MAGENTA -> HotMagenta
}
