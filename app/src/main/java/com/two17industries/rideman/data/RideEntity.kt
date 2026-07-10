package com.two17industries.rideman.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val totalTimeMs: Long,
    val distanceM: Double,
    val maxSpeedMps: Float,
    val avgSpeedMps: Float,
    /** Plan slot this ride was tagged to (e.g. "w3B"), or null for a free ride. */
    val planRideId: String? = null,
    val stravaState: StravaUploadState = StravaUploadState.NONE,
    val stravaActivityId: Long? = null,
    val stravaExternalId: String? = null,
    val stravaError: String? = null,
)
