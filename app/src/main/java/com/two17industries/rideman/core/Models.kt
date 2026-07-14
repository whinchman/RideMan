package com.two17industries.rideman.core

/** One GPS fix. SI units. epochMillis is the fix time. */
data class LocationSample(
    val epochMillis: Long,
    val lat: Double,
    val lng: Double,
    /**
     * GPS Doppler speed, or null when the fix carries none (`Location.hasSpeed() == false`).
     * Null means *unknown*, never zero — a device that reports no speed must not look stationary,
     * or its whole ride would be gated out of the distance total.
     */
    val speedMps: Float?,
    val headingDeg: Float,
    val gpsAltitudeM: Double?,
    /** Horizontal accuracy in metres (68% confidence), or null when the fix carries none. */
    val accuracyM: Float? = null,
)

/** Aggregated end-of-ride numbers. SI units. */
data class RideSummary(
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val totalTimeMs: Long,
    val distanceM: Double,
    val maxSpeedMps: Float,
    val avgSpeedMps: Float,
)
