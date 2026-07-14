package com.two17industries.rideman.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RideTrackerTest {
    private fun sample(t: Long, lat: Double, lng: Double, speed: Float?) =
        LocationSample(t, lat, lng, speed, 0f, null, accuracyM = null)

    /**
     * A phone sitting still still emits fixes that wander by a few metres. Haversine distance is
     * always positive, so without a gate that jitter rectifies into phantom forward distance —
     * reproduced on-device 2026-07-13: a motionless phone logged 0.01 mi in 50 s.
     */
    @Test fun stationary_jitter_does_not_accumulate_distance() {
        val t = RideTracker(startMillis = 0L)
        // ~3 m of GPS wander in each direction, Doppler speed reports stationary throughout.
        t.add(sample(0L, 42.4800000, -83.0600000, 0.05f))
        t.add(sample(1000L, 42.4800270, -83.0600000, 0.11f))
        t.add(sample(2000L, 42.4799730, -83.0600300, 0.09f))
        t.add(sample(3000L, 42.4800000, -83.0599700, 0.02f))
        assertEquals(0.0, t.distanceM, 0.0001)
    }

    /**
     * Safety property. `Location.hasSpeed()` can be false, in which case speed is unknown — NOT
     * zero. If an unknown speed were treated as "stationary", a device that never reports Doppler
     * speed would have its entire ride's distance silently discarded. Unknown speed must never gate.
     */
    @Test fun unknown_speed_still_accumulates_distance() {
        val t = RideTracker(startMillis = 0L)
        t.add(sample(0L, 0.0, 0.0, null))
        t.add(sample(1000L, 1.0, 0.0, null))
        assertEquals(111195.0, t.distanceM, 100.0)
    }

    /** Real movement must survive the stationary gate — the gate must not eat slow riding. */
    @Test fun slow_but_real_movement_accumulates_distance() {
        val t = RideTracker(startMillis = 0L)
        // ~4 m per 1 s fix ≈ 14 km/h — typical cycling displacement at 1 Hz.
        t.add(sample(0L, 42.4800000, -83.0600000, 4.0f))
        t.add(sample(1000L, 42.4800360, -83.0600000, 4.0f))
        assertEquals(4.0, t.distanceM, 0.5)
    }

    /** An unknown speed must not be mistaken for 0 and must not disturb max speed. */
    @Test fun max_speed_ignores_unknown_speed() {
        val t = RideTracker(startMillis = 0L)
        t.add(sample(0L, 0.0, 0.0, 5f))
        t.add(sample(1000L, 0.0, 0.0, null))
        assertEquals(5f, t.maxSpeedMps, 0.0001f)
    }

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
        // Speeds must be consistent with the movement: 111 m in 10 s is ~11 m/s. (The original
        // fixture reported 0 m/s while covering 111 m, which no real GPS would emit.)
        t.add(sample(0L, 0.0, 0.0, 11f))
        t.add(sample(10_000L, 0.001, 0.0, 11f))
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
