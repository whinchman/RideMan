package com.two17industries.rideman.data

import com.two17industries.rideman.core.LocationSample
import com.two17industries.rideman.core.RideSummary
import kotlinx.coroutines.flow.Flow

class RideRepository(private val dao: RideDao) {

    fun allRides(): Flow<List<RideEntity>> = dao.getAllRides()

    fun planTaggedRides(): Flow<List<RideEntity>> = dao.getPlanTaggedRides()

    suspend fun saveRide(
        summary: RideSummary,
        track: List<LocationSample>,
        planRideId: String? = null,
    ): Long {
        val ride = RideEntity(
            startedAt = summary.startedAtMillis,
            endedAt = summary.endedAtMillis,
            totalTimeMs = summary.totalTimeMs,
            distanceM = summary.distanceM,
            maxSpeedMps = summary.maxSpeedMps,
            avgSpeedMps = summary.avgSpeedMps,
            planRideId = planRideId,
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
