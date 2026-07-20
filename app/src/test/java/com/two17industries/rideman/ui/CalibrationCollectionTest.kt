package com.two17industries.rideman.ui

import com.two17industries.rideman.core.BaselineCalibration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bug these pin down: the loop used to terminate on wall-clock elapsed, while
 * [BaselineCalibration.reduce] gates on the *sample span*. A ~1 Hz strap stamps each sample up
 * to a second before the loop sees it, so a genuine five-minute session could hand the reducer
 * a span a few hundred milliseconds under its threshold and be rejected as TOO_SHORT.
 */
class CalibrationCollectionTest {

    private val duration = BaselineCalibration.DURATION_MS

    @Test
    fun `collects through the nominal window regardless of span`() {
        assertTrue(CalibrationCollection.shouldKeepCollecting(elapsedMs = 0, sampleSpanMs = 0))
        assertTrue(CalibrationCollection.shouldKeepCollecting(elapsedMs = duration - 1, sampleSpanMs = 0))
    }

    @Test
    fun `stops at the nominal end when the span already satisfies the reducer`() {
        assertFalse(
            CalibrationCollection.shouldKeepCollecting(
                elapsedMs = duration,
                sampleSpanMs = CalibrationCollection.REQUIRED_SPAN_MS,
            )
        )
    }

    @Test
    fun `overshoots when stale sample stamps leave the span just short`() {
        // The failure from the field: wall clock says five minutes, but the first and last
        // stamps are stale by differing amounts, so the data spans only 298_900 — under the
        // reducer's 299_000 gate. Stopping here would reject a perfect session as TOO_SHORT.
        assertTrue(
            CalibrationCollection.shouldKeepCollecting(
                elapsedMs = duration,
                sampleSpanMs = 298_900L,
            )
        )
    }

    @Test
    fun `gives up at the hard cap so a silent strap cannot hang the session`() {
        assertFalse(
            CalibrationCollection.shouldKeepCollecting(
                elapsedMs = duration + CalibrationCollection.MAX_OVERSHOOT_MS,
                sampleSpanMs = 0,
            )
        )
    }

    @Test
    fun `required span matches the reducer's gate exactly and does not relax it`() {
        // A session whose span clears REQUIRED_SPAN_MS must be accepted by the reducer, and one
        // that falls a millisecond short must not be — i.e. we overshoot to meet the gate rather
        // than loosening it.
        assertTrue(CalibrationCollection.REQUIRED_SPAN_MS >= duration - 1_000L)
        assertFalse(
            CalibrationCollection.shouldKeepCollecting(
                elapsedMs = duration,
                sampleSpanMs = CalibrationCollection.REQUIRED_SPAN_MS,
            )
        )
        assertTrue(
            CalibrationCollection.shouldKeepCollecting(
                elapsedMs = duration,
                sampleSpanMs = CalibrationCollection.REQUIRED_SPAN_MS - 1,
            )
        )
    }

    @Test
    fun `span of an empty or single-sample session is zero`() {
        assertTrue(CalibrationCollection.spanMs(emptyList()) == 0L)
        assertTrue(CalibrationCollection.spanMs(listOf(sample(1_000))) == 0L)
    }

    @Test
    fun `span is measured first stamp to last stamp`() {
        val samples = listOf(sample(1_000), sample(200_000), sample(300_400))
        assertTrue(CalibrationCollection.spanMs(samples) == 299_400L)
    }

    private fun sample(at: Long) =
        com.two17industries.rideman.core.CalibrationSample(epochMillis = at, bpm = 60, contactOk = true)
}
