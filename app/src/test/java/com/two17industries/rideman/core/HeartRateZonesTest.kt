package com.two17industries.rideman.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HeartRateZonesTest {

    @Test
    fun `without a baseline zones are straight percentages of max`() {
        // max 200, no calibration -> 50/60/70/80/90% of max
        assertEquals(listOf(100, 120, 140, 160, 180), HeartRateZones.lowerBounds(200, null))
    }

    @Test
    fun `with a baseline zones use heart rate reserve`() {
        // max 200, baseline 60 -> reserve 140; bound = baseline + pct * reserve
        // 50% -> 60 + 70 = 130, 60% -> 144, 70% -> 158, 80% -> 172, 90% -> 186
        assertEquals(listOf(130, 144, 158, 172, 186), HeartRateZones.lowerBounds(200, 60))
    }

    @Test
    fun `karvonen bounds sit above percent of max bounds`() {
        val karvonen = HeartRateZones.lowerBounds(190, 55)
        val pctOfMax = HeartRateZones.lowerBounds(190, null)
        karvonen.zip(pctOfMax).forEach { (k, p) -> assert(k > p) { "$k should exceed $p" } }
    }

    @Test
    fun `baseline at or above max falls back to percent of max`() {
        assertEquals(HeartRateZones.lowerBounds(180, null), HeartRateZones.lowerBounds(180, 180))
        assertEquals(HeartRateZones.lowerBounds(180, null), HeartRateZones.lowerBounds(180, 190))
    }

    @Test
    fun `zoneFor returns zero below zone one`() {
        assertEquals(0, HeartRateZones.zoneFor(99, 200, null))
    }

    @Test
    fun `zoneFor is inclusive at each lower bound`() {
        assertEquals(1, HeartRateZones.zoneFor(100, 200, null))
        assertEquals(2, HeartRateZones.zoneFor(120, 200, null))
        assertEquals(5, HeartRateZones.zoneFor(180, 200, null))
    }

    @Test
    fun `zoneFor clamps above max to zone five`() {
        assertEquals(5, HeartRateZones.zoneFor(250, 200, null))
    }

    @Test
    fun `timeInZoneMs attributes each interval to the zone of its starting sample`() {
        // t=0 bpm 105 (zone 1), t=1000 bpm 125 (zone 2), t=3000 bpm 125 (zone 2, final sample)
        val samples = listOf(0L to 105, 1_000L to 125, 3_000L to 125)
        val result = HeartRateZones.timeInZoneMs(samples, 200, null)
        // zone 1 held for 1000ms, zone 2 for 2000ms; the final sample contributes nothing
        assertArrayEquals(longArrayOf(0, 1_000, 2_000, 0, 0, 0), result)
    }

    @Test
    fun `timeInZoneMs on an empty list is all zeroes`() {
        assertArrayEquals(longArrayOf(0, 0, 0, 0, 0, 0), HeartRateZones.timeInZoneMs(emptyList(), 200, null))
    }

    @Test
    fun `timeInZoneMs ignores a backwards timestamp rather than subtracting time`() {
        val samples = listOf(0L to 105, 5_000L to 105, 2_000L to 105)
        val result = HeartRateZones.timeInZoneMs(samples, 200, null)
        assertEquals(5_000L, result[1])
    }
}
