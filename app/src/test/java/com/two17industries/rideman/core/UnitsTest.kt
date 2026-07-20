package com.two17industries.rideman.core

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    @Test fun mps_to_mph() {
        assertEquals(22.369, Units.speed(10f, UnitSystem.AMERICAN).toDouble(), 0.01)
    }
    @Test fun mps_to_kmh() {
        assertEquals(36.0, Units.speed(10f, UnitSystem.METRIC).toDouble(), 0.01)
    }
    @Test fun meters_to_miles() {
        assertEquals(1.0, Units.distance(1609.344, UnitSystem.AMERICAN), 0.001)
    }
    @Test fun meters_to_km() {
        assertEquals(1.0, Units.distance(1000.0, UnitSystem.METRIC), 0.001)
    }
    @Test fun meters_to_feet() {
        assertEquals(3.28084, Units.altitude(1.0, UnitSystem.AMERICAN), 0.0001)
    }
    @Test fun meters_to_meters() {
        assertEquals(1.0, Units.altitude(1.0, UnitSystem.METRIC), 0.0001)
    }
    @Test fun labels_american() {
        assertEquals("MPH", Units.speedLabel(UnitSystem.AMERICAN))
        assertEquals("MI", Units.distanceLabel(UnitSystem.AMERICAN))
        assertEquals("FT", Units.altitudeLabel(UnitSystem.AMERICAN))
    }
    @Test fun labels_metric() {
        assertEquals("KM/H", Units.speedLabel(UnitSystem.METRIC))
        assertEquals("KM", Units.distanceLabel(UnitSystem.METRIC))
        assertEquals("M", Units.altitudeLabel(UnitSystem.METRIC))
    }
    @Test fun duration_zero() {
        assertEquals("0:00", Units.duration(0L))
    }
    @Test fun duration_seconds_only() {
        assertEquals("0:07", Units.duration(7_000L))
    }
    @Test fun duration_pads_seconds() {
        assertEquals("1:05", Units.duration(65_000L))
    }
    @Test fun duration_minutes() {
        assertEquals("24:18", Units.duration(24 * 60_000L + 18_000L))
    }
    @Test fun duration_rolls_over_to_hours() {
        assertEquals("1:00:00", Units.duration(3_600_000L))
    }
    @Test fun duration_just_under_an_hour() {
        assertEquals("59:59", Units.duration(3_599_000L))
    }
    @Test fun duration_hours_pad_minutes_and_seconds() {
        assertEquals("1:04:08", Units.duration(3_600_000L + 4 * 60_000L + 8_000L))
    }
    @Test fun duration_truncates_partial_seconds() {
        assertEquals("0:01", Units.duration(1_999L))
    }
    @Test fun duration_clamps_negative_to_zero() {
        assertEquals("0:00", Units.duration(-5_000L))
    }
}
