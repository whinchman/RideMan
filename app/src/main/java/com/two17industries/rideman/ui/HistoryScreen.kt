package com.two17industries.rideman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.two17industries.rideman.core.Plan
import com.two17industries.rideman.core.PlanGrading
import com.two17industries.rideman.core.PlanProgress
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.core.Units
import com.two17industries.rideman.data.RideEntity
import com.two17industries.rideman.ui.theme.LocalAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
    rides: List<RideEntity>,
    plan: Plan?,
    progress: PlanProgress?,
    units: UnitSystem,
    onBack: () -> Unit,
) {
    val accent = LocalAccent.current
    var expandedId by remember { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "◀ HISTORY",
            color = accent,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.clickable(onClick = onBack).padding(bottom = 12.dp),
        )

        if (progress != null) {
            ProgressHeader(progress, accent)
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(rides, key = { it.id }) { ride ->
                RideRow(
                    ride = ride,
                    plan = plan,
                    units = units,
                    accent = accent,
                    expanded = ride.id == expandedId,
                    onToggle = { expandedId = if (expandedId == ride.id) null else ride.id },
                )
            }
        }
    }
}

@Composable
private fun ProgressHeader(progress: PlanProgress, accent: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.08f))
            .padding(12.dp),
    ) {
        Column {
            Text("PLAN PROGRESS", color = accent.copy(alpha = 0.6f), style = MaterialTheme.typography.labelLarge)
            Text(
                "${progress.completedCount()} / ${progress.total} rides",
                color = accent, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
private fun RideRow(
    ride: RideEntity,
    plan: Plan?,
    units: UnitSystem,
    accent: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val planRide = ride.planRideId?.let { plan?.byId?.get(it) }
    val dist = String.format(Locale.US, "%.1f", Units.distance(ride.distanceM, units))
    val distLabel = Units.distanceLabel(units).lowercase()

    val bg = if (expanded) accent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (expanded) 12.dp else 10.dp))
            .background(bg)
            .clickable(onClick = onToggle)
            .padding(12.dp),
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDate(ride.startedAt), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyLarge)
                Text(if (planRide != null) "PLAN" else "FREE", color = if (planRide != null) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
            val main = if (planRide != null)
                "Wk ${planRide.week} · Ride ${planRide.slot} — $dist $distLabel"
            else "$dist $distLabel"
            Text(main, color = if (expanded) accent else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)

            if (expanded) {
                if (planRide != null) {
                    val met = PlanGrading.isMet(planRide, ride.distanceM, plan!!.tolerancePercent)
                    DetailLine("target", "${formatMiles(planRide.targetMiles)} mi  " + if (met) "✓ met" else "✗ short", accent, met)
                }
                DetailLine("time", formatHistoryDuration(ride.totalTimeMs), accent, null)
                DetailLine("avg speed", "${Units.speed(ride.avgSpeedMps, units).roundToInt()} ${Units.speedLabel(units)}", accent, null)
                if (planRide == null) {
                    DetailLine("max speed", "${Units.speed(ride.maxSpeedMps, units).roundToInt()} ${Units.speedLabel(units)}", accent, null)
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String, accent: Color, met: Boolean?) {
    Row(
        Modifier.fillMaxWidth().padding(top = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            color = when (met) { true -> accent; false -> Color(0xFFFFCF3A); null -> MaterialTheme.colorScheme.onSurface },
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("MMM d · h:mm a", Locale.US).format(Date(epochMillis))

private fun formatHistoryDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}
