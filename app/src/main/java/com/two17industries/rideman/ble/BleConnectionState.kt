package com.two17industries.rideman.ble

/**
 * Connection state shared by every BLE consumer (dash, HRM, and cadence later).
 * NO_PERMISSION and BLUETOOTH_OFF are distinct from DISABLED so the UI can tell the rider
 * what to actually do about it, rather than silently degrading the way the dash used to.
 */
enum class BleConnectionState {
    /** Feature switched off in settings. */
    DISABLED,
    NO_PERMISSION,
    BLUETOOTH_OFF,
    SCANNING,
    CONNECTED,
    DISCONNECTED,
}
