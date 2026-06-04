package com.two17industries.rideman.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.two17industries.rideman.core.RideSummary
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.core.Units
import com.two17industries.rideman.ui.theme.LocalAccent
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EndScreen(summary: RideSummary, units: UnitSystem, onDone: () -> Unit) {
    val accent = LocalAccent.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text("RIDE COMPLETE", color = accent, style = MaterialTheme.typography.titleLarge)
        Stat("TIME", formatDuration(summary.totalTimeMs), accent)
        Stat("DISTANCE",
            "${String.format(Locale.US, "%.2f", Units.distance(summary.distanceM, units))} ${Units.distanceLabel(units)}",
            accent)
        Stat("MAX SPEED",
            "${Units.speed(summary.maxSpeedMps, units).roundToInt()} ${Units.speedLabel(units)}",
            accent)
        Stat("AVG SPEED",
            "${Units.speed(summary.avgSpeedMps, units).roundToInt()} ${Units.speedLabel(units)}",
            accent)
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        ) { Text("DONE", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun Stat(label: String, value: String, accent: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = accent.copy(alpha = 0.6f), style = MaterialTheme.typography.labelLarge)
        Text(value, color = accent, style = MaterialTheme.typography.titleLarge)
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}
