package com.two17industries.rideman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.two17industries.rideman.core.Plan
import com.two17industries.rideman.core.PlanProgress
import com.two17industries.rideman.core.PlanRide
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.ui.theme.LocalAccent

@Composable
fun PlanPickerScreen(
    plan: Plan,
    progress: PlanProgress?,
    units: UnitSystem,
    onStart: (PlanRide) -> Unit,
    onBack: () -> Unit,
) {
    val accent = LocalAccent.current
    val defaultExpanded = progress?.nextIncomplete()?.id ?: plan.rides.firstOrNull()?.id
    var expandedId by rememberSaveable { mutableStateOf(defaultExpanded) }

    // Build the ordered display list: a phase header appears before its first ride,
    // a week header before that week's first ride.
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Text(
            "◀ 14-WEEK PLAN",
            color = accent,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.clickable(onClick = onBack).padding(bottom = 12.dp),
        )

        val listItems = remember(plan) { buildPlanListItems(plan) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listItems, key = { it.key }) { item ->
                when (item) {
                    is PlanListItem.PhaseHeaderItem -> PhaseHeader(item.number, item.name, accent)
                    is PlanListItem.WeekHeaderItem -> WeekHeader(item.week, item.recovery)
                    is PlanListItem.RideItem -> {
                        val ride = item.ride
                        val complete = progress?.isComplete(ride.id) == true
                        if (ride.id == expandedId) ExpandedRow(ride, complete, accent, units)
                        else CollapsedRow(ride, complete, accent, units) { expandedId = ride.id }
                    }
                }
            }
        }

        Button(
            onClick = { plan.byId[expandedId]?.let(onStart) },
            enabled = expandedId != null,
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { Text("START RIDE", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun PhaseHeader(number: Int, name: String, accent: Color) {
    Text(
        "PHASE $number · ${name.uppercase()}",
        color = accent.copy(alpha = 0.6f),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun WeekHeader(week: Int, recovery: Boolean) {
    Text(
        "Week $week" + if (recovery) " (recovery)" else "",
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun CollapsedRow(
    ride: PlanRide,
    complete: Boolean,
    accent: Color,
    units: UnitSystem,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(complete, accent)
        Spacer(Modifier.width(12.dp))
        Text(
            "Ride ${ride.slot} · ${ride.kind}",
            modifier = Modifier.weight(1f),
            color = if (complete) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            formatPlanDistance(ride.targetMiles, units),
            color = if (complete) accent.copy(alpha = 0.5f) else accent,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun StatusDot(complete: Boolean, accent: Color) {
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .then(
                if (complete) Modifier.background(accent)
                else Modifier.border(1.5.dp, accent.copy(alpha = 0.5f), CircleShape)
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (complete) {
            Text("✓", color = Color.Black, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ExpandedRow(
    ride: PlanRide,
    complete: Boolean,
    accent: Color,
    units: UnitSystem,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, accent, RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.08f))
            .padding(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Week ${ride.week} · Ride ${ride.slot} — ${ride.kind}",
                color = accent, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            if (complete) {
                Text("✓ DONE", color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(12.dp))
        DetailRow("TARGET", formatPlanDistance(ride.targetMiles, units), accent, valueStrong = true)
        DetailRow("PACE", ride.pace.name.lowercase(), accent, valueStrong = false)
        if (ride.guidance.isNotBlank()) {
            Text(
                ride.guidance,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (ride.longRide) {
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(accent)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text("★ LONG RIDE", color = Color.Black, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, accent: Color, valueStrong: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), style = MaterialTheme.typography.labelMedium)
        Text(
            value,
            color = accent,
            fontWeight = if (valueStrong) FontWeight.Bold else FontWeight.Normal,
            style = if (valueStrong) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
        )
    }
}

private sealed interface PlanListItem {
    val key: String
    data class PhaseHeaderItem(val number: Int, val name: String) : PlanListItem {
        override val key get() = "phase-$number"
    }
    data class WeekHeaderItem(val week: Int, val recovery: Boolean) : PlanListItem {
        override val key get() = "week-$week"
    }
    data class RideItem(val ride: PlanRide) : PlanListItem {
        override val key get() = ride.id
    }
}

/** Flatten the plan into a stable, ordered list of headers + ride rows (single sequential pass). */
private fun buildPlanListItems(plan: Plan): List<PlanListItem> {
    val out = mutableListOf<PlanListItem>()
    var lastPhase = -1
    var lastWeek = -1
    for (ride in plan.rides) {
        if (ride.phaseNumber != lastPhase) {
            lastPhase = ride.phaseNumber
            out += PlanListItem.PhaseHeaderItem(ride.phaseNumber, ride.phaseName)
        }
        if (ride.week != lastWeek) {
            lastWeek = ride.week
            out += PlanListItem.WeekHeaderItem(ride.week, ride.recoveryWeek)
        }
        out += PlanListItem.RideItem(ride)
    }
    return out
}
