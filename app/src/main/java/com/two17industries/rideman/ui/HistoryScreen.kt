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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.two17industries.rideman.core.Plan
import com.two17industries.rideman.core.PlanAttempt
import com.two17industries.rideman.core.PlanGrading
import com.two17industries.rideman.core.PlanProgress
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.core.Units
import com.two17industries.rideman.core.slotsUncompletedBy
import com.two17industries.rideman.data.RideEntity
import com.two17industries.rideman.data.StravaUploadState
import com.two17industries.rideman.ui.components.BackLabel
import com.two17industries.rideman.ui.components.CheckSquare
import com.two17industries.rideman.ui.components.HairLine
import com.two17industries.rideman.ui.components.PromptLabel
import com.two17industries.rideman.ui.components.TerminalButton
import com.two17industries.rideman.ui.components.TerminalButtonStyle
import com.two17industries.rideman.ui.components.glow
import com.two17industries.rideman.ui.theme.BorderCyanDim
import com.two17industries.rideman.ui.theme.Cyan
import com.two17industries.rideman.ui.theme.Delete
import com.two17industries.rideman.ui.theme.Dim
import com.two17industries.rideman.ui.theme.Muted
import com.two17industries.rideman.ui.theme.Surface1
import com.two17industries.rideman.ui.theme.TextPrimary
import com.two17industries.rideman.ui.theme.Warn
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
            onDismiss = { pendingDelete = emptyList() },
            onConfirm = {
                onDeleteRides(pendingDelete.map { it.id })
                pendingDelete = emptyList()
                exitSelection()
            },
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp, vertical = 16.dp)) {
        if (!selectionMode) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackLabel("HISTORY", modifier = Modifier.clickable(onClick = onBack))
                if (rides.isNotEmpty()) {
                    Text(
                        "SELECT",
                        color = Cyan,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, letterSpacing = 1.2.sp),
                        modifier = Modifier.clickable { selectionMode = true },
                    )
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackLabel("CANCEL", modifier = Modifier.clickable { exitSelection() }, fontSize = 15.sp)
                Text(
                    "${selected.size} selected",
                    color = Muted,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp),
                )
                Text(
                    if (selected.size == rides.size) "NONE" else "ALL",
                    color = Cyan,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, letterSpacing = 1.2.sp),
                    modifier = Modifier.clickable {
                        selected = if (selected.size == rides.size) emptySet() else rides.map { it.id }.toSet()
                    },
                )
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(enabled = selected.isNotEmpty()) {
                            pendingDelete = rides.filter { it.id in selected }
                        }
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "✕ DELETE",
                        color = if (selected.isEmpty()) Delete.copy(alpha = 0.35f) else Delete,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, letterSpacing = 1.2.sp),
                    )
                }
            }
        }

        if (stravaConnected) {
            Text(
                "↑ Upload past rides to Strava",
                color = Cyan.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp),
                modifier = Modifier.clickable(onClick = onBackfill).padding(bottom = 10.dp),
            )
        }

        if (progress != null) {
            ProgressHeader(progress)
            Spacer(Modifier.height(12.dp))
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(rides, key = { it.id }) { ride ->
                RideRow(
                    ride = ride,
                    plan = plan,
                    units = units,
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
private fun ProgressHeader(progress: PlanProgress) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, BorderCyanDim, RectangleShape)
            .background(Cyan.copy(alpha = 0.05f), RectangleShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        PromptLabel("PLAN PROGRESS", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Text(
            "${progress.completedCount()} / ${progress.total} rides",
            color = Cyan,
            style = MaterialTheme.typography.titleLarge
                .copy(fontSize = 24.sp, letterSpacing = 0.sp)
                .glow(Cyan, blurRadius = 6f),
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@Composable
private fun RideRow(
    ride: RideEntity,
    plan: Plan?,
    units: UnitSystem,
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

    Box(
        Modifier
            .fillMaxWidth()
            .then(
                if (expanded) Modifier
                    .border(1.dp, Cyan.copy(alpha = 0.35f), RectangleShape)
                    .background(Cyan.copy(alpha = 0.06f), RectangleShape)
                else Modifier.background(Surface1, RectangleShape)
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectionMode) {
                        CheckSquare(checked = checked, accent = Cyan, size = 16.dp, glyphSize = 11.sp)
                    }
                    Text(
                        formatDate(ride.startedAt),
                        color = Muted,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 11.sp),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StravaChip(
                        state = ride.stravaState,
                        activityId = ride.stravaActivityId,
                        onRetry = { onRetryUpload(ride.id) },
                        onOpenActivity = onOpenActivity,
                    )
                    Text(
                        if (planRide != null) "PLAN" else "FREE",
                        color = if (planRide != null) Cyan else Dim,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 0.7.sp),
                    )
                }
            }

            val main = if (planRide != null)
                "Wk ${planRide.week} · Ride ${planRide.slot} - $dist $distLabel"
            else "$dist $distLabel"
            Text(
                main,
                color = if (expanded) Cyan else TextPrimary,
                style = MaterialTheme.typography.titleLarge
                    .copy(fontSize = 15.sp, letterSpacing = 0.3.sp)
                    .let { if (expanded) it.glow(Cyan, blurRadius = 5f) else it },
                modifier = Modifier.padding(top = 8.dp),
            )

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HairLine(color = BorderCyanDim)
                if (planRide != null) {
                    val met = PlanGrading.isMet(planRide, ride.distanceM, plan!!.tolerancePercent)
                    DetailLine(
                        "target",
                        "${formatPlanDistance(planRide.targetMiles, units)}  " + if (met) "✓ met" else "✕ short",
                        met,
                    )
                }
                DetailLine("time", Units.duration(ride.totalTimeMs), null)
                DetailLine("avg speed", "${Units.speed(ride.avgSpeedMps, units).roundToInt()} ${Units.speedLabel(units)}", null)
                if (planRide == null) {
                    DetailLine("max speed", "${Units.speed(ride.maxSpeedMps, units).roundToInt()} ${Units.speedLabel(units)}", null)
                }
                TerminalButton(
                    text = "✕ DELETE RIDE",
                    onClick = onDelete,
                    accent = Delete,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun StravaChip(
    state: StravaUploadState,
    activityId: Long?,
    onRetry: () -> Unit,
    onOpenActivity: (Long) -> Unit,
) {
    // No emoji: the hourglass (U+23F3) renders in colour on Android and has no text-presentation
    // form, so it is dropped outright rather than substituted.
    val label = when (state) {
        StravaUploadState.NONE -> return
        StravaUploadState.QUEUED -> "QUEUED"
        StravaUploadState.UPLOADING -> "↑ UPLOADING"
        StravaUploadState.UPLOADED -> "✓ STRAVA"
        StravaUploadState.FAILED -> "⚠ RETRY"
    }
    val clickModifier = when {
        state == StravaUploadState.FAILED -> Modifier.clickable(onClick = onRetry)
        state == StravaUploadState.UPLOADED && activityId != null ->
            Modifier.clickable { onOpenActivity(activityId) }
        else -> Modifier
    }
    Text(
        label,
        color = when (state) {
            StravaUploadState.FAILED -> Warn
            StravaUploadState.QUEUED -> Muted
            else -> Cyan
        },
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 0.6.sp),
        modifier = clickModifier,
    )
}

@Composable
private fun DetailLine(label: String, value: String, met: Boolean?) {
    Row(
        Modifier.fillMaxWidth().padding(top = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Muted, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp))
        Text(
            value,
            color = when (met) { true -> Cyan; false -> Warn; null -> TextPrimary },
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, letterSpacing = 0.sp),
        )
    }
}

private val HISTORY_DATE_FMT = SimpleDateFormat("MMM d · h:mm a", Locale.US)

private fun formatDate(epochMillis: Long): String = HISTORY_DATE_FMT.format(Date(epochMillis))

@Composable
private fun ConfirmDeleteDialog(
    rides: List<RideEntity>,
    allRides: List<RideEntity>,
    plan: Plan?,
    units: UnitSystem,
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
        shape = RectangleShape,
        containerColor = Surface1,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, letterSpacing = 0.5.sp))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (rides.size == 1) {
                    val r = rides.first()
                    val dist = String.format(Locale.US, "%.1f", Units.distance(r.distanceM, units))
                    Text(
                        "${formatDate(r.startedAt)} · $dist ${Units.distanceLabel(units).lowercase()}",
                        color = Muted,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                    )
                }
                Text(
                    "This also deletes the GPS track. It cannot be undone.",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                )
                if (onStrava > 0) {
                    Text(
                        if (rides.size == 1) "This ride is on Strava. Deleting here will NOT remove it from Strava."
                        else "$onStrava of these are on Strava. Deleting here will NOT remove them from Strava.",
                        color = Warn,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                    )
                }
                if (uncompleted.isNotEmpty()) {
                    val slots = uncompleted.joinToString(", ") { "Wk ${it.week} · Ride ${it.slot}" }
                    Text(
                        "This will mark $slots incomplete again.",
                        color = Warn,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                    )
                }
            }
        },
        confirmButton = {
            Text(
                "✕ DELETE",
                color = Delete,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, letterSpacing = 1.sp),
                modifier = Modifier.clickable(onClick = onConfirm).padding(12.dp),
            )
        },
        dismissButton = {
            Text(
                "CANCEL",
                color = Cyan,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, letterSpacing = 1.sp),
                modifier = Modifier.clickable(onClick = onDismiss).padding(12.dp),
            )
        },
    )
}
