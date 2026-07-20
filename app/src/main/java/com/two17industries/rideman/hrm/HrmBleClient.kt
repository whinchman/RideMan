package com.two17industries.rideman.hrm

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.content.Context
import com.two17industries.rideman.ble.BleCentral
import com.two17industries.rideman.ble.BleCentralListener
import com.two17industries.rideman.core.HeartRateSample
import kotlinx.coroutines.CoroutineScope
import java.util.UUID

/**
 * BLE central for a standard heart rate strap: connects by the Heart Rate Service UUID and
 * subscribes to Heart Rate Measurement notifications, publishing each reading to [HrmBus].
 *
 * Never bonds. HR straps are Just Works, and calling createBond() breaks reconnects on
 * several models.
 */
@SuppressLint("MissingPermission") // guarded inside BleCentral
class HrmBleClient(context: Context, scope: CoroutineScope) : BleCentralListener {

    private val central = BleCentral(
        context = context,
        serviceUuid = HrmBleContract.SERVICE_UUID,
        status = HrmStatus.status,
        scope = scope,
        listener = this,
    )

    /** Connect, optionally restricted to a remembered device MAC. */
    fun start(address: String?) {
        central.targetAddress = address
        central.start()
    }

    fun stop() {
        central.stop()
        HrmBus.reset()
    }

    override fun onReady(gatt: BluetoothGatt): Boolean {
        val ch = gatt.getService(HrmBleContract.SERVICE_UUID)
            ?.getCharacteristic(HrmBleContract.MEASUREMENT_UUID)
            ?: return false
        // subscribe() does the CCCD write as well — setCharacteristicNotification alone
        // leaves the strap connected but silent. Its return value must propagate: a strap
        // that cannot be subscribed to is a failed connection, not a connected one.
        return central.subscribe(ch)
    }

    override fun onDisconnected() {
        HrmBus.reset()
    }

    override fun onCharacteristicChanged(uuid: UUID, value: ByteArray) {
        if (uuid != HrmBleContract.MEASUREMENT_UUID) return
        val m = HeartRateMeasurement.parse(value) ?: return
        HrmBus.publish(
            HeartRateSample(
                epochMillis = System.currentTimeMillis(),
                bpm = m.bpm,
                contactOk = m.contactOk,
            )
        )
    }
}
