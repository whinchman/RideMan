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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.two17industries.rideman.core.Plan
import com.two17industries.rideman.core.PlanAttempt
import com.two17industries.rideman.core.PlanGrading
import com.two17industries.rideman.core.PlanProgress
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.core.Units
import com.two17industries.rideman.core.slotsUncompletedBy
import com.two17industries.rideman.data.RideEntity
import com.two17industries.rideman.data.StravaUploadState
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
    onRetryUpload: (Long) -> Unit,
    onOpenActivity: (Long) -> Unit,
    stravaConnected: Boolean,
    onBackfill: () -> Unit,
    onDeleteRides: (List<Long>) -> Unit,
) {
    val accent = LocalAccent.current
    var expandedId by remember { mutableStateOf<Long?>(null) }

    // Rides staged for deletion; non-empty means the confirm dialog is showing.
    var pendingDelete by remember { mutableStateOf<List<RideEntity>>(emptyList()) }

    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }

    fun exitSelection() {
        selectionMode = false
        selected = emptySet()
    }

    if (pendingDelete.isNotEmpty()) {
        ConfirmDeleteDialog(
            rides = pendingDelete,
            allRides = rides,
            plan = plan,
            units = units,
            accent = accent,
            onDismiss = { pendingDelete = emptyList() },
            onConfirm = {
                onDeleteRides(pendingDelete.map { it.id })
                pendingDelete = emptyList()
                exitSelection()
            },
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        if (!selectionMode) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "◀ HISTORY",
                    color = accent,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.clickable(onClick = onBack),
                )
                if (rides.isNotEmpty()) {
                    Text(
                        "SELECT",
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable { selectionMode = true },
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "◀ CANCEL",
                    color = accent,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.clickable { exitSelection() },
                )
                Text(
                    "${selected.size} selected",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    if (selected.size == rides.size) "NONE" else "ALL",
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable {
                        selected = if (selected.size == rides.size) emptySet() else rides.map { it.id }.toSet()
                    },
                )
                Text(
                    "🗑 DELETE",
                    color = if (selected.isEmpty()) Color(0xFFFF5252).copy(alpha = 0.35f) else Color(0xFFFF5252),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable(enabled = selected.isNotEmpty()) {
                        pendingDelete = rides.filter { it.id in selected }
                    },
                )
            }
        }

        if (stravaConnected) {
            Text(
                "⭱ Upload past rides to Strava",
                color = accent,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.clickable(onClick = onBackfill).padding(bottom = 8.dp),
            )
        }

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
                    selectionMode = selectionMode,
                    checked = ride.id in selected,
                    expanded = !selectionMode && ride.id == expandedId,
                    onToggle = {
                        if (selectionMode) {
                            selected = if (ride.id in selected) selected - ride.id else selected + ride.id
                        } else {
                            expandedId = if (expandedId == ride.id) null else ride.id
                        }
                    },
                    onDelete = { pendingDelete = listOf(ride) },
                    onRetryUpload = onRetryUpload,
                    onOpenActivity = onOpenActivity,
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
    selectionMode: Boolean,
    checked: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRetryUpload: (Long) -> Unit,
    onOpenActivity: (Long) -> Unit,
    onDelete: () -> Unit,
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectionMode) {
                        Text(
                            if (checked) "☑" else "☐",
                            color = accent,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Text(formatDate(ride.startedAt), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyLarge)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StravaChip(
                        state = ride.stravaState,
                        activityId = ride.stravaActivityId,
                        accent = accent,
                        onRetry = { onRetryUpload(ride.id) },
                        onOpenActivity = onOpenActivity,
                    )
                    Text(if (planRide != null) "PLAN" else "FREE", color = if (planRide != null) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }
            val main = if (planRide != null)
                "Wk ${planRide.week} · Ride ${planRide.slot} — $dist $distLabel"
            else "$dist $distLabel"
            Text(main, color = if (expanded) accent else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)

            if (expanded) {
                if (planRide != null) {
                    val met = PlanGrading.isMet(planRide, ride.distanceM, plan!!.tolerancePercent)
                    DetailLine("target", "${formatPlanDistance(planRide.targetMiles, units)}  " + if (met) "✓ met" else "✗ short", accent, met)
                }
                DetailLine("time", formatHistoryDuration(ride.totalTimeMs), accent, null)
                DetailLine("avg speed", "${Units.speed(ride.avgSpeedMps, units).roundToInt()} ${Units.speedLabel(units)}", accent, null)
                if (planRide == null) {
                    DetailLine("max speed", "${Units.speed(ride.maxSpeedMps, units).roundToInt()} ${Units.speedLabel(units)}", accent, null)
                }
                Text(
                    "🗑 DELETE RIDE",
                    color = Color(0xFFFF5252),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clickable(onClick = onDelete),
                )
            }
        }
    }
}

@Composable
private fun StravaChip(
    state: StravaUploadState,
    activityId: Long?,
    accent: Color,
    onRetry: () -> Unit,
    onOpenActivity: (Long) -> Unit,
) {
    val label = when (state) {
        StravaUploadState.NONE -> return
        StravaUploadState.QUEUED -> "⏳ Queued"
        StravaUploadState.UPLOADING -> "↑ Uploading"
        StravaUploadState.UPLOADED -> "✓ Strava"
        StravaUploadState.FAILED -> "⚠ Retry"
    }
    val clickModifier = when {
        state == StravaUploadState.FAILED -> Modifier.clickable(onClick = onRetry)
        state == StravaUploadState.UPLOADED && activityId != null ->
            Modifier.clickable { onOpenActivity(activityId) }
        else -> Modifier
    }
    Text(
        label,
        color = if (state == StravaUploadState.FAILED) Color(0xFFFFCF3A) else accent,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelLarge,
        modifier = clickModifier,
    )
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

private val HISTORY_DATE_FMT = SimpleDateFormat("MMM d · h:mm a", Locale.US)

private fun formatDate(epochMillis: Long): String = HISTORY_DATE_FMT.format(Date(epochMillis))

private fun formatHistoryDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

@Composable
private fun ConfirmDeleteDialog(
    rides: List<RideEntity>,
    allRides: List<RideEntity>,
    plan: Plan?,
    units: UnitSystem,
    accent: Color,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val ids = rides.map { it.id }.toSet()

    // Strava keeps its copy — their v3 API has no delete endpoint, so we cannot remove it.
    val onStrava = rides.count { it.stravaState == StravaUploadState.UPLOADED }

    // Only warn when a slot actually regresses; a duplicate attempt changes nothing.
    val uncompleted = plan?.let { p ->
        val attempts = allRides.mapNotNull { r ->
            r.planRideId?.let { PlanAttempt(r.id, it, r.distanceM) }
        }
        slotsUncompletedBy(p, attempts, ids)
    }.orEmpty()

    val title = if (rides.size == 1) "Delete this ride?" else "Delete ${rides.size} rides?"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (rides.size == 1) {
                    val r = rides.first()
                    val dist = String.format(Locale.US, "%.1f", Units.distance(r.distanceM, units))
                    Text("${formatDate(r.startedAt)} · $dist ${Units.distanceLabel(units).lowercase()}")
                }
                Text("This also deletes the GPS track. It cannot be undone.")
                if (onStrava > 0) {
                    Text(
                        if (rides.size == 1) "This ride is on Strava. Deleting here will NOT remove it from Strava."
                        else "$onStrava of these are on Strava. Deleting here will NOT remove them from Strava.",
                        color = Color(0xFFFFCF3A),
                    )
                }
                if (uncompleted.isNotEmpty()) {
                    val slots = uncompleted.joinToString(", ") { "Wk ${it.week} · Ride ${it.slot}" }
                    Text(
                        "This will mark $slots incomplete again.",
                        color = Color(0xFFFFCF3A),
                    )
                }
            }
        },
        confirmButton = {
            Text(
                "DELETE",
                color = Color(0xFFFF5252),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onConfirm).padding(12.dp),
            )
        },
        dismissButton = {
            Text(
                "CANCEL",
                color = accent,
                modifier = Modifier.clickable(onClick = onDismiss).padding(12.dp),
            )
        },
    )
}
