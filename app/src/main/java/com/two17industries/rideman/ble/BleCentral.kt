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
import java.util.concurrent.atomic.AtomicReference

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

    /** Written from the binder thread ([stop] via the service), the main dispatcher, and the retry coroutine itself. */
    @Volatile private var retryJob: Job? = null

    /**
     * A queued GATT operation and the characteristic UUID whose completion callback ends it.
     * The tag is what lets [operationComplete] tell OUR callback from a bypassing writer's.
     */
    private class QueuedOp(val targetUuid: UUID, val run: (BluetoothGatt) -> Unit)

    private val opQueue = ConcurrentLinkedQueue<QueuedOp>()

    /**
     * The in-flight slot AND its tag, fused into one atomic so the two can never skew.
     *
     * A previous shape kept an AtomicBoolean flag beside a @Volatile UUID and released them
     * separately; a completing thread could free the flag, let another thread acquire and tag
     * the next op, and only then null the UUID — clobbering the new tag and stalling the queue
     * forever. One cell, one CAS, no window.
     *
     * INVARIANT: null means idle. [RESERVED] means "acquired by a drain() that has not yet
     * decided which op it is running" — no real characteristic UUID, so no callback can match
     * it and steal the release. Any other value is the targetUuid of the op actually issued,
     * and ONLY a callback carrying that same UUID may release it (back to null).
     *
     * Ownership rule: a thread may write this field non-atomically (plain set) only while it
     * holds the slot, i.e. between a successful acquiring CAS and its release.
     */
    private val inFlight = AtomicReference<UUID?>(null)

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
        inFlight.set(null)
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
                // drain(), which tagged the slot with this characteristic's UUID before
                // calling us, so the release CAS matches and the queue keeps moving.
                operationComplete(characteristic.uuid)
            } else {
                // API 33+ overload: value is passed rather than staged on the descriptor.
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            }
        }
    }

    private fun drain() {
        val g = gatt ?: return
        // Acquire the slot as RESERVED. Losing this CAS means someone else owns the slot; they
        // are responsible for draining what we just enqueued when they release (see below).
        if (!inFlight.compareAndSet(null, RESERVED)) return
        val op = opQueue.poll()
        if (op == null) {
            // We own the slot, so a plain set is safe: no one else can be writing this field.
            inFlight.set(null)
            // LOST-WAKEUP GUARD — do not "simplify" this away. Without the re-check:
            // thread A wins the CAS and polls null; thread B enqueues and calls drain(), whose
            // CAS fails because A still holds the slot, so B returns; A then releases. The
            // queue is now non-empty with nothing in flight and nobody left to drain it. A
            // subscription flow has no later enqueue() to rescue it, so the strap would
            // connect and stay silent for the whole ride.
            if (opQueue.isNotEmpty()) drain()
            return
        }
        // Re-tag RESERVED -> the op's UUID before issuing it. Safe as a plain set for the same
        // reason, and it must happen BEFORE op.run so a callback can never beat the tag.
        inFlight.set(op.targetUuid)
        op.run(g)
    }

    /**
     * Advance the queue, but ONLY if [uuid] is the operation we actually issued. Consumers may
     * also write outside the queue (the dash writes telemetry and time-sync directly, by
     * design); those writes raise onCharacteristicWrite too, and advancing on one would consume
     * the in-flight slot of a queued CCCD write and leave the strap connected but silent.
     * Matching on the tag set in [drain] makes that impossible, rather than merely unlikely.
     *
     * Release is a single CAS from our own tag to null: it both proves we were the in-flight
     * op and frees the slot in one step, so nothing we do afterwards can touch a slot that has
     * since been re-acquired by another thread.
     */
    private fun operationComplete(uuid: UUID?) {
        if (uuid == null) return
        if (!inFlight.compareAndSet(uuid, null)) return
        // Same lost-wakeup guard, from the other side: an enqueue that raced our release saw a
        // busy slot and returned, trusting us to pick its op up here.
        if (opQueue.isNotEmpty()) drain()
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
            // getBluetoothLeScanner() returns null whenever the adapter is not in STATE_ON, so
            // reaching here means the adapter went down between the isEnabled check above and
            // this getter — a teardown race, not a permanent condition. BLUETOOTH_OFF is
            // therefore accurate (the stale part is the check, not the status). Re-arm the
            // chain ourselves: if the adapter never fully reached STATE_OFF there is no
            // STATE_ON broadcast coming, and without this the scan would stall for the ride.
            scanning = false
            status.set(BleConnectionState.BLUETOOTH_OFF)
            scheduleReconnect()
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
                    inFlight.set(null)
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

        /**
         * Sentinel for [inFlight]: the slot is held by a drain() that has not yet picked its op.
         * The all-zero UUID is not assignable to a real characteristic, so no callback can
         * match it — the slot stays exclusively ours across the poll.
         */
        private val RESERVED: UUID = UUID(0L, 0L)
    }
}
