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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.RectangleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.two17industries.rideman.core.Plan
import com.two17industries.rideman.core.PlanProgress
import com.two17industries.rideman.core.PlanRide
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.ui.components.BackLabel
import com.two17industries.rideman.ui.components.CheckSquare
import com.two17industries.rideman.ui.components.HairLine
import com.two17industries.rideman.ui.components.PromptLabel
import com.two17industries.rideman.ui.components.TerminalButton
import com.two17industries.rideman.ui.components.TerminalButtonStyle
import com.two17industries.rideman.ui.components.glow
import com.two17industries.rideman.ui.theme.Background
import com.two17industries.rideman.ui.theme.BorderCyanDim
import com.two17industries.rideman.ui.theme.Cyan
import com.two17industries.rideman.ui.theme.Dim
import com.two17industries.rideman.ui.theme.Muted
import com.two17industries.rideman.ui.theme.TextPrimary

@Composable
fun PlanPickerScreen(
    plan: Plan,
    progress: PlanProgress?,
    units: UnitSystem,
    onStart: (PlanRide) -> Unit,
    onBack: () -> Unit,
) {
    val defaultExpanded = progress?.nextIncomplete()?.id ?: plan.rides.firstOrNull()?.id
    var expandedId by rememberSaveable { mutableStateOf(defaultExpanded) }

    // Build the ordered display list: a phase header appears before its first ride,
    // a week header before that week's first ride.
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp, vertical = 16.dp)) {
        BackLabel(
            "14-WEEK PLAN",
            modifier = Modifier.clickable(onClick = onBack).padding(bottom = 12.dp),
        )

        val listItems = remember(plan) { buildPlanListItems(plan) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listItems, key = { it.key }) { item ->
                when (item) {
                    is PlanListItem.PhaseHeaderItem -> PhaseHeader(item.number, item.name)
                    is PlanListItem.WeekHeaderItem -> WeekHeader(item.week, item.recovery)
                    is PlanListItem.RideItem -> {
                        val ride = item.ride
                        val complete = progress?.isComplete(ride.id) == true
                        if (ride.id == expandedId) ExpandedRow(ride, complete, units)
                        else CollapsedRow(ride, complete, units) { expandedId = ride.id }
                    }
                }
            }
        }

        TerminalButton(
            text = "START RIDE",
            onClick = { plan.byId[expandedId]?.let(onStart) },
            enabled = expandedId != null,
            style = TerminalButtonStyle.PRIMARY,
            fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}

@Composable
private fun PhaseHeader(number: Int, name: String) {
    PromptLabel(
        "PHASE $number · ${name.uppercase()}",
        color = Muted,
        fontSize = 10.sp,
        letterSpacing = 1.6.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun WeekHeader(week: Int, recovery: Boolean) {
    Text(
        "Week $week" + if (recovery) " (recovery)" else "",
        color = Dim,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp),
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun CollapsedRow(
    ride: PlanRide,
    complete: Boolean,
    units: UnitSystem,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckSquare(checked = complete, accent = Cyan, size = 18.dp)
        Spacer(Modifier.width(11.dp))
        Text(
            "Ride ${ride.slot} · ${ride.kind}",
            modifier = Modifier.weight(1f),
            color = if (complete) Dim else TextPrimary,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
        )
        Text(
            formatPlanDistance(ride.targetMiles, units),
            color = if (complete) Cyan.copy(alpha = 0.5f) else Cyan,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
        )
    }
}

@Composable
private fun ExpandedRow(
    ride: PlanRide,
    complete: Boolean,
    units: UnitSystem,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Cyan, RectangleShape)
            .background(Cyan.copy(alpha = 0.06f), RectangleShape)
            .padding(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "WK ${ride.week} · RIDE ${ride.slot} - ${ride.kind}",
                color = Cyan,
                style = MaterialTheme.typography.titleLarge
                    .copy(fontSize = 13.sp, letterSpacing = 0.5.sp)
                    .glow(Cyan, blurRadius = 5f),
            )
            if (complete) {
                Text(
                    "✓ DONE",
                    color = Cyan,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp, letterSpacing = 1.2.sp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        HairLine(color = BorderCyanDim)
        Spacer(Modifier.height(9.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "TARGET",
                color = Muted,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp, letterSpacing = 1.2.sp),
            )
            Text(
                formatPlanDistance(ride.targetMiles, units),
                color = Cyan,
                style = MaterialTheme.typography.titleLarge
                    .copy(fontSize = 26.sp, letterSpacing = 0.sp)
                    .glow(Cyan, blurRadius = 6f),
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "PACE",
                color = Muted,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp, letterSpacing = 1.2.sp),
            )
            Text(
                ride.pace.name.lowercase(),
                color = Cyan,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
            )
        }

        if (ride.guidance.isNotBlank()) {
            Text(
                ride.guidance,
                color = Muted,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp, lineHeight = 18.sp),
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        if (ride.longRide) {
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .background(Cyan, RectangleShape)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Text(
                    "★ LONG RIDE",
                    color = Background,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp, letterSpacing = 0.6.sp),
                )
            }
        }
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
