package com.two17industries.rideman.ui.ride

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.core.Units
import com.two17industries.rideman.ui.RideUiState
import com.two17industries.rideman.ui.theme.Background
import com.two17industries.rideman.ui.theme.Dim
import com.two17industries.rideman.ui.theme.LocalAccent
import com.two17industries.rideman.ui.theme.Magenta
import com.two17industries.rideman.ui.theme.Muted
import com.two17industries.rideman.ui.theme.Orbitron
import com.two17industries.rideman.ui.theme.IbmPlexMono
import kotlin.math.roundToInt

/**
 * The "Dash" screen: a 2x2 readout grid matching the bike-mounted display.
 *
 * Row-major: SPEED, DISTANCE / DURATION, HEADING.
 *
 * The hairline grid lines are drawn as a 1dp gap over an accent-tinted background — the cells
 * paint themselves Background and the gutters show through. That gives the interior cross with
 * no outer border, which is what the design calls for and what Modifier.border cannot do.
 */
@Composable
fun DashGridScreen(state: RideUiState, units: UnitSystem, landscape: Boolean) {
    val accent = LocalAccent.current
    val gridLine = accent.copy(alpha = 0.12f)

    Column(
        modifier = Modifier.fillMaxSize().background(gridLine),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Cell(
                label = "SPEED",
                unit = Units.speedLabel(units),
                landscape = landscape,
                modifier = Modifier.weight(1f),
            ) {
                Value(
                    text = Units.speed(state.speedMps, units).roundToInt().toString(),
                    sizeSp = if (landscape) 56 else 54,
                    color = accent,
                )
            }
            Cell(
                label = "DISTANCE",
                unit = Units.distanceLabel(units),
                landscape = landscape,
                modifier = Modifier.weight(1f),
            ) {
                Value(
                    text = String.format(java.util.Locale.US, "%.2f", Units.distance(state.distanceM, units)),
                    sizeSp = if (landscape) 48 else 40,
                    color = accent,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Cell(
                label = "DURATION",
                unit = null,  // duration carries no unit suffix
                landscape = landscape,
                modifier = Modifier.weight(1f),
            ) {
                Value(
                    text = Units.duration(state.elapsedMs),
                    sizeSp = if (landscape) 44 else 38,
                    color = accent,
                )
            }
            Cell(
                label = "HEADING",
                unit = "DEG",
                landscape = landscape,
                modifier = Modifier.weight(1f),
            ) {
                Heading(headingDeg = state.headingDeg, landscape = landscape, accent = accent)
            }
        }
    }
}

/** One grid cell: a mono header line above a centered value. */
@Composable
private fun Cell(
    label: String,
    unit: String?,
    landscape: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(
                horizontal = if (landscape) 18.dp else 16.dp,
                vertical = if (landscape) 14.dp else 16.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Header(label = label, unit = unit)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))
        content()
    }
}

/** `SPEED · MPH` — label in muted grey, the unit suffix dimmer still. No glow on small labels. */
@Composable
private fun Header(label: String, unit: String?) {
    val style = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
    )
    Row {
        Text(label, color = Muted, style = style)
        if (unit != null) {
            Text(" · $unit", color = Dim, style = style)
        }
    }
}

/** An Orbitron grid value with a single low-radius glow — sunlight legibility first. */
@Composable
private fun Value(text: String, sizeSp: Int, color: Color) {
    Text(
        text = text,
        color = color,
        textAlign = TextAlign.Center,
        style = TextStyle(
            fontFamily = Orbitron,
            fontWeight = FontWeight.Bold,
            fontSize = sizeSp.sp,
            lineHeight = (sizeSp * 0.82f).sp,
            letterSpacing = 0.sp,
            shadow = Shadow(color = color.copy(alpha = 0.45f), offset = Offset.Zero, blurRadius = 5f),
        ),
    )
}

/**
 * Degrees over cardinal in portrait; side-by-side, baseline-aligned in landscape.
 *
 * The cardinal is the ONLY fixed color on the ride screen — magenta, never the accent — so it
 * stays distinguishable from the degrees at a glance.
 */
@Composable
private fun Heading(headingDeg: Float, landscape: Boolean, accent: Color) {
    val degrees = String.format(java.util.Locale.US, "%03d°", headingDeg.roundToInt().mod(360))
    val dir = cardinal(headingDeg)

    if (landscape) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Value(text = degrees, sizeSp = 48, color = accent)
            Value(text = dir, sizeSp = 24, color = Magenta)
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Value(text = degrees, sizeSp = 44, color = accent)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))
            Value(text = dir, sizeSp = 22, color = Magenta)
        }
    }
}
