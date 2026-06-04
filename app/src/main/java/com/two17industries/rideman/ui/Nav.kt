package com.two17industries.rideman.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.two17industries.rideman.core.RideSummary

private enum class Dest { START, SETTINGS, RIDE, END }

@Composable
fun RidemanNav(vm: RideViewModel, onRideActiveChanged: (Boolean) -> Unit) {
    var dest by remember { mutableStateOf(Dest.START) }
    var lastSummary by remember { mutableStateOf<RideSummary?>(null) }

    LaunchedEffect(dest) { onRideActiveChanged(dest == Dest.RIDE) }

    val settings by vm.settings.collectAsState()
    val ui by vm.ui.collectAsState()

    when (dest) {
        Dest.START -> {
            StartScreen(
                onStartRide = { vm.startRide(); dest = Dest.RIDE },
                onSettings = { dest = Dest.SETTINGS },
            )
        }
        Dest.SETTINGS -> {
            // Hardware back = cancel (discard edits) and return to Start.
            BackHandler { dest = Dest.START }
            SettingsScreen(
                current = settings,
                onSave = { vm.saveSettings(it) },
                onDone = { dest = Dest.START },
                onCancel = { dest = Dest.START },
            )
        }
        Dest.RIDE -> {
            // Hardware back ends the ride rather than dropping out of the app.
            BackHandler { lastSummary = vm.endRide(); dest = Dest.END }
            com.two17industries.rideman.ui.ride.RideScreen(
                state = ui,
                settings = settings,
                onEndRide = { lastSummary = vm.endRide(); dest = Dest.END },
            )
        }
        Dest.END -> {
            // Hardware back still persists the ride (same as DONE) so data isn't lost.
            BackHandler { vm.persistLastRide(); dest = Dest.START }
            EndScreen(
                summary = lastSummary ?: vm.endRide(),
                units = settings.units,
                onDone = { vm.persistLastRide(); dest = Dest.START },
            )
        }
    }
}
