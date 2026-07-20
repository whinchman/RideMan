package com.two17industries.rideman.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/** Callbacks from [BleCentral] into a feature-specific client. */
interface BleCentralListener {
    /**
     * Services have been discovered. Grab characteristics and start any subscriptions here.
     * Return false if the required characteristics are missing — the central will disconnect.
     */
    fun onReady(gatt: BluetoothGatt): Boolean

    fun onDisconnected()

    /** A subscribed characteristic produced a notification. */
    fun onCharacteristicChanged(uuid: UUID, value: ByteArray) {}
}

/**
 * Shared BLE central: scan by service UUID, connect, discover, reconnect with backoff, and
 * serialise GATT operations. Feature-specific behaviour lives in the [BleCentralListener].
 *
 * Android permits exactly one outstanding GATT operation, so every descriptor write goes
 * through [enqueue] rather than being issued directly.
 *
 * All calls are no-ops (never crashes) when BLE permissions are missing or Bluetooth is off.
 */
@SuppressLint("MissingPermission") // guarded by hasBlePermissions()
class BleCentral(
    context: Context,
    private val serviceUuid: UUID,
    private val status: BleStatus,
    private val scope: CoroutineScope,
    private val listener: BleCentralListener,
) {
    private val appContext = context.applicationContext
    private val manager by lazy { appContext.getSystemService(BluetoothManager::class.java) }
    private val scanner: BluetoothLeScanner? get() = manager?.adapter?.bluetoothLeScanner

    /** When set, only connect to this MAC (a remembered device). Null connects to the first match. */
    @Volatile var targetAddress: String? = null

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var scanning = false
    @Volatile private var wantRunning = false
    @Volatile private var attempt = 0
    private var retryJob: Job? = null

    private val opQueue = ConcurrentLinkedQueue<(BluetoothGatt) -> Unit>()
    private val opInFlight = AtomicBoolean(false)

    fun gatt(): BluetoothGatt? = gatt

    fun start() {
        // wantRunning is set BEFORE the guards on purpose. The old DashBleClient returned
        // early here without setting it and registered no adapter-state receiver, so if
        // Bluetooth was off at ride start, turning it on mid-ride never began a scan and the
        // dash stayed dead for the whole ride (recorded as D5 in the dash-time-sync review).
        wantRunning = true
        attempt = 0
        registerAdapterReceiver()
        when {
            !hasBlePermissions() -> { status.set(BleConnectionState.NO_PERMISSION); return }
            manager?.adapter?.isEnabled != true -> { status.set(BleConnectionState.BLUETOOTH_OFF); return }
        }
        startScan()
    }

    fun stop() {
        wantRunning = false
        retryJob?.cancel()
        retryJob = null
        unregisterAdapterReceiver()
        stopScan()
        gatt?.close()
        gatt = null
        opQueue.clear()
        opInFlight.set(false)
        status.set(BleConnectionState.DISABLED)
    }

    private var adapterReceiver: BroadcastReceiver? = null

    /** Start scanning if the rider turns Bluetooth on after the ride has already begun. */
    private fun registerAdapterReceiver() {
        if (adapterReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_ON -> if (wantRunning && hasBlePermissions()) {
                        attempt = 0
                        startScan()
                    }
                    BluetoothAdapter.STATE_OFF -> {
                        scanning = false
                        status.set(BleConnectionState.BLUETOOTH_OFF)
                    }
                }
            }
        }
        adapterReceiver = r
        appContext.registerReceiver(r, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    }

    private fun unregisterAdapterReceiver() {
        adapterReceiver?.let { runCatching { appContext.unregisterReceiver(it) } }
        adapterReceiver = null
    }

    /**
     * Queue a GATT operation. [op] must issue exactly one operation that completes via a
     * BluetoothGattCallback; the queue advances when that callback arrives.
     */
    fun enqueue(op: (BluetoothGatt) -> Unit) {
        opQueue.add(op)
        drain()
    }

    /** Enable notifications on [characteristic], including the mandatory CCCD write. */
    fun subscribe(characteristic: BluetoothGattCharacteristic) {
        enqueue { g ->
            g.setCharacteristicNotification(characteristic, true)
            val cccd = characteristic.getDescriptor(CCCD_UUID)
            if (cccd == null) {
                operationComplete()
            } else {
                // API 33+ overload: value is passed rather than staged on the descriptor.
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            }
        }
    }

    private fun drain() {
        val g = gatt ?: return
        if (!opInFlight.compareAndSet(false, true)) return
        val op = opQueue.poll()
        if (op == null) { opInFlight.set(false); return }
        op(g)
    }

    private fun operationComplete() {
        // Only advance if a QUEUED operation was actually in flight. The dash writes telemetry
        // (and time-sync) directly, bypassing the queue by design — those writes also produce
        // onCharacteristicWrite, and advancing on them would skip a queued CCCD write and
        // leave the strap connected but silent.
        if (!opInFlight.compareAndSet(true, false)) return
        drain()
    }

    private fun startScan() {
        val s = scanner ?: return
        if (scanning) return
        scanning = true
        status.set(BleConnectionState.SCANNING)
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUuid))
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

    private fun scheduleReconnect() {
        if (!wantRunning) return
        val wait = BackoffPolicy.delayMsFor(attempt)
        if (wait == null) {
            status.set(BleConnectionState.GAVE_UP)
            return
        }
        attempt++
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(wait)
            if (wantRunning) startScan()
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val wanted = targetAddress
            if (wanted != null && !wanted.equals(result.device.address, ignoreCase = true)) return
            stopScan()
            gatt = result.device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            scheduleReconnect()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status_: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    gatt = g
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    opQueue.clear()
                    opInFlight.set(false)
                    g.close()
                    if (gatt === g) gatt = null
                    listener.onDisconnected()
                    status.set(BleConnectionState.DISCONNECTED)
                    scheduleReconnect()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status_: Int) {
            if (listener.onReady(g)) {
                attempt = 0
                status.set(BleConnectionState.CONNECTED)
                drain()
            } else {
                g.disconnect()
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status_: Int) {
            operationComplete()
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            status_: Int,
        ) {
            operationComplete()
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            listener.onCharacteristicChanged(c.uuid, value)
        }
    }

    private fun hasBlePermissions(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    companion object {
        /** Client Characteristic Configuration Descriptor — required to start notifications. */
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
