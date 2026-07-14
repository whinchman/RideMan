package com.two17industries.rideman.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.two17industries.rideman.R

val Orbitron = FontFamily(
    Font(R.font.orbitron_regular, FontWeight.Normal),
    Font(R.font.orbitron_medium, FontWeight.Medium),
    Font(R.font.orbitron_semibold, FontWeight.SemiBold),
    Font(R.font.orbitron_bold, FontWeight.Bold),
    Font(R.font.orbitron_extrabold, FontWeight.ExtraBold),
    Font(R.font.orbitron_black, FontWeight.Black),
)

val IbmPlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_bold, FontWeight.Bold),
)

/**
 * The full-screen single-metric value (Speed, Odometer, Compass, Altitude, Cadence).
 *
 * Deliberately NOT displayLarge. displayLarge's 54sp is a *dash grid cell* size, where four
 * values share a screen; these five screens give one value the whole display and need to stay
 * legible on a handlebar at arm's length.
 */
val bigMetric = TextStyle(
    fontFamily = Orbitron,
    fontWeight = FontWeight.Bold,
    fontSize = 120.sp,
    letterSpacing = 0.sp,
)

val RidemanTypography = Typography(
    // Dash grid value.
    displayLarge = TextStyle(
        fontFamily = Orbitron,
        fontWeight = FontWeight.Bold,
        fontSize = 54.sp,
        lineHeight = 46.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Orbitron,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 2.sp,
    ),
    // Small-caps mono labels.
    labelLarge = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.6.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
)
