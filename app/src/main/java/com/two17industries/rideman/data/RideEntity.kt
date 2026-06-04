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
)
