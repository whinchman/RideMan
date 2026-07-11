package com.two17industries.rideman.dash

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.core.content.ContextCompat

/**
 * BLE central: scans for the T-Display by service UUID, connects, and writes telemetry to the
 * Telemetry characteristic (write-without-response). Auto-reconnects on drop. All calls are
 * no-ops (not crashes) when BLE permissions are missing or Bluetooth is off.
 */
@SuppressLint("MissingPermission") // guarded by hasBlePermissions()
class DashBleClient(private val context: Context) {

    private val manager by lazy { context.getSystemService(BluetoothManager::class.java) }
    private val scanner: BluetoothLeScanner? get() = manager?.adapter?.bluetoothLeScanner

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var characteristic: BluetoothGattCharacteristic? = null
    @Volatile private var timeChar: BluetoothGattCharacteristic? = null
    @Volatile private var scanning = false
    @Volatile private var wantRunning = false

    fun start() {
        if (!hasBlePermissions() || manager?.adapter?.isEnabled != true) {
            DashStatus.set(DashConnectionState.DISABLED)
            return
        }
        wantRunning = true
        startScan()
    }

    fun stop() {
        wantRunning = false
        stopScan()
        gatt?.close()
        gatt = null
        characteristic = null
        timeChar = null
        DashStatus.set(DashConnectionState.DISABLED)
    }

    fun write(bytes: ByteArray) {
        val g = gatt ?: return
        val c = characteristic ?: return
        if (!hasBlePermissions()) return
        g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
    }

    /**
     * Writes the time-sync packet. No-ops if the peer has no time-sync characteristic —
     * i.e. a board on older firmware. That is not an error: the dash keeps working, it
     * just has no clock. MUST only be called from the broadcaster's 1 Hz ticker, which
     * is the single caller of the GATT write path (see writeTime/write racing note).
     */
    fun writeTime(bytes: ByteArray) {
        val g = gatt ?: return
        val c = timeChar ?: return
        if (!hasBlePermissions()) return
        g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
    }

    private fun startScan() {
        val s = scanner ?: return
        if (scanning) return
        scanning = true
        DashStatus.set(DashConnectionState.SCANNING)
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(DashBleContract.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        s.startScan(listOf(filter), settings, scanCallback)
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        if (hasBlePermissions()) scanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            stopScan()
            gatt = result.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    gatt = g
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    characteristic = null
                    timeChar = null
                    g.close()
                    if (gatt === g) gatt = null
                    DashStatus.set(DashConnectionState.DISCONNECTED)
                    if (wantRunning) startScan() // auto-reconnect
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val svc = g.getService(DashBleContract.SERVICE_UUID)
            val ch = svc?.getCharacteristic(DashBleContract.TELEMETRY_UUID)
            // Absent on older firmware — not an error, we just never get a clock.
            timeChar = svc?.getCharacteristic(DashBleContract.TIME_SYNC_UUID)
            if (ch != null) {
                characteristic = ch
                DashStatus.set(DashConnectionState.CONNECTED)
            } else {
                g.disconnect()
            }
        }
    }

    private fun hasBlePermissions(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}
