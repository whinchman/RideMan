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
    fun `a future reading inside the freshness window is still dropped`() {
        // 2s ahead is within MAX_AGE_MS (5s) in absolute terms, so this only passes under
        // correct signed handling (age = -2000 < 0 -> dropped). A naive abs(age) > MAX_AGE_MS
        // implementation would wrongly accept it (abs(-2000) = 2000 <= 5000), so this is the
        // case that actually discriminates signed handling from the bug it guards against.
        val fixMillis = 10_000L
        val hr = HeartRateSample(epochMillis = fixMillis + 2_000L, bpm = 142, contactOk = true)
        assertNull(HeartRateStamp.bpmFor(fixMillis = fixMillis, hr = hr))
    }

    @Test
    fun `a reading from the far future is dropped rather than accepted`() {
        // Clock skew between the strap callback and the GPS fix must not admit a bogus sample.
        val hr = HeartRateSample(epochMillis = 30_000L, bpm = 142, contactOk = true)
        assertNull(HeartRateStamp.bpmFor(fixMillis = 10_000L, hr = hr))
    }
}
