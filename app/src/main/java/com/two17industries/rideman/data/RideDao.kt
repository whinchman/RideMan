package com.two17industries.rideman.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

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

    /** All rides, newest first — for the History screen. */
    @Query("SELECT * FROM rides ORDER BY startedAt DESC")
    fun getAllRides(): Flow<List<RideEntity>>

    /** Rides tagged to a plan slot — for deriving plan progress. */
    @Query("SELECT * FROM rides WHERE planRideId IS NOT NULL")
    fun getPlanTaggedRides(): Flow<List<RideEntity>>
}
