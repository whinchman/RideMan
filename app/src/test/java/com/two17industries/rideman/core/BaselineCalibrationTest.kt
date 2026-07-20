package com.two17industries.rideman.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineCalibrationTest {

    /** One sample per second for [seconds], all with the given bpm and good contact. */
    private fun steady(seconds: Int, bpm: Int, contactOk: Boolean = true) =
        (0 until seconds).map { CalibrationSample(it * 1000L, bpm, contactOk) }

    @Test
    fun `a steady five minute session yields that heart rate`() {
        val r = BaselineCalibration.reduce(steady(300, 62))
        assertEquals(CalibrationResult.Ok(62), r)
    }

    @Test
    fun `the first sixty seconds are discarded`() {
        // High while settling, then steady low. The settling period must not raise the result.
        val settling = (0 until 60).map { CalibrationSample(it * 1000L, 95, true) }
        val settled = (60 until 300).map { CalibrationSample(it * 1000L, 58, true) }
        assertEquals(CalibrationResult.Ok(58), BaselineCalibration.reduce(settling + settled))
    }

    @Test
    fun `the lowest rolling sixty second window wins not the lowest sample`() {
        // Steady 70, with a single 40 bpm artifact. One low sample must not define the result.
        val samples = steady(300, 70).toMutableList()
        samples[200] = CalibrationSample(200_000L, 40, true)
        val r = BaselineCalibration.reduce(samples) as CalibrationResult.Ok
        assertTrue("expected ~70, got ${r.baselineBpm}", r.baselineBpm in 69..70)
    }

    @Test
    fun `a session shorter than the required duration is rejected`() {
        val r = BaselineCalibration.reduce(steady(90, 60))
        assertEquals(CalibrationResult.Failed(CalibrationResult.Failure.TOO_SHORT), r)
    }

    /**
     * The session-length gate, pinned by behaviour rather than by arithmetic.
     *
     * The required span is spelled out here instead of being read from
     * [BaselineCalibration.MIN_SPAN_MS], deliberately: a test that derives its input from the
     * constant under test moves whenever that constant moves, and so can never detect drift.
     * These two cases straddle the boundary, so either one fails the moment the gate shifts —
     * which is exactly the "sat still for five minutes, told you stopped early" regression.
     */
    @Test
    fun `a span of exactly the required length is not rejected as too short`() {
        val r = BaselineCalibration.reduce(spanning(BaselineCalibration.DURATION_MS - 1_000L))
        assertNotEquals(CalibrationResult.Failed(CalibrationResult.Failure.TOO_SHORT), r)
    }

    @Test
    fun `a span one millisecond short of the required length is rejected as too short`() {
        val r = BaselineCalibration.reduce(spanning(BaselineCalibration.DURATION_MS - 1_000L - 1L))
        assertEquals(CalibrationResult.Failed(CalibrationResult.Failure.TOO_SHORT), r)
    }

    /**
     * Steady, good-contact samples at roughly 1 Hz whose first and last stamps are exactly
     * [spanMs] apart — so the only thing under test is the span.
     */
    private fun spanning(spanMs: Long): List<CalibrationSample> {
        val ticks = (0 until spanMs / 1000).map { CalibrationSample(it * 1000L, 62, true) }
        return ticks + CalibrationSample(spanMs, 62, true)
    }

    @Test
    fun `an empty session is rejected as too short`() {
        assertEquals(
            CalibrationResult.Failed(CalibrationResult.Failure.TOO_SHORT),
            BaselineCalibration.reduce(emptyList()),
        )
    }

    @Test
    fun `more than ten percent poor contact is rejected`() {
        val samples = steady(300, 62).mapIndexed { i, s ->
            if (i % 5 == 0) s.copy(contactOk = false) else s   // 20% bad
        }
        assertEquals(
            CalibrationResult.Failed(CalibrationResult.Failure.POOR_CONTACT),
            BaselineCalibration.reduce(samples),
        )
    }

    @Test
    fun `exactly ten percent poor contact is accepted`() {
        val samples = steady(300, 62).mapIndexed { i, s ->
            if (i % 10 == 0) s.copy(contactOk = false) else s   // 10% bad
        }
        assertTrue(BaselineCalibration.reduce(samples) is CalibrationResult.Ok)
    }

    @Test
    fun `a session that never settles is rejected as too variable`() {
        // Alternating 55/85 gives a standard deviation of ~15 bpm in every window.
        val samples = (0 until 300).map {
            CalibrationSample(it * 1000L, if (it % 2 == 0) 55 else 85, true)
        }
        assertEquals(
            CalibrationResult.Failed(CalibrationResult.Failure.TOO_VARIABLE),
            BaselineCalibration.reduce(samples),
        )
    }

    @Test
    fun `mild variation within five bpm is accepted`() {
        val samples = (0 until 300).map {
            CalibrationSample(it * 1000L, if (it % 2 == 0) 60 else 63, true)
        }
        assertTrue(BaselineCalibration.reduce(samples) is CalibrationResult.Ok)
    }

    @Test
    fun `good contact confined to the settle period is insufficient data not too short`() {
        // A full five-minute session: 60s of good contact while settling, then only two
        // poor-contact samples afterward. Overall poor-contact ratio stays well under 10%, and
        // the session ran the full duration, so this must not be blamed on session length.
        val settling = (0 until 60).map { CalibrationSample(it * 1000L, 62, true) }
        val afterSettle = listOf(
            CalibrationSample(61_000L, 62, false),
            CalibrationSample(300_000L, 62, false),
        )
        val samples = settling + afterSettle
        assertEquals(
            CalibrationResult.Failed(CalibrationResult.Failure.INSUFFICIENT_DATA),
            BaselineCalibration.reduce(samples),
        )
    }

    @Test
    fun `post-settle samples too sparse for any full window is insufficient data not too variable`() {
        // A full five-minute session with a solid settle period, then only four good-contact
        // readings spread across the remainder — never close enough together to fill a
        // sixty-second window. No window can be scored at all, so this is a strap/data problem,
        // not evidence the rider never sat still.
        val settling = (0 until 60).map { CalibrationSample(it * 1000L, 62, true) }
        val sparse = listOf(
            CalibrationSample(61_000L, 62, true),
            CalibrationSample(161_000L, 62, true),
            CalibrationSample(261_000L, 62, true),
            CalibrationSample(300_000L, 62, true),
        )
        val samples = settling + sparse
        assertEquals(
            CalibrationResult.Failed(CalibrationResult.Failure.INSUFFICIENT_DATA),
            BaselineCalibration.reduce(samples),
        )
    }

    @Test
    fun `a genuinely unsteady full-density session is still too variable not insufficient data`() {
        // Full one-sample-per-second density for the whole session, oscillating well outside the
        // 5 bpm std-dev threshold in every window. Windows ARE scored here — this guards that the
        // new insufficient-data branch cannot swallow a real too-variable result.
        val samples = (0 until 300).map {
            CalibrationSample(it * 1000L, if (it % 3 == 0) 50 else 80, true)
        }
        assertEquals(
            CalibrationResult.Failed(CalibrationResult.Failure.TOO_VARIABLE),
            BaselineCalibration.reduce(samples),
        )
    }
}
