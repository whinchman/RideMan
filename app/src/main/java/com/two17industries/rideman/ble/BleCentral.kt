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
import android.bluetooth.BluetoothStatusCodes
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
 * Shared BLE central: scan by service UUID, connect, discover, and reconnect with backoff.
 * Feature-specific behaviour lives in the [BleCentralListener].
 *
 * GATT operations are issued directly. Android permits one outstanding operation per
 * connection, and each consumer of BleCentral owns its own connection — see [subscribe] for
 * why the operation queue that used to live here was removed.
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
     * Cancellable delayed reset of [attempt], armed on every successful [onReady]. Also written
     * from the binder thread ([stop]) and the main dispatcher (connection-state callback), so
     * it needs the same [Volatile] treatment as [retryJob].
     */
    @Volatile private var readyResetJob: Job? = null

    /**
     * Cancellable watchdog for the FIRST notification after a [subscribe], armed by [subscribe]
     * itself and cancelled by the first [onCharacteristicChanged]. Written from the main
     * dispatcher (subscribe/callbacks), the binder thread ([stop]), and the coroutine itself,
     * so it needs the same [Volatile] treatment as [retryJob] and [readyResetJob].
     *
     * Deliberately first-notification ONLY, never an ongoing liveness check. Some straps stay
     * connected and simply go quiet when the rider takes them off mid-ride; an ongoing
     * watchdog would tear that link down every 10s and produce a reconnect storm at exactly
     * the moment the rider is fiddling with the strap.
     *
     * Armed in [subscribe] rather than in the onReady path so it can never fire for a
     * write-only consumer: DashBleClient never calls [subscribe] (its onReady only assigns
     * characteristics and returns true), so no watchdog is ever armed on the dash connection.
     */
    @Volatile private var firstNotifyJob: Job? = null

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
        readyResetJob?.cancel()
        readyResetJob = null
        firstNotifyJob?.cancel()
        firstNotifyJob = null
        unregisterAdapterReceiver()
        stopScan()
        gatt?.close()
        gatt = null
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
     * Enable notifications on [characteristic], including the mandatory CCCD write.
     *
     * Issued directly, with no queue. Android allows one outstanding GATT operation per
     * connection, and each consumer of BleCentral owns its own connection and issues at most
     * one operation — a subscribe on connect. A queue was tried here and removed: it was
     * serialising a queue that never held more than one item, and its concurrency produced
     * three separate permanent-stall bugs.
     *
     * If a consumer ever needs two operations on ONE connection (e.g. reading the strap's
     * Battery Level 0x180F as well as subscribing), serialisation becomes genuinely necessary
     * and must be reintroduced deliberately — with a real consumer to test it against.
     *
     * Returns true when the CCCD write was actually issued, false when it could not be. This
     * distinction matters because every failure path below returns before any write is queued,
     * so [onDescriptorWrite]'s disconnect-on-failure recovery never runs for them — without an
     * explicit signal here, [onReady] would return true, [BleCentral] would publish CONNECTED,
     * and the strap would sit there silently forever: connected but dead, indistinguishable
     * from broken hardware. Callers must treat false the same as a failed connection.
     *
     * [gatt] is passed explicitly rather than read from the field so the operation is provably
     * issued on the connection that delivered [BleCentralListener.onReady]. Reading the field
     * meant that a double-connect (see [onScanResult]) could apply `setCharacteristicNotification`
     * and the CCCD write to connection B using characteristic/descriptor objects owned by
     * connection A — a handle mismatch that subscribes nothing while reporting success.
     */
    fun subscribe(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Boolean {
        if (!hasPermissions()) {
            // Silence here is worse than for other guards: the rider can act on NO_PERMISSION,
            // but a bare disconnect (or nothing at all) tells them nothing is wrong.
            //
            // This NO_PERMISSION is promptly overwritten by DISCONNECTED, because returning
            // false makes the caller refuse the connection and onServicesDiscovered disconnects.
            // The status is not lost: that disconnect calls scheduleReconnect(), and startScan()
            // re-runs the permission check after the backoff delay and republishes NO_PERMISSION.
            // So the UI converges on NO_PERMISSION within one backoff interval for as long as
            // the permission is actually missing.
            status.set(BleConnectionState.NO_PERMISSION)
            return false
        }
        gatt.setCharacteristicNotification(characteristic, true)
        // A characteristic with no CCCD cannot be subscribed to over the air. The peer that
        // advertises NOTIFY without a CCCD is simply out of spec and can never notify, so
        // returning false is enough — the caller refuses the connection and BleCentral's
        // existing reconnect path takes over.
        val cccd = characteristic.getDescriptor(CCCD_UUID) ?: return false
        // API 33+ overload: value is passed rather than staged on the descriptor, and the
        // result is an Int BluetoothStatusCodes — NOT void. It fails synchronously and
        // routinely (ERROR_GATT_WRITE_NOT_ALLOWED, ERROR_GATT_WRITE_REQUEST_BUSY,
        // ERROR_DEVICE_NOT_CONNECTED, ERROR_PROFILE_SERVICE_NOT_BOUND). When it does, nothing
        // is queued, so onDescriptorWrite never fires and its disconnect-on-failure recovery
        // never runs. Discarding this value is exactly how "connected but silent" happens.
        val issued = gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
            BluetoothStatusCodes.SUCCESS
        if (issued) armFirstNotifyWatchdog(gatt)
        return issued
    }

    /**
     * A successfully issued CCCD write still has two routes to a silent connection: the write
     * is accepted but [onDescriptorWrite] never arrives, and the write completes with
     * GATT_SUCCESS but the peer simply never notifies (a stale service cache will do this).
     * Neither produces any callback to react to, so only a timeout can catch them.
     *
     * Disconnecting routes both into recovery the class already has: onConnectionStateChange
     * publishes DISCONNECTED and calls scheduleReconnect().
     */
    private fun armFirstNotifyWatchdog(g: BluetoothGatt) {
        firstNotifyJob?.cancel()
        firstNotifyJob = scope.launch {
            delay(FIRST_NOTIFY_TIMEOUT_MS)
            firstNotifyJob = null
            if (hasPermissions()) runCatching { g.disconnect() }
        }
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
            // ScanSettings leaves setCallbackType at its CALLBACK_TYPE_ALL_MATCHES default, so
            // the same device is reported on every advertisement. stopScan() unregisters in the
            // stack, but results already posted to the main looper still arrive, and stopScan()'s
            // own `if (!scanning) return` lets the second one fall straight through to
            // connectGatt. That overwrote the first BluetoothGatt without closing it (a leaked
            // connection) and left two live links to the same peer.
            if (gatt != null) return
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
                    // A non-success status here is a failed connection wearing CONNECTED's
                    // clothes: the link is not usable, and discovery on it either never
                    // completes or returns a stale cache. Disconnect so the DISCONNECTED branch
                    // re-arms the backoff.
                    if (status_ != BluetoothGatt.GATT_SUCCESS) {
                        g.disconnect()
                        return
                    }
                    // discoverServices() returns false when the stack is busy or the link is
                    // tearing down, and then onServicesDiscovered NEVER fires. Discarding it
                    // stalled the ride permanently: wantRunning true, scanning false, retryJob
                    // null, gatt non-null, status stuck on SCANNING — and because the link
                    // stays up, no DISCONNECTED callback ever comes to re-arm anything.
                    if (!g.discoverServices()) g.disconnect()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    g.close()
                    if (gatt === g) gatt = null
                    readyResetJob?.cancel()
                    readyResetJob = null
                    firstNotifyJob?.cancel()
                    firstNotifyJob = null
                    listener.onDisconnected()
                    status.set(BleConnectionState.DISCONNECTED)
                    scheduleReconnect()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status_: Int) {
            // Checked BEFORE consulting the listener. On a non-success status Android can hand
            // back a stale service cache, whose characteristic and descriptor handles no longer
            // match the peer — so onReady would find its characteristic, the CCCD write would
            // complete with GATT_SUCCESS against the wrong handle, and nothing would ever
            // notify. Another route to connected-but-silent, so refuse the connection outright.
            if (status_ != BluetoothGatt.GATT_SUCCESS) {
                g.disconnect()
                return
            }
            if (listener.onReady(g)) {
                // attempt is deliberately NOT reset here. onReady is where a listener issues
                // its subscribe(), and the CCCD write it triggers fails afterwards, routed
                // through onDescriptorWrite -> g.disconnect() -> scheduleReconnect(). A peer
                // that permanently rejects the CCCD write would otherwise reset attempt to 0 on
                // every cycle, pinning the backoff at its 1s floor for the whole ride instead
                // of climbing to the 30s ceiling the design intends. Reset only once the
                // connection has proven durable, not merely established.
                readyResetJob?.cancel()
                readyResetJob = scope.launch {
                    delay(READY_STABLE_MS)
                    attempt = 0
                }
                status.set(BleConnectionState.CONNECTED)
            } else {
                g.disconnect()
            }
        }

        /**
         * The only thing left worth doing here is noticing FAILURE. A CCCD write that fails
         * leaves the subscription off while the link stays up, so the UI would sit on
         * CONNECTED and no notification would ever arrive — indistinguishable from a dead
         * strap, and previously swallowed entirely.
         *
         * Disconnecting turns that silent state into one the rest of the class already
         * handles: onConnectionStateChange publishes DISCONNECTED (which the UI renders) and
         * calls scheduleReconnect(), so a transient failure is simply retried with backoff
         * rather than costing the rider the whole ride.
         */
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status_: Int) {
            if (d.uuid == CCCD_UUID && status_ != BluetoothGatt.GATT_SUCCESS) g.disconnect()
        }

        // No onCharacteristicWrite: it existed only to advance the removed queue. The dash's
        // bypassing telemetry/time-sync writes are WRITE_TYPE_NO_RESPONSE fire-and-forget and
        // depend on nothing in that callback.

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            // First notification proves the subscription is live. Cancel and clear, so the
            // watchdog is never re-armed for the life of this connection — see firstNotifyJob
            // for why an ongoing liveness check would be actively harmful.
            firstNotifyJob?.cancel()
            firstNotifyJob = null
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
         * How long a connection must stay up after [onReady] before [attempt] is reset to 0.
         * Chosen to comfortably exceed any CCCD write's round trip (well under a second), so a
         * peer that permanently rejects the write never resets the counter, while a genuinely
         * healthy connection resets it well before the rider would notice.
         */
        private const val READY_STABLE_MS = 20_000L

        /**
         * How long after a successfully issued CCCD write the first notification may take
         * before the connection is treated as silent and torn down. Generous next to a
         * standard HR strap's 1 Hz measurement rate, so a healthy strap never trips it even
         * across a slow connection interval negotiation.
         */
        private const val FIRST_NOTIFY_TIMEOUT_MS = 10_000L
    }
}
