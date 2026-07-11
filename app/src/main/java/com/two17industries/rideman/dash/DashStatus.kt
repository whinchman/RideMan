package com.two17industries.rideman.dash

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class DashConnectionState { DISABLED, SCANNING, CONNECTED, DISCONNECTED }

/** Single-process channel from the BLE client to the UI (mirrors LocationBus). */
object DashStatus {
    private val _state = MutableStateFlow(DashConnectionState.DISABLED)
    val state: StateFlow<DashConnectionState> = _state
    fun set(s: DashConnectionState) { _state.value = s }
}
