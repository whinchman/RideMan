package com.two17industries.rideman.data

import com.two17industries.rideman.core.LocationSample
import com.two17industries.rideman.core.RideSummary

class RideRepository(private val dao: RideDao) {

    suspend fun saveRide(summary: RideSummary, track: List<LocationSample>): Long {
        val ride = RideEntity(
            startedAt = summary.startedAtMillis,
            endedAt = summary.endedAtMillis,
            totalTimeMs = summary.totalTimeMs,
            distanceM = summary.distanceM,
            maxSpeedMps = summary.maxSpeedMps,
            avgSpeedMps = summary.avgSpeedMps,
        )
        val points = track.map {
            TrackPointEntity(
                rideId = 0,
                timestamp = it.epochMillis,
                lat = it.lat,
                lng = it.lng,
                altitudeM = it.gpsAltitudeM,
                speedMps = it.speedMps,
                headingDeg = it.headingDeg,
            )
        }
        return dao.insertRideWithTrack(ride, points)
    }
}
