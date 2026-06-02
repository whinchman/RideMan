package com.two17industries.rideman.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RideTrackerTest {
    private fun sample(t: Long, lat: Double, lng: Double, speed: Float) =
        LocationSample(t, lat, lng, speed, 0f, null)

    @Test fun starts_empty() {
        val t = RideTracker(startMillis = 1000L)
        assertEquals(0.0, t.distanceM, 0.0001)
        assertEquals(0f, t.maxSpeedMps, 0.0001f)
    }

    @Test fun accumulates_distance_between_points() {
        val t = RideTracker(startMillis = 0L)
        t.add(sample(0L, 0.0, 0.0, 5f))
        t.add(sample(1000L, 1.0, 0.0, 6f))
        assertEquals(111195.0, t.distanceM, 100.0)
    }

    @Test fun tracks_max_speed() {
        val t = RideTracker(startMillis = 0L)
        t.add(sample(0L, 0.0, 0.0, 5f))
        t.add(sample(1000L, 0.0, 0.0, 9f))
        t.add(sample(2000L, 0.0, 0.0, 7f))
        assertEquals(9f, t.maxSpeedMps, 0.0001f)
    }

    @Test fun avg_speed_is_distance_over_elapsed() {
        val t = RideTracker(startMillis = 0L)
        t.add(sample(0L, 0.0, 0.0, 0f))
        t.add(sample(10_000L, 0.001, 0.0, 0f))
        val summary = t.summarize(endMillis = 10_000L)
        assertEquals(11.12, summary.avgSpeedMps.toDouble(), 0.3)
        assertEquals(10_000L, summary.totalTimeMs)
    }

    @Test fun avg_speed_zero_when_no_time() {
        val t = RideTracker(startMillis = 5000L)
        val summary = t.summarize(endMillis = 5000L)
        assertEquals(0f, summary.avgSpeedMps, 0.0001f)
    }
}
