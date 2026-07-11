package com.two17industries.rideman.dash

import com.two17industries.rideman.core.LocationSample

/**
 * Assembles a [Telemetry] snapshot. distance/elapsed are supplied by the broadcaster's own
 * RideTracker + clock; speed/heading/altitude come from the latest GPS fix (heading is the
 * GPS course-over-ground). A null fix means "no data yet" — gpsValid=false, motion fields 0,
 * but distance and elapsed still flow through.
 */
object TelemetryBuilder {
    fun build(
        sample: LocationSample?,
        distanceM: Double,
        elapsedSec: Long,
        unitsUS: Boolean,
        themeIndex: Int = 0,
    ): Telemetry =
        Telemetry(
            speedMps = sample?.speedMps ?: 0f,
            distanceM = distanceM,
            elapsedSec = elapsedSec,
            headingDeg = sample?.headingDeg ?: 0f,
            altitudeM = sample?.gpsAltitudeM ?: 0.0,
            unitsUS = unitsUS,
            rideActive = true,
            gpsValid = sample != null,
            theme = themeIndex,
        )
}
