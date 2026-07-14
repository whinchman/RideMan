package com.two17industries.rideman.ui.ride

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.core.Units
import com.two17industries.rideman.ui.theme.LocalAccent
import com.two17industries.rideman.ui.theme.bigMetric
import kotlin.math.roundToInt

/** The canonical one-value-per-screen layout: small label, huge number, unit. */
@Composable
fun BigMetric(label: String, value: String, unit: String) {
    val accent = LocalAccent.current
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = accent, style = MaterialTheme.typography.labelLarge)
        Text(
            value,
            color = accent,
            style = bigMetric,
            textAlign = TextAlign.Center,
        )
        Text(unit, color = accent, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun SpeedometerScreen(speedMps: Float, units: UnitSystem) {
    BigMetric("SPEED", Units.speed(speedMps, units).roundToInt().toString(), Units.speedLabel(units))
}

@Composable
fun OdometerScreen(distanceM: Double, units: UnitSystem) {
    val v = Units.distance(distanceM, units)
    BigMetric("DISTANCE", String.format("%.2f", v), Units.distanceLabel(units))
}

@Composable
fun CompassScreen(headingDeg: Float) {
    BigMetric("HEADING", "${headingDeg.roundToInt() % 360}°", cardinal(headingDeg))
}

@Composable
fun AltimeterScreen(altitudeM: Double, units: UnitSystem) {
    val v = Units.altitude(altitudeM, units)
    BigMetric("ALTITUDE", v.roundToInt().toString(), Units.altitudeLabel(units))
}

private fun cardinal(deg: Float): String {
    val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val idx = (((deg % 360f) + 360f) % 360f / 45f).roundToInt() % 8
    return dirs[idx]
}
