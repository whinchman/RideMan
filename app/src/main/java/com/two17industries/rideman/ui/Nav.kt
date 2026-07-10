package com.two17industries.rideman.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import com.two17industries.rideman.core.PlanRide
import com.two17industries.rideman.core.RideSummary

private enum class Dest { START, SETTINGS, RIDE, END, PLAN_PICKER, HISTORY, BACKFILL }

@Composable
fun RidemanNav(vm: RideViewModel, onRideActiveChanged: (Boolean) -> Unit) {
    var dest by remember { mutableStateOf(Dest.START) }
    var lastSummary by remember { mutableStateOf<RideSummary?>(null) }
    var activePlanRide by remember { mutableStateOf<PlanRide?>(null) }

    LaunchedEffect(dest) { onRideActiveChanged(dest == Dest.RIDE) }

    val settings by vm.settings.collectAsState()
    val ui by vm.ui.collectAsState()
    val progress by vm.progress.collectAsState()
    val allRides by vm.allRides.collectAsState()
    val stravaConnected by vm.stravaConnected.collectAsState()
    val stravaAthleteName by vm.stravaAthleteName.collectAsState()

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
                    com.two17industries.rideman.strava.CustomTabLauncher.launch(context, vm.connectStravaUrl())
                },
                onDisconnectStrava = { vm.disconnectStrava() },
                onSave = { vm.saveSettings(it) },
                onDone = { dest = Dest.START },
                onCancel = { dest = Dest.START },
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
