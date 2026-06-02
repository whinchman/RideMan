package com.two17industries.rideman.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "track_points",
    foreignKeys = [ForeignKey(
        entity = RideEntity::class,
        parentColumns = ["id"],
        childColumns = ["rideId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("rideId")],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rideId: Long,
    val timestamp: Long,
    val lat: Double,
    val lng: Double,
    val altitudeM: Double?,
    val speedMps: Float,
    val headingDeg: Float,
)
