package com.two17industries.rideman.core

/** One GPS fix. SI units. epochMillis is the fix time. */
data class LocationSample(
    val epochMillis: Long,
    val lat: Double,
    val lng: Double,
    val speedMps: Float,
    val headingDeg: Float,
    val gpsAltitudeM: Double?,
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
