package com.two17industries.rideman.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.sp
import com.two17industries.rideman.core.PlanRide
import com.two17industries.rideman.core.RideSummary
import com.two17industries.rideman.data.effectiveMaxHeartRate
import com.two17industries.rideman.ui.components.TerminalButton
import com.two17industries.rideman.ui.theme.Cyan
import com.two17industries.rideman.ui.theme.Surface1
import com.two17industries.rideman.ui.theme.TextPrimary
import kotlinx.coroutines.flow.StateFlow

private enum class Dest { START, SETTINGS, RIDE, END, PLAN_PICKER, HISTORY, BACKFILL, HR_CALIBRATION }

@Composable
fun RidemanNav(
    vm: RideViewModel,
    blePermissionsGranted: StateFlow<Boolean>,
    onRideActiveChanged: (Boolean) -> Unit,
    onRequestBlePermissions: () -> Unit,
) {
    var dest by remember { mutableStateOf(Dest.START) }
    var lastSummary by remember { mutableStateOf<RideSummary?>(null) }
    var activePlanRide by remember { mutableStateOf<PlanRide?>(null) }

    // Max HR is age-derived when not set explicitly, so it needs the current year.
    val currentYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }

    LaunchedEffect(dest) { onRideActiveChanged(dest == Dest.RIDE) }

    val settings by vm.settings.collectAsState()
    val ui by vm.ui.collectAsState()
    val progress by vm.progress.collectAsState()
    val allRides by vm.allRides.collectAsState()
    val stravaConnected by vm.stravaConnected.collectAsState()
    val stravaAthleteName by vm.stravaAthleteName.collectAsState()
    val bleGranted by blePermissionsGranted.collectAsState()

    // The auto-raise happens at ride save, which can resolve after the rider has already
    // navigated away from END — so this dialog is collected here, not scoped to a Dest branch.
    val maxHrRaised by vm.maxHrRaised.collectAsState()
    maxHrRaised?.let { raise ->
        AlertDialog(
            onDismissRequest = { vm.clearMaxHrRaised() },
            containerColor = Surface1,
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary,
            title = {
                Text(
                    "MAX HEART RATE RAISED",
                    color = Cyan,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
                )
            },
            text = {
                Text(
                    if (raise.from != null) {
                        "Your ride held ${raise.to} bpm, above your previous ${raise.from}. " +
                            "Zones have been updated — including on past rides. " +
                            "You can change this in Settings."
                    } else {
                        "Your ride held ${raise.to} bpm. Zones are now based on ${raise.to} bpm. " +
                            "You can change this in Settings."
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                )
            },
            confirmButton = {
                TerminalButton(text = "OK", onClick = { vm.clearMaxHrRaised() }, fontSize = 13.sp)
            },
        )
    }

    when (dest) {
        Dest.START -> {
            StartScreen(
                nextUp = progress?.nextIncomplete(),
                planAvailable = vm.plan != null,
                units = settings.units,
                onPlanRide = { dest = Dest.PLAN_PICKER },
                onFreeRide = { activePlanRide = null; vm.startRide(null); dest = Dest.RIDE },
                onHistory = { dest = Dest.HISTORY },
                onSettings = { dest = Dest.SETTINGS },
            )
        }
        Dest.PLAN_PICKER -> {
            BackHandler { dest = Dest.START }
            val plan = vm.plan
            if (plan == null) {
                dest = Dest.START
            } else {
                PlanPickerScreen(
                    plan = plan,
                    progress = progress,
                    units = settings.units,
                    onStart = { ride ->
                        activePlanRide = ride
                        vm.startRide(ride.id)
                        dest = Dest.RIDE
                    },
                    onBack = { dest = Dest.START },
                )
            }
        }
        Dest.SETTINGS -> {
            BackHandler { dest = Dest.START }
            val context = androidx.compose.ui.platform.LocalContext.current
            SettingsScreen(
                current = settings,
                stravaConnected = stravaConnected,
                stravaAthleteName = stravaAthleteName,
                onConnectStrava = {
                    // Implicit intent, not a Custom Tab: the flow ends on a rideman:// redirect,
                    // which a browser tab cannot follow.
                    com.two17industries.rideman.strava.StravaAuthorizeLauncher.launch(context, vm.connectStravaUrl())
                },
                onDisconnectStrava = { vm.disconnectStrava() },
                onSave = { vm.saveSettings(it) },
                onDone = { dest = Dest.START },
                onCancel = { dest = Dest.START },
                onOpenCalibration = { dest = Dest.HR_CALIBRATION },
                blePermissionsGranted = bleGranted,
                onRequestBlePermissions = onRequestBlePermissions,
            )
        }
        Dest.HISTORY -> {
            BackHandler { dest = Dest.START }
            val context = androidx.compose.ui.platform.LocalContext.current
            HistoryScreen(
                rides = allRides,
                plan = vm.plan,
                progress = progress,
                units = settings.units,
                onBack = { dest = Dest.START },
                onRetryUpload = { vm.retryUpload(it) },
                onOpenActivity = { id ->
                    com.two17industries.rideman.strava.CustomTabLauncher.launch(context, "https://www.strava.com/activities/$id")
                },
                stravaConnected = stravaConnected,
                onBackfill = { dest = Dest.BACKFILL },
                onDeleteRides = { vm.deleteRides(it) },
                maxHr = settings.effectiveMaxHeartRate(currentYear),
                baselineHr = settings.baselineHeartRateBpm,
                loadHeartRateSamples = { id -> vm.heartRateSamples(id) },
            )
        }
        Dest.HR_CALIBRATION -> {
            BackHandler { dest = Dest.SETTINGS }
            HeartRateCalibrationScreen(
                // The screen connects the strap itself for the length of its composition — no
                // ride is running here, so nothing else would.
                hrmAddress = settings.hrmAddress,
                // For the sanity check on the result: a seated baseline at or above max HR is
                // not a physiological reading, and HeartRateZones would silently ignore it.
                maxHr = settings.effectiveMaxHeartRate(currentYear),
                onSave = { bpm, at ->
                    vm.saveSettings(
                        settings.copy(baselineHeartRateBpm = bpm, baselineCalibratedAtMillis = at)
                    )
                },
                onDone = { dest = Dest.SETTINGS },
            )
        }
        Dest.BACKFILL -> {
            BackHandler { dest = Dest.HISTORY }
            BackfillScreen(
                rides = allRides,
                units = settings.units,
                onUpload = { vm.backfillUpload(it) },
                onDone = { dest = Dest.HISTORY },
            )
        }
        Dest.RIDE -> {
            BackHandler { lastSummary = vm.endRide(); dest = Dest.END }
            com.two17industries.rideman.ui.ride.RideScreen(
                state = ui,
                settings = settings,
                onEndRide = { lastSummary = vm.endRide(); dest = Dest.END },
                onToggleOrientation = { vm.toggleRideOrientation() },
            )
        }
        Dest.END -> {
            BackHandler { vm.persistLastRide(); dest = Dest.START }
            EndScreen(
                summary = lastSummary ?: vm.endRide(),
                units = settings.units,
                planRide = activePlanRide,
                tolerancePercent = vm.plan?.tolerancePercent ?: 0,
                onDone = { vm.persistLastRide(); dest = Dest.START },
            )
        }
    }
}
