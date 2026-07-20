package com.two17industries.rideman.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackedPointTest {

    @Test
    fun `a fresh reading with contact is stamped`() {
        val hr = HeartRateSample(epochMillis = 10_000L, bpm = 142, contactOk = true)
        assertEquals(142, HeartRateStamp.bpmFor(fixMillis = 12_000L, hr = hr))
    }

    @Test
    fun `a reading exactly at the age limit is still stamped`() {
        val hr = HeartRateSample(epochMillis = 10_000L, bpm = 142, contactOk = true)
        assertEquals(142, HeartRateStamp.bpmFor(fixMillis = 15_000L, hr = hr))
    }

    @Test
    fun `a stale reading is dropped`() {
        // The strap died. Points must go null rather than holding the last value forever.
        val hr = HeartRateSample(epochMillis = 10_000L, bpm = 142, contactOk = true)
        assertNull(HeartRateStamp.bpmFor(fixMillis = 15_001L, hr = hr))
    }

    @Test
    fun `a reading without sensor contact is dropped`() {
        val hr = HeartRateSample(epochMillis = 10_000L, bpm = 142, contactOk = false)
        assertNull(HeartRateStamp.bpmFor(fixMillis = 10_500L, hr = hr))
    }

    @Test
    fun `no reading at all yields null`() {
        assertNull(HeartRateStamp.bpmFor(fixMillis = 10_000L, hr = null))
    }

    @Test
    fun `a reading from the future is dropped rather than accepted`() {
        // Clock skew between the strap callback and the GPS fix must not admit a bogus sample.
        val hr = HeartRateSample(epochMillis = 30_000L, bpm = 142, contactOk = true)
        assertNull(HeartRateStamp.bpmFor(fixMillis = 10_000L, hr = hr))
    }
}
