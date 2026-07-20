package com.two17industries.rideman.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Single-process channel from a BLE client to the UI (mirrors LocationBus). */
class BleStatus {
    private val _state = MutableStateFlow(BleConnectionState.DISABLED)
    val state: StateFlow<BleConnectionState> = _state
    fun set(s: BleConnectionState) { _state.value = s }
}
