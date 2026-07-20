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
@SuppressLint("MissingPermission") // guarded by hasPermissions()
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

    /**
     * A queued GATT operation and the characteristic UUID whose completion callback ends it.
     * The tag is what lets [operationComplete] tell OUR callback from a bypassing writer's.
     */
    private class QueuedOp(val targetUuid: UUID, val run: (BluetoothGatt) -> Unit)

    private val opQueue = ConcurrentLinkedQueue<QueuedOp>()
    private val opInFlight = AtomicBoolean(false)

    /** UUID of the operation currently in flight, or null when the queue is idle. */
    @Volatile private var inFlightUuid: UUID? = null

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
            !hasPermissions() -> { status.set(BleConnectionState.NO_PERMISSION); return }
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
        inFlightUuid = null
        opInFlight.set(false)
        status.set(BleConnectionState.DISABLED)
    }

    @Volatile private var adapterReceiver: BroadcastReceiver? = null

    /** Start scanning if the rider turns Bluetooth on after the ride has already begun. */
    private fun registerAdapterReceiver() {
        if (adapterReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_ON -> if (wantRunning && hasPermissions()) {
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
     * Queue a GATT operation against [targetUuid]. [op] must issue exactly one operation on the
     * characteristic with that UUID (or its CCCD) which completes via onCharacteristicWrite /
     * onDescriptorWrite; the queue advances only when a callback carrying [targetUuid] arrives.
     */
    fun enqueue(targetUuid: UUID, op: (BluetoothGatt) -> Unit) {
        opQueue.add(QueuedOp(targetUuid, op))
        drain()
    }

    /** Enable notifications on [characteristic], including the mandatory CCCD write. */
    fun subscribe(characteristic: BluetoothGattCharacteristic) {
        enqueue(characteristic.uuid) { g ->
            g.setCharacteristicNotification(characteristic, true)
            val cccd = characteristic.getDescriptor(CCCD_UUID)
            if (cccd == null) {
                // No CCCD: no callback is coming, so complete synchronously. We are inside
                // drain(), so inFlightUuid is this characteristic and the tag check passes.
                operationComplete(characteristic.uuid)
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
        if (op == null) {
            opInFlight.set(false)
            // LOST-WAKEUP GUARD — do not "simplify" this away. Without the re-check:
            // thread A (in operationComplete) wins the CAS and polls null; thread B enqueues
            // and calls drain(), whose CAS fails because A still holds the flag, so B returns;
            // A then clears the flag. The queue is now non-empty with nothing in flight and
            // nobody left to drain it. A subscription flow has no later enqueue() to rescue
            // it, so the strap would connect and stay silent for the whole ride.
            if (opQueue.isNotEmpty()) drain()
            return
        }
        inFlightUuid = op.targetUuid
        op.run(g)
    }

    /**
     * Advance the queue, but ONLY if [uuid] is the operation we actually issued. Consumers may
     * also write outside the queue (the dash writes telemetry and time-sync directly, by
     * design); those writes raise onCharacteristicWrite too, and advancing on one would consume
     * the in-flight slot of a queued CCCD write and leave the strap connected but silent.
     * Matching on the tag set in [drain] makes that impossible, rather than merely unlikely.
     */
    private fun operationComplete(uuid: UUID?) {
        if (uuid == null || uuid != inFlightUuid) return
        if (!opInFlight.compareAndSet(true, false)) return
        inFlightUuid = null
        drain()
    }

    private fun startScan() {
        // These guards are re-run on EVERY entry, not just from start(): the retry coroutine
        // resumes minutes after its guards were last checked, and the rider may have revoked
        // permission or turned Bluetooth off in between. Publishing the status here keeps
        // Settings honest instead of leaving it stuck on "Searching…".
        if (!hasPermissions()) {
            scanning = false
            status.set(BleConnectionState.NO_PERMISSION)
            return
        }
        if (manager?.adapter?.isEnabled != true) {
            scanning = false
            status.set(BleConnectionState.BLUETOOTH_OFF)
            return
        }
        val s = scanner ?: run {
            scanning = false
            status.set(BleConnectionState.BLUETOOTH_OFF)
            return
        }
        if (scanning) return
        scanning = true
        status.set(BleConnectionState.SCANNING)
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUuid))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        // The guards above are a race, not a fence: the adapter can go down between the check
        // and this call (IllegalStateException), and permission can be revoked (SecurityException).
        // We run on a SupervisorJob scope with no exception handler, so an escaping throw from
        // the retry coroutine would reach the default handler and kill the process mid-ride.
        runCatching { s.startScan(listOf(filter), settings, scanCallback) }
            .onFailure {
                scanning = false
                status.set(BleConnectionState.BLUETOOTH_OFF)
                scheduleReconnect()
            }
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        if (hasPermissions()) scanner?.stopScan(scanCallback)
    }

    /** Retries forever; [BackoffPolicy] pins the wait at 30s rather than ever giving up. */
    private fun scheduleReconnect() {
        if (!wantRunning) return
        val wait = BackoffPolicy.delayMsFor(attempt)
        attempt++
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(wait)
            // Cleared before startScan so that a failure path calling scheduleReconnect()
            // does not cancel the coroutine it is currently running in.
            retryJob = null
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
                    inFlightUuid = null
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
            // Tag by the OWNING characteristic: every CCCD shares the same descriptor UUID,
            // so the descriptor's own UUID would not identify which subscription completed.
            operationComplete(d.characteristic?.uuid)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            status_: Int,
        ) {
            operationComplete(c.uuid)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            listener.onCharacteristicChanged(c.uuid, value)
        }
    }

    /**
     * True when both runtime BLE permissions are held. Public so consumers that touch [gatt]
     * directly (the dash's bypassing telemetry writes) can guard themselves with the same
     * check instead of duplicating the ContextCompat logic.
     */
    fun hasPermissions(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    companion object {
        /** Client Characteristic Configuration Descriptor — required to start notifications. */
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
