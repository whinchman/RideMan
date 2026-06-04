package com.two17industries.rideman.core

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    @Test fun zero_distance_same_point() {
        assertEquals(0.0, Geo.haversineMeters(40.0, -88.0, 40.0, -88.0), 0.001)
    }
    @Test fun one_degree_latitude_is_about_111km() {
        assertEquals(111195.0, Geo.haversineMeters(0.0, 0.0, 1.0, 0.0), 50.0)
    }
    @Test fun short_hop_is_reasonable() {
        val d = Geo.haversineMeters(40.4406, -88.0000, 40.4420, -88.0000)
        assertEquals(155.7, d, 2.0)
    }
}
