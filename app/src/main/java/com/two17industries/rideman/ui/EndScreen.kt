package com.two17industries.rideman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.two17industries.rideman.core.PlanGrading
import com.two17industries.rideman.core.PlanRide
import com.two17industries.rideman.core.RideSummary
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.core.Units
import com.two17industries.rideman.ui.theme.LocalAccent
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun EndScreen(
    summary: RideSummary,
    units: UnitSystem,
    planRide: PlanRide?,
    tolerancePercent: Int,
    onDone: () -> Unit,
) {
    val accent = LocalAccent.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("RIDE COMPLETE", color = accent, style = MaterialTheme.typography.titleLarge)

        if (planRide != null) {
            PlanResult(summary, planRide, tolerancePercent, units, accent)
        }

        Stat("TIME", formatDuration(summary.totalTimeMs), accent)
        if (planRide == null) {
            Stat("DISTANCE",
                "${String.format(Locale.US, "%.2f", Units.distance(summary.distanceM, units))} ${Units.distanceLabel(units)}",
                accent)
        }
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
private fun PlanResult(
    summary: RideSummary,
    planRide: PlanRide,
    tolerancePercent: Int,
    units: UnitSystem,
    accent: Color,
) {
    val met = PlanGrading.isMet(planRide, summary.distanceM, tolerancePercent)
    val amber = Color(0xFFFFCF3A)
    val actualMi = Units.distance(summary.distanceM, UnitSystem.AMERICAN)
    val shortByMi = planRide.targetMiles - actualMi

    Text(
        "Week ${planRide.week} · Ride ${planRide.slot} — ${planRide.kind}",
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        style = MaterialTheme.typography.bodyLarge,
    )

    val (bannerText, bannerColor) = if (met)
        "✓ TARGET MET · SLOT COMPLETE" to accent
    else
        "LOGGED · ${String.format(Locale.US, "%.1f", abs(shortByMi))} mi SHORT — SLOT STAYS OPEN" to amber

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bannerColor.copy(alpha = if (met) 1f else 0.18f)).padding(12.dp),
    ) {
        Text(
            bannerText,
            color = if (met) Color.Black else amber,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("DISTANCE", color = accent.copy(alpha = 0.6f), style = MaterialTheme.typography.labelLarge)
        Text(
            "target ${formatMiles(planRide.targetMiles)} mi  →  " +
                "${String.format(Locale.US, "%.2f", Units.distance(summary.distanceM, units))} ${Units.distanceLabel(units)}",
            color = if (met) accent else amber,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun Stat(label: String, value: String, accent: Color) {
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
