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
}
