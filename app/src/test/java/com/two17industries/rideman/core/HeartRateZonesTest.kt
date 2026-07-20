package com.two17industries.rideman.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        karvonen.zip(pctOfMax).forEach { (k, p) -> assertTrue("$k should exceed $p", k > p) }
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
        assertEquals(3, HeartRateZones.zoneFor(140, 200, null))
        assertEquals(4, HeartRateZones.zoneFor(160, 200, null))
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

    @Test
    fun `timeInZoneMs credits nothing for a dropout, and still credits either side of it`() {
        // The rider is in zone 1, the strap dies for ten minutes, and comes back in zone 2.
        // Points exist across the dropout but carry no reading — that is what null means here.
        val samples = listOf<Pair<Long, Int?>>(
            0L to 105,          // zone 1
            1_000L to 105,      // zone 1
            2_000L to null,     // strap drops
            600_000L to null,   // ...still dead, ten minutes later
            601_000L to 125,    // zone 2, back on
            602_000L to 125,    // zone 2, final sample contributes nothing
        )
        val result = HeartRateZones.timeInZoneMs(samples, 200, null)
        // Only 0->1000 (zone 1) and 601000->602000 (zone 2) have a reading at BOTH ends.
        // The ten-minute dropout must contribute nothing at all.
        assertArrayEquals(longArrayOf(0, 1_000, 1_000, 0, 0, 0), result)
    }

    @Test
    fun `timeInZoneMs attributes nothing across a gap with no points at all`() {
        // A GPS tunnel produces no track points whatsoever, so the gap is not marked by nulls —
        // it is simply a long interval between two readings. It must not be credited either.
        val samples = listOf<Pair<Long, Int?>>(0L to 105, 1_000L to 105, 601_000L to 105)
        val result = HeartRateZones.timeInZoneMs(samples, 200, null)
        assertEquals(1_000L, result[1])
    }

    @Test
    fun `timeInZoneMs on a contiguous series is unchanged by the gap rule`() {
        // Same series as the attribution test above, widened to nullable: no gaps, so today's
        // behaviour must survive the change exactly.
        val samples = listOf<Pair<Long, Int?>>(0L to 105, 1_000L to 125, 3_000L to 125)
        val result = HeartRateZones.timeInZoneMs(samples, 200, null)
        assertArrayEquals(longArrayOf(0, 1_000, 2_000, 0, 0, 0), result)
    }

    @Test
    fun `timeInZoneMs ignores a zero-delta duplicate sample`() {
        // t=0 bpm 105 (zone 1), t=2000 bpm 125 (zone 2, duplicate timestamp), t=2000 bpm 130 (zone 2)
        val samples = listOf(0L to 105, 2_000L to 125, 2_000L to 130, 5_000L to 140)
        val result = HeartRateZones.timeInZoneMs(samples, 200, null)
        // interval 0->2000 (1000ms in zone 1), duplicate at 2000 contributes nothing, 2000->5000 (3000ms in zone 2)
        assertArrayEquals(longArrayOf(0, 2_000, 3_000, 0, 0, 0), result)
    }
}
