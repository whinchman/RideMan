package com.two17industries.rideman.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.two17industries.rideman.core.PlanGrading
import com.two17industries.rideman.core.PlanRide
import com.two17industries.rideman.core.RideSummary
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.core.Units
import com.two17industries.rideman.ui.components.TerminalButton
import com.two17industries.rideman.ui.components.TerminalButtonStyle
import com.two17industries.rideman.ui.components.glow
import com.two17industries.rideman.ui.theme.Background
import com.two17industries.rideman.ui.theme.LocalAccent
import com.two17industries.rideman.ui.theme.Muted
import com.two17industries.rideman.ui.theme.Warn
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
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(
                "RIDE COMPLETE",
                color = accent,
                style = MaterialTheme.typography.titleLarge
                    .copy(fontSize = 20.sp, letterSpacing = 1.2.sp)
                    .glow(accent, blurRadius = 6f),
            )

            if (planRide != null) {
                PlanResult(summary, planRide, tolerancePercent, units, accent)
            }

            val stats = buildList {
                add("TIME" to Units.duration(summary.totalTimeMs))
                if (planRide == null) {
                    add("DISTANCE" to
                        "${String.format(Locale.US, "%.2f", Units.distance(summary.distanceM, units))} ${Units.distanceLabel(units)}")
                }
                add("MAX SPEED" to
                    "${Units.speed(summary.maxSpeedMps, units).roundToInt()} ${Units.speedLabel(units)}")
                add("AVG SPEED" to
                    "${Units.speed(summary.avgSpeedMps, units).roundToInt()} ${Units.speedLabel(units)}")
            }

            val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            val gridLine = accent.copy(alpha = 0.12f)
            if (landscape) {
                // Single 4-up row. The 1dp gaps sit on a grid-line-coloured background, so the
                // dividers are the background showing through — no per-cell borders.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, gridLine, RectangleShape)
                        .background(gridLine, RectangleShape),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    stats.forEach { (label, value) -> StatCell(label, value, accent, Modifier.weight(1f)) }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, gridLine, RectangleShape)
                        .background(gridLine, RectangleShape),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    stats.forEach { (label, value) -> StatRow(label, value, accent) }
                }
            }

            TerminalButton(
                text = "DONE",
                onClick = onDone,
                style = TerminalButtonStyle.PRIMARY,
                accent = accent,
                fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            )
        }
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
    val label = Units.distanceLabel(units)
    val targetDisplay = Units.distance(planRide.targetMiles * PlanGrading.METERS_PER_MILE, units)
    val actualDisplay = Units.distance(summary.distanceM, units)
    val shortBy = targetDisplay - actualDisplay

    Text(
        "Week ${planRide.week} · Ride ${planRide.slot} — ${planRide.kind}",
        color = Muted,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
    )

    val bannerText = if (met) "✓ TARGET MET · SLOT COMPLETE"
        else "LOGGED · ${String.format(Locale.US, "%.2f", abs(shortBy))} $label SHORT — SLOT STAYS OPEN"

    Box(
        Modifier
            .fillMaxWidth()
            .then(
                if (met) Modifier.background(accent, RectangleShape)
                else Modifier
                    .border(1.dp, Warn, RectangleShape)
                    .background(Warn.copy(alpha = 0.18f), RectangleShape)
            )
            .padding(13.dp),
    ) {
        Text(
            bannerText,
            color = if (met) Background else Warn,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 13.sp, letterSpacing = 0.7.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "DISTANCE",
            color = Muted,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp, letterSpacing = 1.4.sp),
        )
        Text(
            "target ${String.format(Locale.US, "%.1f", targetDisplay)} $label  →  " +
                "${String.format(Locale.US, "%.2f", actualDisplay)} $label",
            color = if (met) accent else Warn,
            style = MaterialTheme.typography.titleLarge
                .copy(fontSize = 17.sp, letterSpacing = 0.4.sp)
                .glow(if (met) accent else Warn, blurRadius = 5f),
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@Composable
private fun StatRow(label: String, value: String, accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Background, RectangleShape)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = Muted,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 1.3.sp),
            maxLines = 1,
        )
        Text(
            value,
            color = accent,
            style = MaterialTheme.typography.titleLarge
                .copy(fontSize = 20.sp, letterSpacing = 0.sp)
                .glow(accent, blurRadius = 5f),
            maxLines = 1,
        )
    }
}

@Composable
private fun StatCell(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Background, RectangleShape)
            .padding(horizontal = 8.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            color = Muted,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp, letterSpacing = 1.2.sp),
            maxLines = 1,
        )
        Text(
            value,
            color = accent,
            style = MaterialTheme.typography.titleLarge
                .copy(fontSize = 24.sp, letterSpacing = 0.sp)
                .glow(accent, blurRadius = 5f),
            maxLines = 1,
        )
    }
}
