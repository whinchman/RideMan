package com.two17industries.rideman.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Transaction

@Dao
interface RideDao {
    @Insert
    suspend fun insertRide(ride: RideEntity): Long

    @Insert
    suspend fun insertTrackPoints(points: List<TrackPointEntity>)

    @Transaction
    suspend fun insertRideWithTrack(ride: RideEntity, points: List<TrackPointEntity>): Long {
        val rideId = insertRide(ride)
        insertTrackPoints(points.map { it.copy(rideId = rideId) })
        return rideId
    }
}
