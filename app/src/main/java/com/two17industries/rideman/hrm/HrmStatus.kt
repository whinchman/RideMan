package com.two17industries.rideman.hrm

import com.two17industries.rideman.ble.BleConnectionState
import com.two17industries.rideman.ble.BleStatus
import kotlinx.coroutines.flow.StateFlow

/** Single-process channel from the HRM BLE client to the UI (mirrors DashStatus). */
object HrmStatus {
    val status = BleStatus()
    val state: StateFlow<BleConnectionState> = status.state
}
