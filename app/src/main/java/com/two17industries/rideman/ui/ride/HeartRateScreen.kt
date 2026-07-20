package com.two17industries.rideman.ui.ride

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.two17industries.rideman.ble.BleConnectionState
import com.two17industries.rideman.ui.theme.LocalAccent
import com.two17industries.rideman.ui.theme.Muted
import com.two17industries.rideman.ui.theme.bigMetric

/**
 * Rider-facing text for a BLE state. Says what to do about it, not merely that something is
 * wrong — the dash status degrades to a bare "Idle" for a missing permission.
 */
fun hrmStatusLabel(state: BleConnectionState): String = when (state) {
    BleConnectionState.DISABLED -> "Heart rate off"
    BleConnectionState.NO_PERMISSION -> "Bluetooth permission needed"
    BleConnectionState.BLUETOOTH_OFF -> "Bluetooth is off"
    BleConnectionState.SCANNING -> "Looking for strap…"
    BleConnectionState.CONNECTED -> "No reading — check strap contact"
    BleConnectionState.DISCONNECTED -> "Strap disconnected"
}

/**
 * Mirrors [BigMetric]'s layout with one extra line for the zone or strap status. It cannot
 * call BigMetric directly, because that helper renders exactly three lines.
 */
@Composable
fun HeartRateScreen(bpm: Int?, zone: Int?, statusLabel: String?) {
    val accent = LocalAccent.current
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("HEART RATE", color = accent, style = MaterialTheme.typography.labelLarge)
        Text(
            bpm?.toString() ?: "--",
            color = accent,
            style = bigMetric,
            textAlign = TextAlign.Center,
        )
        Text("BPM", color = accent, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            text = statusLabel ?: when {
                zone == null -> "Set your birth year in Settings for zones"
                zone == 0 -> "Below zone 1"
                else -> "Zone $zone"
            },
            color = Muted,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}
