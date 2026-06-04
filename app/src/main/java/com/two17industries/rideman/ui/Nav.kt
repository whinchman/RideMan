package com.two17industries.rideman.ui

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
        Dest.SETTINGS -> SettingsScreen(
            current = settings,
            onSave = { vm.saveSettings(it) },
            onDone = { dest = Dest.START },
        )
        Dest.RIDE -> {
            com.two17industries.rideman.ui.ride.RideScreen(
                state = ui,
                settings = settings,
                onEndRide = { lastSummary = vm.endRide(); dest = Dest.END },
            )
        }
        Dest.END -> {
            EndScreen(
                summary = lastSummary ?: vm.endRide(),
                units = settings.units,
                onDone = { vm.persistLastRide(); dest = Dest.START },
            )
        }
    }
}
