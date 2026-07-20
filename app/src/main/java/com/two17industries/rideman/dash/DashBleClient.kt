package com.two17industries.rideman.dash

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import com.two17industries.rideman.ble.BleCentral
import com.two17industries.rideman.ble.BleCentralListener
import kotlinx.coroutines.CoroutineScope

/**
 * BLE central for the T-Display: connects by service UUID and writes telemetry to the
 * Telemetry characteristic (write-without-response). Scan/connect/reconnect all live in
 * [BleCentral]; this class owns only the dash-specific write paths.
 *
 * [write] and [writeTime] are both fire-and-forget: `WRITE_TYPE_NO_RESPONSE`, the
 * `writeCharacteristic` return value is ignored, and they deliberately BYPASS
 * [BleCentral.enqueue]. That is safe ONLY because the broadcaster's 1 Hz ticker is the sole
 * caller of this write path (one write per tick, never both `write` and `writeTime` in the
 * same tick). A second concurrent writer would collide on `GATT_BUSY` and be silently
 * dropped — there is nothing anywhere that would surface the failure.
 *
 * The queue in [BleCentral] exists for subscriptions (the HRM's CCCD write), not for these.
 * [BleCentral.operationComplete] ignores write callbacks that did not come from a queued
 * operation, so these bypassing writes cannot advance someone else's queue.
 */
@SuppressLint("MissingPermission") // guarded inside BleCentral
class DashBleClient(context: Context, scope: CoroutineScope) : BleCentralListener {

    @Volatile private var characteristic: BluetoothGattCharacteristic? = null
    @Volatile private var timeChar: BluetoothGattCharacteristic? = null

    private val central = BleCentral(
        context = context,
        serviceUuid = DashBleContract.SERVICE_UUID,
        status = DashStatus.status,
        scope = scope,
        listener = this,
    )

    fun start() = central.start()

    fun stop() {
        characteristic = null
        timeChar = null
        central.stop()
    }

    fun write(bytes: ByteArray) {
        val g = central.gatt() ?: return
        val c = characteristic ?: return
        g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
    }

    /**
     * Writes the time-sync packet. No-ops if the peer has no time-sync characteristic —
     * i.e. a board on older firmware. That is not an error: the dash keeps working, it just
     * has no clock. MUST only be called from the broadcaster's 1 Hz ticker (see class KDoc).
     */
    fun writeTime(bytes: ByteArray) {
        val g = central.gatt() ?: return
        val c = timeChar ?: return
        g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
    }

    override fun onReady(gatt: BluetoothGatt): Boolean {
        val svc = gatt.getService(DashBleContract.SERVICE_UUID)
        val ch = svc?.getCharacteristic(DashBleContract.TELEMETRY_UUID) ?: return false
        // Absent on older firmware — not an error, we just never get a clock.
        //
        // ORDERING IS LOAD-BEARING: timeChar must be assigned before this returns true,
        // because BleCentral publishes CONNECTED only after onReady returns. The scheduler
        // syncs on the CONNECTED edge, so assigning timeChar afterwards would burn the first
        // sync and leave the board at "--:--" for 60s. (Recorded as D4 in the dash-time-sync
        // review, where it was load-bearing but uncommented. It is commented now.)
        timeChar = svc.getCharacteristic(DashBleContract.TIME_SYNC_UUID)
        characteristic = ch
        return true
    }

    override fun onDisconnected() {
        characteristic = null
        timeChar = null
    }
}
