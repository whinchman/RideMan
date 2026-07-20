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
    fun `a reading slightly newer than the fix is accepted`() {
        // The real-world case: LocationSample.epochMillis is the provider's fix-generation
        // time, while a HeartRateSample is stamped at BLE delivery. Fused-location delivery
        // latency alone puts a healthy strap's notification ahead of the fix it belongs to.
        val fixMillis = 10_000L
        val hr = HeartRateSample(epochMillis = fixMillis + 500L, bpm = 142, contactOk = true)
        assertEquals(142, HeartRateStamp.bpmFor(fixMillis = fixMillis, hr = hr))
    }

    @Test
    fun `a reading exactly at the future skew limit is accepted`() {
        val fixMillis = 10_000L
        val hr = HeartRateSample(
            epochMillis = fixMillis + HeartRateStamp.MAX_FUTURE_SKEW_MS,
            bpm = 142,
            contactOk = true,
        )
        assertEquals(142, HeartRateStamp.bpmFor(fixMillis = fixMillis, hr = hr))
    }

    @Test
    fun `a reading just past the future skew limit is dropped`() {
        val fixMillis = 10_000L
        val hr = HeartRateSample(
            epochMillis = fixMillis + HeartRateStamp.MAX_FUTURE_SKEW_MS + 1L,
            bpm = 142,
            contactOk = true,
        )
        assertNull(HeartRateStamp.bpmFor(fixMillis = fixMillis, hr = hr))
    }

    @Test
    fun `a future reading inside the freshness window is still dropped`() {
        // 2s ahead is within MAX_AGE_MS (5s) in absolute terms, so this only passes under
        // correct signed handling (age = -2000 is past -MAX_FUTURE_SKEW_MS -> dropped). A naive
        // abs(age) > MAX_AGE_MS implementation would wrongly accept it (abs(-2000) = 2000 <=
        // 5000), so this is the case that actually discriminates signed handling from the bug it
        // guards against. This is also why the future tolerance is 1s and not MAX_AGE_MS: a 5s
        // tolerance would accept this reading and silently destroy the test's value.
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
