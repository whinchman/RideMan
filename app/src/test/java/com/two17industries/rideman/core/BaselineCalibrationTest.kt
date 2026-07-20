package com.two17industries.rideman.core

import org.junit.Assert.assertEquals
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
}
