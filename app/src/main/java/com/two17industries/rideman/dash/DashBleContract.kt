package com.two17industries.rideman.dash

import java.util.UUID

/** Frozen BLE contract — see strava-bike-puter tdisplay-dash design spec. */
object DashBleContract {
    val SERVICE_UUID: UUID = UUID.fromString("a8dc6189-de03-4f47-82ed-830b7b48d183")
    val TELEMETRY_UUID: UUID = UUID.fromString("5d48becb-087d-4c7e-88c4-83d30e7860e9")
    const val DEVICE_NAME = "rideman-dash"
}
