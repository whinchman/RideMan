package com.two17industries.rideman.dash

import com.two17industries.rideman.ble.BleConnectionState
import com.two17industries.rideman.ble.BleStatus
import kotlinx.coroutines.flow.StateFlow

/** Single-process channel from the dash BLE client to the UI (mirrors LocationBus). */
object DashStatus {
    val status = BleStatus()
    val state: StateFlow<BleConnectionState> = status.state
}
