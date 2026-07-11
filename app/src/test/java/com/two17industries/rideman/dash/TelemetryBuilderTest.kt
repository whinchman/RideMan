package com.two17industries.rideman.dash

import com.two17industries.rideman.core.LocationSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryBuilderTest {

    @Test fun null_sample_is_no_fix_zeroed_but_keeps_distance_and_time() {
        val t = TelemetryBuilder.build(sample = null, distanceM = 100.0, elapsedSec = 5, unitsUS = true)
        assertFalse(t.gpsValid)
        assertEquals(0f, t.speedMps, 0f)
        assertEquals(0f, t.headingDeg, 0f)
        assertEquals(100.0, t.distanceM, 0.0)   // distance/elapsed come from the tracker/clock, not the fix
        assertEquals(5L, t.elapsedSec)
        assertTrue(t.rideActive)
        assertTrue(t.unitsUS)
    }

    @Test fun present_sample_passes_through_speed_heading_altitude() {
        val s = LocationSample(
            epochMillis = 0, lat = 40.0, lng = -88.0,
            speedMps = 6.7f, headingDeg = 315f, gpsAltitudeM = 221.0,
        )
        val t = TelemetryBuilder.build(sample = s, distanceM = 12437.0, elapsedSec = 2847, unitsUS = false)
        assertTrue(t.gpsValid)
        assertEquals(6.7f, t.speedMps, 0f)
        assertEquals(315f, t.headingDeg, 0f)   // GPS course, from the sample bearing
        assertEquals(221.0, t.altitudeM, 0.0)
        assertFalse(t.unitsUS)
    }

    @Test fun null_altitude_becomes_zero() {
        val s = LocationSample(0, 40.0, -88.0, 3f, 90f, gpsAltitudeM = null)
        val t = TelemetryBuilder.build(s, 0.0, 0, unitsUS = true)
        assertEquals(0.0, t.altitudeM, 0.0)
    }
}
