package com.two17industries.rideman.ui.theme

import androidx.compose.ui.graphics.Color
import com.two17industries.rideman.data.ThemeChoice

// Canvas / surfaces
val Background = Color(0xFF0A0A0A)
val Surface1 = Color(0xFF151515)
val Surface2 = Color(0xFF1F1F1F)
val TextPrimary = Color(0xFFE0E0E0)
val Muted = Color(0xFF888888)
val Dim = Color(0xFF4A4A4A)

// Fixed neon. Cyan drives every menu screen; the ride/end screens use LocalAccent instead.
val Cyan = Color(0xFF00FFFF)
val Magenta = Color(0xFFFF00FF)
val HotPink = Color(0xFFFF007F)  // wordmark cursor
val Warn = Color(0xFFFFCF3A)     // "SHORT" / Strava pending
val Delete = Color(0xFFFF5252)

// Hairline borders (1px).
val BorderCyan = Color(0x3300FFFF)     // rgba(0,255,255,0.20)
val BorderCyanDim = Color(0x1A00FFFF)  // rgba(0,255,255,0.10)
val GridLine = Color(0x1F00FFFF)       // rgba(0,255,255,0.12)

// User-selectable accents — unchanged; these already match 217.
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
