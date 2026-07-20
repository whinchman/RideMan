package com.two17industries.rideman.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RideHeartRateStatsTest {

    @Test
    fun `average and max over readings that exist`() {
        val (avg, max) = RideHeartRateStats.summarize(listOf(100, 120, 140))
        assertEquals(120, avg)
        assertEquals(140, max)
    }

    @Test
    fun `nulls are excluded from the average rather than counted as zero`() {
        // A dropout must not drag the average down.
        val (avg, max) = RideHeartRateStats.summarize(listOf(100, null, null, 140))
        assertEquals(120, avg)
        assertEquals(140, max)
    }

    @Test
    fun `a ride with no readings at all yields nulls`() {
        val (avg, max) = RideHeartRateStats.summarize(listOf(null, null))
        assertNull(avg)
        assertNull(max)
    }

    @Test
    fun `an empty ride yields nulls`() {
        val (avg, max) = RideHeartRateStats.summarize(emptyList())
        assertNull(avg)
        assertNull(max)
    }

    @Test
    fun `average rounds to nearest`() {
        val (avg, _) = RideHeartRateStats.summarize(listOf(100, 101))
        assertEquals(101, avg)
    }
}
