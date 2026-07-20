package com.two17industries.rideman.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
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
 * Lists nearby heart rate straps. Scans only while the dialog is open — this is a listing,
 * not a connection, so it never calls connectGatt.
 */
@SuppressLint("MissingPermission") // guarded by the permission check below
@Composable
fun HrmPickerDialog(
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var found by remember { mutableStateOf<List<FoundStrap>>(emptyList()) }

    val granted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.BLUETOOTH_SCAN,
    ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED

    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose { }
        val scanner = context.getSystemService(BluetoothManager::class.java)
            ?.adapter?.bluetoothLeScanner
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
        }
        scanner?.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(HrmBleContract.SERVICE_UUID)).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            callback,
        )
        onDispose { runCatching { scanner?.stopScan(callback) } }
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
                    if (found.isEmpty()) {
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
