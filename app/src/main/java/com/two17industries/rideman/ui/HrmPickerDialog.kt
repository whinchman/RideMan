package com.two17industries.rideman.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.two17industries.rideman.hrm.HrmBleContract
import com.two17industries.rideman.ui.components.HairLine
import com.two17industries.rideman.ui.components.TerminalButton
import com.two17industries.rideman.ui.theme.BorderCyanDim
import com.two17industries.rideman.ui.theme.Cyan
import com.two17industries.rideman.ui.theme.Muted
import com.two17industries.rideman.ui.theme.Surface1
import com.two17industries.rideman.ui.theme.TextPrimary

/** A strap seen during the picker's scan. */
data class FoundStrap(val name: String, val address: String, val rssi: Int)

/**
 * Lists nearby heart rate straps. Scans only while the dialog is open **and** the activity is at
 * least STARTED — this is a listing, not a connection, so it never calls connectGatt.
 *
 * The lifecycle tie matters. A DisposableEffect alone covers dismiss, selection and back, all of
 * which leave composition, but backgrounding the activity does not. A SCAN_MODE_LOW_LATENCY scan
 * left running with the screen off delivers nothing — Android suppresses background scan results
 * — while draining the radio and burning the five-starts-per-thirty-seconds quota, which then
 * comes back as SCAN_FAILED_SCANNING_TOO_FREQUENTLY the next time the rider opens this dialog.
 */
@SuppressLint("MissingPermission") // guarded by hasBlePermissions()
@Composable
fun HrmPickerDialog(
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var found by remember { mutableStateOf<List<FoundStrap>>(emptyList()) }
    var scanError by remember { mutableStateOf<String?>(null) }

    val granted = hasBlePermissions(context)

    DisposableEffect(granted, lifecycleOwner) {
        if (!granted) return@DisposableEffect onDispose { }
        val scanner = context.getSystemService(BluetoothManager::class.java)
            ?.adapter?.bluetoothLeScanner
        var scanning = false
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val strap = FoundStrap(
                    name = result.device.name ?: "Unknown strap",
                    address = result.device.address,
                    rssi = result.rssi,
                )
                found = (found.filter { it.address != strap.address } + strap)
                    .sortedByDescending { it.rssi }
            }

            // Previously not overridden, so a refused scan was discarded entirely and the dialog
            // sat on "Searching…" forever, telling the rider to check a strap that was never
            // asked for.
            override fun onScanFailed(errorCode: Int) {
                scanning = false
                scanError = scanFailureMessage(errorCode)
            }
        }

        fun startScan() {
            if (scanning) return
            scanning = true
            scanError = null
            runCatching {
                scanner?.startScan(
                    listOf(
                        ScanFilter.Builder()
                            .setServiceUuid(ParcelUuid(HrmBleContract.SERVICE_UUID))
                            .build()
                    ),
                    ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build(),
                    callback,
                )
            }.onFailure {
                // A synchronous refusal produces no callback at all, so it has to be surfaced here.
                scanning = false
                scanError = SCAN_START_REFUSED_MESSAGE
            }
        }

        fun stopScan() {
            if (!scanning) return
            scanning = false
            // Matches BleCentral.stopScan(): hasBlePermissions() is a race, not a fence — the
            // permission can be revoked between the check and the call — and an uncaught
            // SecurityException on the main looper would kill the process.
            if (hasBlePermissions(context)) runCatching { scanner?.stopScan(callback) }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> startScan()
                Lifecycle.Event.ON_STOP -> stopScan()
                else -> Unit
            }
        }
        // addObserver replays up to the current state, so an already-STARTED owner scans at once.
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopScan()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = {
            Text(
                "CHOOSE A STRAP",
                color = Cyan,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
            )
        },
        text = {
            Column {
                if (!granted) {
                    Text(
                        "Bluetooth permission is needed to search for straps.",
                        color = Muted,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                    )
                } else {
                    Text(
                        "Any strap",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(null) }
                            .padding(vertical = 12.dp),
                    )
                    HairLine(color = BorderCyanDim)
                    val error = scanError
                    if (error != null) {
                        Text(
                            error,
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp),
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    } else if (found.isEmpty()) {
                        Text(
                            "Searching… the strap must be worn and damp to transmit.",
                            color = Muted,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp),
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                    found.forEach { strap ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(strap.address) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                strap.name,
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                            )
                            Text(
                                strap.address,
                                color = Muted,
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TerminalButton(text = "CANCEL", onClick = onDismiss, fontSize = 12.sp)
        },
    )
}

/** Both BLE permissions, checked together — a scan needs SCAN and reading a name needs CONNECT. */
internal fun hasBlePermissions(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context, Manifest.permission.BLUETOOTH_SCAN,
    ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
