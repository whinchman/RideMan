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

private enum class Dest { START, SETTINGS, RIDE, END, PLAN_PICKER, HISTORY }

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
            SettingsScreen(
                current = settings,
                onSave = { vm.saveSettings(it) },
                onDone = { dest = Dest.START },
                onCancel = { dest = Dest.START },
            )
        }
        Dest.HISTORY -> {
            BackHandler { dest = Dest.START }
            HistoryScreen(
                rides = allRides,
                plan = vm.plan,
                progress = progress,
                units = settings.units,
                onBack = { dest = Dest.START },
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
