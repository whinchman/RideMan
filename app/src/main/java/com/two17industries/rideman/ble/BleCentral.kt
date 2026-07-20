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

    /**
     * The last connection this class saw a STATE_DISCONNECTED for while [gatt] did NOT yet refer
     * to it. Exists solely to unpin [gatt] after the following race.
     *
     * [onScanResult] does `gatt = result.device.connectGatt(...)`. The callback arrives on a
     * binder thread and can land between `connectGatt` returning and that store landing — on a
     * fast failure (immediate status 133, adapter downed under us) that is the common case, not
     * an exotic one. The DISCONNECTED branch then reads `gatt` as null, skips the per-connection
     * cleanup, and the main thread afterwards completes the assignment, pinning [gatt] to a
     * connection that has already been closed. [onScanResult]'s `if (gatt != null) return` guard
     * then blocks re-entry for the rest of the ride.
     *
     * WRITE ORDER IS LOAD-BEARING. The callback writes this field BEFORE reading [gatt];
     * [onScanResult] writes [gatt] before reading this field. Both are [Volatile], so the two
     * accesses on each side are ordered against each other, and the opposed order makes it
     * impossible for both sides to miss: if the callback's read of [gatt] preceded
     * [onScanResult]'s write, then the callback's write of this field preceded that too, so
     * [onScanResult]'s later read must observe it. Reversing either pair reopens an interleaving
     * in which neither side notices.
     *
     * A stale value is harmless: `connectGatt` always returns a freshly allocated object, so a
     * new connection can never be reference-equal to one already recorded here.
     */
    @Volatile private var closedGatt: BluetoothGatt? = null

    @Volatile private var scanning = false
    @Volatile private var wantRunning = false
    @Volatile private var attempt = 0

    /**
     * Written from an arbitrary caller thread ([stop] via the service) and from the retry
     * coroutine itself.
     *
     * THREADING: GATT callbacks are NOT delivered on the main dispatcher. No [Handler] overload
     * of `connectGatt` is used, so every [BluetoothGattCallback] method below arrives on a
     * binder thread from the process-wide binder pool, and that pool can dispatch two callbacks
     * concurrently. Nothing here is confined to a single thread; [Volatile] is a minimum, not a
     * proof of safety, and any state machine built on job identity alone is racy (see
     * [firstNotifyDone]).
     */
    @Volatile private var retryJob: Job? = null

    /**
     * Cancellable delayed reset of [attempt], armed on every successful [onReady]. Written from
     * a binder thread (the GATT callbacks), from [stop], and from the watchdog coroutines, so
     * it needs the same [Volatile] treatment as [retryJob] — see the threading note there.
     */
    @Volatile private var readyResetJob: Job? = null

    /**
     * Cancellable watchdog for the FIRST notification after a [subscribe], armed by [subscribe]
     * itself and cancelled by the first [onCharacteristicChanged]. Written from a binder thread
     * (the GATT callbacks and the onReady path that calls [subscribe]), from [stop], and from
     * the coroutine itself, so it needs the same [Volatile] treatment as [retryJob] and
     * [readyResetJob] — see the threading note on [retryJob].
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

    /**
     * Set the instant a notification arrives; cleared when the watchdog is armed. The watchdog
     * tail checks this AFTER its delay and stands down if it is set.
     *
     * WHY A FLAG AND NOT JUST `cancel()`: the flag makes the stand-down decision independent of
     * job identity, and that independence is the load-bearing property. If a disconnect +
     * reconnect + [subscribe] lands while an old tail is in flight, a tail that clears
     * [firstNotifyJob] unconditionally clobbers the SUCCESSOR's job reference, leaving the new
     * watchdog unreachable from [onCharacteristicChanged], [stop] and the DISCONNECTED branch —
     * it then fires at its own timeout and tears down a healthy, notifying connection. Reaching
     * for the field is exactly what goes wrong; the flag answers "has THIS stage completed?"
     * without consulting it.
     *
     * It also covers the genuinely uncancellable window. A `launch` continuation resuming from
     * `delay` IS dispatched cancellably, so kotlinx does honour a `cancel()` that lands after the
     * delay expires but before the continuation runs — an earlier version of this comment claimed
     * otherwise and was simply wrong. The window `cancel()` cannot close is the narrower one
     * after the body has already begun executing, and the flag closes that too because it is read
     * as the body's first act.
     */
    @Volatile private var firstNotifyDone = false

    /**
     * Cancellable timeout for the SERVICE DISCOVERY stage, armed immediately BEFORE
     * `discoverServices()` is called and cancelled by [onServicesDiscovered]. Same [Volatile]
     * rationale and same binder-thread reality as [firstNotifyJob] — see the threading note on
     * [retryJob]. Arming before rather than after the request is what stops a fast-path
     * callback's stand-down write being lost; see the arming site in [onConnectionStateChange].
     *
     * `discoverServices()` returning false is handled inline, but the twin failure is the one
     * that strands a ride: it returns true, the request is queued, and [onServicesDiscovered]
     * NEVER fires — real behaviour on a saturated or mid-teardown stack. Unlike
     * `connectGatt(autoConnect = false)`, which self-times-out in roughly 30s, service discovery
     * has no platform timeout behind it. The terminal state is exactly the one the
     * `discoverServices()` false-branch comment describes: wantRunning true, scanning false,
     * retryJob null, gatt non-null, status pinned on SCANNING, the link still up so no
     * DISCONNECTED ever arrives — and permanently so, because [onScanResult]'s `gatt != null`
     * guard prevents re-entry. Dead for the whole ride.
     *
     * Armed in [onConnectionStateChange], which the DASH reaches too. That is deliberate: the
     * dash gets the same stall protection. It cannot fire spuriously for a write-only consumer,
     * because it is bounded by service discovery — a stage every consumer completes identically
     * — and not by any notification. See [onServicesDiscovered], which cancels it before
     * consulting the listener at all.
     *
     * That claim was FALSE while this timer was armed after `discoverServices()` returned: the
     * dash reaches discovery on exactly the same path as the strap, so a lost stand-down write
     * on the cached-services fast path would tear the dash down 10s into a healthy connection.
     * It holds now only because the arming precedes the request. Do not re-order it.
     */
    @Volatile private var discoveryJob: Job? = null

    /** Discovery-stage twin of [firstNotifyDone]; same identity-race rationale. */
    @Volatile private var discoveryDone = false

    /**
     * Cancellable timeout for the TEARDOWN stage: `disconnect()` issued → DISCONNECTED delivered.
     * Armed by [disconnectAndArmTeardown] and stood down by the DISCONNECTED branch.
     *
     * This stage is the funnel every recovery path in the class runs through — the discovery
     * timeout, the first-notify watchdog, a failed CCCD write, a refused `onReady`, a stale
     * service cache and a non-success CONNECTED all end in `disconnect()` and then wait. Until
     * this timer existed it was the one stage with no bound at all: `disconnect()` on a stack
     * that is wedged, or on a link whose peer has vanished without the controller noticing,
     * simply never produces a STATE_DISCONNECTED callback. The terminal state is the same one
     * every other stage's timeout exists to break — [gatt] non-null, `scanning` false,
     * `retryJob` null, status frozen wherever it last was, and [onScanResult]'s `gatt != null`
     * guard refusing re-entry — i.e. connected-but-silent or scanning-forever, for the ride.
     *
     * Armed for the DASH too, and cannot fire spuriously for it: it is bounded by a callback the
     * platform owes for every connection it ever reports as connected, not by anything the peer
     * chooses to send, and it is armed ONLY after this class has itself called `disconnect()` —
     * i.e. only on a connection already being torn down. A healthy dash, which never notifies
     * and never subscribes, never reaches an arming site at all.
     */
    @Volatile private var teardownJob: Job? = null

    /**
     * Teardown-stage stand-down, and the twin of [firstNotifyDone] / [discoveryDone] — but it
     * holds the connection rather than a boolean, and that difference is required, not stylistic.
     *
     * Set to the connection when the timer is armed; cleared by the DISCONNECTED branch when the
     * callback is for that same connection. The tail stands down unless this still refers to its
     * own [BluetoothGatt], which gives it the same identity-independence the booleans give the
     * other two stages plus immunity to a late DISCONNECTED for a SUPERSEDED connection standing
     * down the live connection's timer.
     *
     * A boolean could not be stood down safely here. The other two stages stand down inside a
     * callback that can gate itself on `gatt === g`; the DISCONNECTED branch cannot, because the
     * race this whole wave exists to fix (see [closedGatt]) is precisely the one where
     * `gatt === g` is false for the connection the callback is about.
     */
    @Volatile private var teardownGatt: BluetoothGatt? = null

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
        discoveryJob?.cancel()
        discoveryJob = null
        teardownJob?.cancel()
        teardownJob = null
        // Set the stand-down markers as well as cancelling. cancel() is honoured right up to the
        // moment the tail's body begins, but not after — and these tails would otherwise call
        // disconnect() on, or publish DISCONNECTED over, a connection stop() is in the middle of
        // closing. Clearing teardownGatt is the teardown stage's equivalent of setting the two
        // booleans; see [teardownGatt].
        firstNotifyDone = true
        discoveryDone = true
        teardownGatt = null
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
                        // A live connection here had no local bound at all: `gatt` was left
                        // pinned and nothing armed, delegating teardown entirely to the platform
                        // delivering STATE_DISCONNECTED. Most stacks do — but "the callback might
                        // not come" is the premise every timer in this class was added on, and no
                        // disconnect() was issued here so the bounding helper never ran. If it
                        // does not arrive, a later STATE_ON starts a scan, a result arrives, and
                        // onScanResult's `if (gatt != null) return` refuses re-entry for the rest
                        // of the ride.
                        //
                        // The helper is already correct for an adapter-down state: it skips the
                        // disconnect() when permissions are missing and arms the timer either
                        // way, so the teardown completes locally at 5s regardless of whether the
                        // stack is in any condition to answer.
                        gatt?.let { disconnectAndArmTeardown(it) }
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
     * The connection is passed explicitly as [g] rather than read from the field so the
     * operation is provably issued on the connection that delivered [BleCentralListener.onReady].
     * Reading the field meant that a double-connect (see [onScanResult]) could apply
     * `setCharacteristicNotification` and the CCCD write to connection B using
     * characteristic/descriptor objects owned by connection A — a handle mismatch that
     * subscribes nothing while reporting success.
     *
     * The parameter is named `g`, matching every other passed connection in this file, so that
     * the field is genuinely unreachable from this body. It was previously named `gatt`, which
     * enforced the same invariant only by shadowing — an accident that would silently reverse
     * the moment anyone renamed the parameter.
     */
    fun subscribe(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Boolean {
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
        g.setCharacteristicNotification(characteristic, true)
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
        //
        // ARMED BEFORE THE WRITE, for the same reason the discovery timeout is — see the
        // comment at its arming site in onConnectionStateChange. Arming after the request lets
        // a fast-path callback's stand-down write be overwritten by the arming thread's
        // `firstNotifyDone = false`, after which the tail tears down a healthy connection. This
        // stage does self-heal (onCharacteristicChanged re-sets the flag on EVERY notification,
        // not just the first), so the inversion here is for symmetry with the stage that
        // cannot; it is not load-bearing on its own.
        armFirstNotifyWatchdog(g)
        val issued = g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
            BluetoothStatusCodes.SUCCESS
        if (!issued) {
            // Nothing was queued, so no notification is coming and the watchdog just armed has
            // no stage left to bound. Stand it down rather than let it fire 10s later at a
            // connection the caller is about to refuse anyway. Flag first, then compare before
            // nulling the shared field — the same treatment the tails get, so a successor's
            // watchdog reference cannot be clobbered.
            firstNotifyDone = true
            val w = firstNotifyJob
            w?.cancel()
            if (firstNotifyJob === w) firstNotifyJob = null
        }
        return issued
    }

    /**
     * A successfully issued CCCD write still has two routes to a silent connection: the write
     * is accepted but [onDescriptorWrite] never arrives, and the write completes with
     * GATT_SUCCESS but the peer simply never notifies (a stale service cache will do this).
     * Neither produces any callback to react to, so only a timeout can catch them.
     *
     * Disconnecting routes both into recovery the class already has: onConnectionStateChange
     * publishes DISCONNECTED and calls scheduleReconnect(). That handoff is itself bounded —
     * the disconnect goes through [disconnectAndArmTeardown], so a DISCONNECTED that never
     * arrives no longer strands the connection here. See [teardownJob].
     *
     * [g] is captured in the closure and disconnected directly; the field is never read here,
     * so this timer can only ever tear down its OWN connection, never a successor's.
     */
    private fun armFirstNotifyWatchdog(g: BluetoothGatt) {
        firstNotifyDone = false
        firstNotifyJob?.cancel()
        firstNotifyJob = scope.launch {
            delay(FIRST_NOTIFY_TIMEOUT_MS)
            // Stand down on the flag, not on job identity — see [firstNotifyDone].
            if (firstNotifyDone) return@launch
            // Cancelled HERE, not left to the DISCONNECTED branch. See FIRST_NOTIFY_TIMEOUT_MS:
            // attempt must not be reset for a connection we are tearing down as silent, and
            // relying on the DISCONNECTED callback to arrive within the remaining margin makes
            // that correctness a function of callback latency.
            //
            // Compare-before-null on the shared field, for the same reason the KDoc on
            // [firstNotifyDone] gives for not clearing [firstNotifyJob] unconditionally: nulling
            // it blind would clobber a SUCCESSOR's reset job, leaving it unreachable from [stop]
            // and the DISCONNECTED branch and free to reset [attempt] for a connection this tail
            // knows nothing about. Unreachable today only because [scope] is
            // Dispatchers.Main.immediate and every connection-creating path is main-confined —
            // an incidental property, not one this file states anywhere, and one that a move off
            // Main or a switch to the Handler overload of connectGatt would silently withdraw.
            val reset = readyResetJob
            reset?.cancel()
            if (readyResetJob === reset) readyResetJob = null
            disconnectAndArmTeardown(g)
        }
    }

    /**
     * Bounds the service-discovery stage, which has no platform timeout of its own — see
     * [discoveryJob] for the stall this exists to break.
     *
     * Structurally identical to [armFirstNotifyWatchdog], including the captured [g] (so it can
     * only tear down its own connection) and the flag-based stand-down (so a cancel racing the
     * tail's dispatch cannot let it disconnect a connection that has in fact discovered).
     */
    private fun armDiscoveryTimeout(g: BluetoothGatt) {
        discoveryDone = false
        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            delay(DISCOVERY_TIMEOUT_MS)
            if (discoveryDone) return@launch
            // No readyResetJob cancel here, deliberately. It was self-admittedly a no-op for
            // THIS connection — readyResetJob is armed in onServicesDiscovered, which by
            // definition has not run when this tail fires — so the only job it could ever have
            // cancelled belonged to somebody else. "Symmetric and costs nothing" was wrong on
            // the second half: it cost an unnecessary touch of a shared field from a tail with
            // no claim to it. Removing it halves the exposure at zero behavioural cost.
            disconnectAndArmTeardown(g)
        }
    }

    /**
     * The ONLY route to a self-initiated `disconnect()` in this class. Every recovery path —
     * non-success CONNECTED, `discoverServices()` returning false, non-success discovery, a
     * listener refusing `onReady`, a failed CCCD write, and both stage watchdogs — goes through
     * here, so no call site can issue a disconnect and forget to bound it.
     *
     * The timer is armed even when `hasPermissions()` is false and the `disconnect()` was
     * therefore never issued. That case needs the exit MORE, not less: nothing was asked of the
     * stack, so nothing is coming back, and without the timer the connection would sit pinned in
     * [gatt] forever.
     */
    private fun disconnectAndArmTeardown(g: BluetoothGatt) {
        if (hasPermissions()) runCatching { g.disconnect() }
        armTeardownTimeout(g)
    }

    /**
     * Bounds the teardown stage — see [teardownJob] for the stall this exists to break.
     *
     * Structurally identical to the other two stage timers: the connection is captured in the
     * closure and never read from the field, so this can only ever act on its OWN connection,
     * and the stand-down ([teardownGatt]) is checked as the body's first act so a tail already
     * past cancellation cannot publish DISCONNECTED over a link that has since been replaced.
     */
    private fun armTeardownTimeout(g: BluetoothGatt) {
        teardownGatt = g
        teardownJob?.cancel()
        teardownJob = scope.launch {
            delay(TEARDOWN_TIMEOUT_MS)
            if (teardownGatt !== g) return@launch
            teardownGatt = null
            // The DISCONNECTED callback is not coming. Do its job: release the client interface
            // (unconditional for the same reason it is unconditional in the callback — close()
            // releases the interface belonging to g, whichever connection g is), drop the
            // per-connection state, and hand the rider back to the reconnect loop.
            runCatching { g.close() }
            // Same single-read, same widened guard as the DISCONNECTED branch, for the same
            // reason — see the comments there. No successor can actually be live at this point
            // (every route that nulls `gatt` for this connection also stands this timer down),
            // but the invariant is stated rather than assumed.
            val current = gatt
            if (current === g) clearConnectionState()
            if (wantRunning && (current == null || current === g)) {
                listener.onDisconnected()
                status.set(BleConnectionState.DISCONNECTED)
                scheduleReconnect()
            }
        }
    }

    /**
     * Drops everything that belongs to the connection currently in [gatt]. Callers must have
     * established that the connection they are retiring IS the one in the field.
     *
     * Deliberately does NOT touch [teardownJob]. Its stand-down ([teardownGatt]) is cleared by
     * the DISCONNECTED branch before this is called, so the tail already no-ops, and one of this
     * function's two callers IS that tail — cancelling the coroutine it is running in is a trap
     * this file has been caught by once already (see the comment in [scheduleReconnect]).
     */
    private fun clearConnectionState() {
        gatt = null
        readyResetJob?.cancel()
        readyResetJob = null
        firstNotifyJob?.cancel()
        firstNotifyJob = null
        discoveryJob?.cancel()
        discoveryJob = null
        // Stand-down markers as well as cancels: cancel() stops being honoured once a tail's
        // body has begun, and this connection's timers are moot now. Re-cleared when the next
        // connection arms them.
        firstNotifyDone = true
        discoveryDone = true
    }

    private fun startScan() {
        // These guards are re-run on EVERY entry, not just from start(): the retry coroutine
        // resumes minutes after its guards were last checked, and the rider may have revoked
        // permission or turned Bluetooth off in between. Publishing the status here keeps
        // Settings honest instead of leaving it stuck on "Searching…".
        if (!hasPermissions()) {
            scanning = false
            status.set(BleConnectionState.NO_PERMISSION)
            // Re-arm the chain, matching the `scanner == null` branch below. Without this the
            // backoff chain DIES the first time it re-enters here: retryJob has already been
            // nulled by the retry coroutine, so the terminal state is wantRunning true,
            // scanning false, retryJob null and nothing left that can ever start a scan again.
            // The two neighbouring guards are both covered — BLUETOOTH_OFF by the adapter
            // receiver's STATE_ON, `scanner == null` by its own explicit reschedule — and this
            // one had neither.
            //
            // It is also what makes subscribe()'s KDoc true: the UI converges on NO_PERMISSION
            // "for as long as the permission is actually missing" only if this check keeps
            // being re-run, and until now it ran once and then stopped.
            scheduleReconnect()
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
            // Re-checked here, not inherited from startScan(): this callback is dispatched
            // minutes after the scan began and permission can have been revoked in between.
            // Without it the connectGatt below throws SecurityException out of a main-looper
            // dispatch (see the runCatching note under it).
            if (!hasPermissions()) {
                status.set(BleConnectionState.NO_PERMISSION)
                return
            }
            // connectGatt is declared BluetoothGatt! — a platform type — so Kotlin inserts no
            // null check and a null return compiles to a plain store of null. AOSP returns null
            // when getBluetoothGatt() yields no service: the adapter went down between the scan
            // result and this call, or GATT client registration failed. That is the SAME window
            // startScan's own runCatching exists for ("the guards above are a race, not a
            // fence").
            //
            // This is the only place a null return can be caught, and the reason is structural:
            // a synchronous refusal to START the stage produces no BluetoothGatt, therefore no
            // callback, therefore nothing for any stage timer to bound. Every other stall in
            // this class is "the callback never came" and is caught by a timeout; this one is
            // "the call never began" and can only be caught at the call itself.
            //
            // Left unhandled the ride is over: stopScan() has already run, `gatt` stays null so
            // no cleanup path is keyed to anything, retryJob was nulled by the retry coroutine
            // before it called startScan, and nothing schedules another attempt — wantRunning
            // true, scanning false, retryJob null, status frozen on SCANNING for the whole ride.
            // The closedGatt unpin block below does not rescue it either: with both null,
            // `closedGatt === g` takes the branch and `gatt = null` is a no-op.
            //
            // runCatching for the throwing variant: ScanCallback is delivered on
            // BluetoothLeScanner's main-looper Handler, so a SecurityException from a permission
            // revoked between the check above and this line would propagate out of a main-looper
            // dispatch and kill the process mid-ride. Every other framework call in this class
            // that can throw for that reason is wrapped; this one was not.
            val g = runCatching {
                result.device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            }.getOrNull()
            if (g == null) {
                scheduleReconnect()
                return
            }
            gatt = g
            // Re-check IMMEDIATELY after the store, and in this order. A STATE_DISCONNECTED for
            // g can be delivered on a binder thread before the line above lands — routine on an
            // immediate status-133 failure — in which case the callback saw `gatt` as null,
            // skipped its per-connection cleanup, and the store above has just pinned the field
            // to a connection that is already closed and can never disconnect again. Recovery
            // itself is not lost (the callback's wantRunning branch already published
            // DISCONNECTED and scheduled the retry); what is lost without this is the `gatt`
            // field, and with it the `gatt != null` guard above, permanently. See [closedGatt]
            // for why this ordering is what makes the two sides unable to both miss.
            if (closedGatt === g) {
                closedGatt = null
                if (gatt === g) gatt = null
            }
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
                        disconnectAndArmTeardown(g)
                        return
                    }
                    // discoverServices() returns false when the stack is busy or the link is
                    // tearing down, and then onServicesDiscovered NEVER fires. Discarding it
                    // stalled the ride permanently: wantRunning true, scanning false, retryJob
                    // null, gatt non-null, status stuck on SCANNING — and because the link
                    // stays up, no DISCONNECTED callback ever comes to re-arm anything.
                    // The twin failure — returning true and then never calling
                    // onServicesDiscovered — has no platform timeout behind it, so it gets an
                    // explicit one. See discoveryJob.
                    //
                    // ARMED BEFORE THE REQUEST, and the order is load-bearing. Arming's first
                    // act is `discoveryDone = false`. Callbacks run inline on binder threads and
                    // that pool dispatches concurrently, so on a cached-services fast path
                    // onServicesDiscovered can complete — setting discoveryDone = true,
                    // cancelling a still-null job, calling onReady, publishing CONNECTED —
                    // before the arming thread executes its `discoveryDone = false`. The
                    // stand-down write is then LOST, and 10s later the tail reads false and
                    // tears down a healthy, fully discovered connection.
                    //
                    // The notify watchdog has the same shape but self-heals, because
                    // onCharacteristicChanged re-sets its flag on every notification.
                    // onServicesDiscovered fires exactly once per connection — there is no
                    // second chance, so the ordering has to carry it.
                    //
                    // Arming first is safe on the false branch: disconnectAndArmTeardown leads
                    // to DISCONNECTED cleanup, which sets discoveryDone = true.
                    armDiscoveryTimeout(g)
                    if (!g.discoverServices()) disconnectAndArmTeardown(g)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    // close() stays unconditional: it releases THIS callback's own client
                    // interface, which is correct to do whichever connection g is.
                    g.close()
                    // Written BEFORE the `gatt === g` read below. That order is the entire proof
                    // that the connectGatt/callback race cannot pin the field — see [closedGatt].
                    closedGatt = g
                    // The teardown stage has reached its terminal state, so its timer stands
                    // down. Keyed on the connection, not a bare boolean, because this branch
                    // cannot gate itself on `gatt === g` — the race above is precisely the case
                    // where that is false for the connection this callback is about.
                    if (teardownGatt === g) teardownGatt = null
                    // ONE read of the field, shared by both decisions below. Two reads could
                    // observe different values (onScanResult's store can land between them) and
                    // the two decisions would then disagree about which connection is live.
                    val current = gatt
                    // State that belongs to a SPECIFIC connection is gated on identity: a
                    // DISCONNECTED for a superseded connection must not cancel the live
                    // connection's timers or null its gatt.
                    if (current === g) clearConnectionState()
                    // Recovery is NOT gated on `current === g`, and putting it inside that gate
                    // was a regression. Two entrances reach a DISCONNECTED where it is false: the
                    // race in [closedGatt], and any connection torn down before its assignment
                    // landed. In both, the gate skipped onDisconnected(), the status publish and
                    // — fatally — scheduleReconnect(), leaving wantRunning true with nothing left
                    // that could ever start a scan again. The gate's real prize was narrower than
                    // its comment claimed: it stops a late DISCONNECTED overwriting stop()'s
                    // DISABLED. wantRunning buys exactly that.
                    //
                    // `current == null` widens the gate to cover the race and nothing else. It is
                    // the state during the connectGatt window (the store has not landed) and
                    // after a teardown-timeout cleanup, and in both there is no live connection
                    // for this recovery to trample. The remaining case — `current` non-null and
                    // not g — is a genuine live successor that owns its own recovery, and
                    // publishing DISCONNECTED over it is the connected-but-shown-as-dead signature
                    // this class keeps producing. [armTeardownTimeout] made that case reachable
                    // for the first time (it can null `gatt` and let a successor connect while a
                    // late DISCONNECTED for the old connection is still in flight), so the guard
                    // is load-bearing now rather than defensive.
                    //
                    // Recovery running twice for one connection (teardown timeout, then its
                    // DISCONNECTED arriving late after all) is accepted: it costs one redundant
                    // scheduleReconnect and a DISCONNECTED briefly shown while scanning, and
                    // startScan republishes SCANNING within the backoff interval. A missed
                    // recovery costs the ride; a duplicated one costs a second.
                    if (wantRunning && (current == null || current === g)) {
                        listener.onDisconnected()
                        status.set(BleConnectionState.DISCONNECTED)
                        scheduleReconnect()
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status_: Int) {
            // ABOVE the flag write, deliberately. A late discovery callback for connection A,
            // landing after B has armed its own discovery timeout, would otherwise set B's
            // stand-down flag — B's watchdog then declines to fire and B is left with no exit
            // from the discovery stage at all, reopening the stall this timer exists to close.
            // Worse here than in onCharacteristicChanged: without this guard the body runs on
            // to hand dead connection A to listener.onReady(), subscribe against A's stale
            // characteristic handles, and publish CONNECTED for a link that is already gone.
            if (gatt !== g) return
            // Discovery reached its terminal state, so the stage timeout has done its job —
            // stood down FIRST, before any branch below can return or disconnect. Flag before
            // cancel, because cancel() alone cannot stop a tail that is already past its delay.
            //
            // This runs for EVERY consumer, including the write-only dash: discovery completes
            // for the dash exactly as it does for the strap, so the dash's timer is always
            // cancelled here and can never fire spuriously for a peer that never notifies.
            discoveryDone = true
            discoveryJob?.cancel()
            discoveryJob = null
            // Checked BEFORE consulting the listener. On a non-success status Android can hand
            // back a stale service cache, whose characteristic and descriptor handles no longer
            // match the peer — so onReady would find its characteristic, the CCCD write would
            // complete with GATT_SUCCESS against the wrong handle, and nothing would ever
            // notify. Another route to connected-but-silent, so refuse the connection outright.
            if (status_ != BluetoothGatt.GATT_SUCCESS) {
                disconnectAndArmTeardown(g)
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
                    // Compare-before-act, the same treatment the two watchdog tails get. This
                    // tail had neither an identity check nor a stand-down flag, so one left over
                    // from a connection that has since been torn down would reset attempt for
                    // whatever connection happens to be live — pinning the backoff at its 1s
                    // floor across exactly the repeated-failure sequence the counter exists to
                    // climb. Capturing g makes the decision belong to this connection.
                    if (gatt !== g) return@launch
                    attempt = 0
                }
                status.set(BleConnectionState.CONNECTED)
            } else {
                disconnectAndArmTeardown(g)
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
            if (d.uuid == CCCD_UUID && status_ != BluetoothGatt.GATT_SUCCESS) {
                disconnectAndArmTeardown(g)
            }
        }

        // No onCharacteristicWrite: it existed only to advance the removed queue. The dash's
        // bypassing telemetry/time-sync writes are WRITE_TYPE_NO_RESPONSE fire-and-forget and
        // depend on nothing in that callback.

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            // ABOVE the flag write. A late notification from connection A, landing after B has
            // subscribed and armed, would otherwise set B's stand-down flag: B's watchdog then
            // declines to fire and B has no silent-hang exit left, which is the exact failure
            // the watchdog exists to catch.
            if (gatt !== g) return
            // First notification proves the subscription is live. The flag is set BEFORE the
            // cancel and is what actually stops the watchdog — not because cancel() is powerless
            // (it is honoured until the tail's body begins), but because the decision must not
            // depend on the shared job field still pointing at this connection's watchdog. See
            // firstNotifyDone.
            firstNotifyDone = true
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
         *
         * ORDERING CONSTRAINT: [FIRST_NOTIFY_TIMEOUT_MS] and [DISCOVERY_TIMEOUT_MS] must both
         * stay STRICTLY LESS than this value. See [FIRST_NOTIFY_TIMEOUT_MS] for what breaks
         * otherwise. If this constant is ever lowered, re-check both against it.
         */
        private const val READY_STABLE_MS = 20_000L

        /**
         * How long after a successfully issued CCCD write the first notification may take
         * before the connection is treated as silent and torn down. Generous next to a
         * standard HR strap's 1 Hz measurement rate, so a healthy strap never trips it even
         * across a slow connection interval negotiation.
         *
         * ORDERING CONSTRAINT: MUST stay strictly less than [READY_STABLE_MS]. The two timers
         * interleave correctly only because this one fires first: the watchdog tears the
         * connection down and cancels the pending [readyResetJob] before its [READY_STABLE_MS]
         * elapses, so [attempt] is NOT reset and the backoff keeps climbing toward its 30s
         * ceiling. Raise this past [READY_STABLE_MS] and a permanently-silent strap resets
         * [attempt] on every cycle, pinning the backoff at its 1s floor for the whole ride —
         * re-entering through this timer the exact failure [readyResetJob] exists to prevent.
         *
         * This is the constant most likely to be raised if field data says 10s is too tight.
         * Raising it past 20s is not a tuning change; it is a regression. Raise
         * [READY_STABLE_MS] first.
         */
        private const val FIRST_NOTIFY_TIMEOUT_MS = 10_000L

        /**
         * How long service discovery may take before the connection is treated as stalled and
         * torn down. Discovery on a healthy link is typically well under a second; 10s is
         * generous even across a cache refresh on a slow peer.
         *
         * ORDERING CONSTRAINT: MUST stay strictly less than [READY_STABLE_MS], for the same
         * reason as [FIRST_NOTIFY_TIMEOUT_MS] — the backoff must keep climbing across repeated
         * stalls rather than being reset by a [readyResetJob] that outlives the teardown.
         */
        private const val DISCOVERY_TIMEOUT_MS = 10_000L

        /**
         * How long after a self-initiated `disconnect()` STATE_DISCONNECTED may take before the
         * teardown is completed locally instead. Teardown on a healthy stack is milliseconds —
         * this is not a stage that runs long on real hardware, it is one that either finishes at
         * once or never finishes at all — so 5s is already far beyond generous, and keeping it
         * short matters because every other recovery path in the class waits behind it.
         *
         * Comfortably under [READY_STABLE_MS] as well, though not for the reason the other two
         * carry that constraint: this stage's tail cannot leave a [readyResetJob] running,
         * because whichever path armed it has already cancelled it (the watchdog explicitly, the
         * DISCONNECTED-equivalent cleanup in [armTeardownTimeout] structurally). The margin is
         * simply belt and braces.
         */
        private const val TEARDOWN_TIMEOUT_MS = 5_000L
    }
}
