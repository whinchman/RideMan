package com.two17industries.rideman.hrm

import java.util.UUID

/**
 * Bluetooth SIG Heart Rate Profile. Not a rideman invention — this is the standard profile
 * every chest strap exposes, which is why no vendor app is needed to read one.
 */
object HrmBleContract {
    /** Heart Rate Service, 0x180D. */
    val SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")

    /** Heart Rate Measurement characteristic, 0x2A37 (notify). */
    val MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
}
