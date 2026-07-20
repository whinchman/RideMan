package com.two17industries.rideman.data

import com.two17industries.rideman.core.RideSummary
import com.two17industries.rideman.core.TrackedPoint
import com.two17industries.rideman.strava.StravaExternalId
import kotlinx.coroutines.flow.Flow

class RideRepository(private val dao: RideDao) {

    fun allRides(): Flow<List<RideEntity>> = dao.getAllRides()

    fun planTaggedRides(): Flow<List<RideEntity>> = dao.getPlanTaggedRides()

    suspend fun saveRide(
        summary: RideSummary,
        track: List<TrackedPoint>,
        planRideId: String? = null,
    ): Long {
        val (avgHr, maxHr) = RideHeartRateStats.summarize(track.map { it.heartRateBpm })
        val ride = RideEntity(
            startedAt = summary.startedAtMillis,
            endedAt = summary.endedAtMillis,
            totalTimeMs = summary.totalTimeMs,
            distanceM = summary.distanceM,
            maxSpeedMps = summary.maxSpeedMps,
            avgSpeedMps = summary.avgSpeedMps,
            planRideId = planRideId,
            avgHeartRateBpm = avgHr,
            maxHeartRateBpm = maxHr,
        )
        val points = track.map { tp ->
            val sample = tp.sample
            TrackPointEntity(
                rideId = 0,
                timestamp = sample.epochMillis,
                lat = sample.lat,
                lng = sample.lng,
                altitudeM = sample.gpsAltitudeM,
                speedMps = sample.speedMps ?: 0f,
                headingDeg = sample.headingDeg,
                accuracyM = sample.accuracyM,
                heartRateBpm = tp.heartRateBpm,
            )
        }
        return dao.insertRideWithTrack(ride, points)
    }

    suspend fun markQueued(rideId: Long, ride: RideEntity) {
        dao.updateStravaStatus(
            rideId = rideId,
            state = StravaUploadState.QUEUED,
            activityId = null,
            externalId = StravaExternalId.forRide(ride.id, ride.startedAt),
            error = null,
        )
    }

    suspend fun getRide(rideId: Long): RideEntity? = dao.getRide(rideId)

    suspend fun deleteRides(rideIds: List<Long>) = dao.deleteRides(rideIds)
}
