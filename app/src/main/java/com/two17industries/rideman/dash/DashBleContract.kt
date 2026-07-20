package com.two17industries.rideman.dash

import java.util.UUID

/** Frozen BLE contract — see strava-bike-puter tdisplay-dash design spec. */
object DashBleContract {
    val SERVICE_UUID: UUID = UUID.fromString("a8dc6189-de03-4f47-82ed-830b7b48d183")
    val TELEMETRY_UUID: UUID = UUID.fromString("5d48becb-087d-4c7e-88c4-83d30e7860e9")

    /**
     * Additive time-sync characteristic — the telemetry packet stays frozen at v1.
     * See 2026-07-11-dash-time-sync-design.md in the firmware repo.
     */
    val TIME_SYNC_UUID: UUID = UUID.fromString("e23bd53a-30a0-48f7-9d96-3f44fce5634d")

    const val DEVICE_NAME = "rideman-dash"
}
