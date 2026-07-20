package com.two17industries.rideman.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MaxHeartRateTest {

    private fun samples(vararg pairs: Pair<Int, Int>, contactOk: Boolean = true) =
        pairs.map { (sec, bpm) -> CalibrationSample(sec * 1000L, bpm, contactOk) }

    @Test
    fun `age estimate is 220 minus age`() {
        assertEquals(180, MaxHeartRate.estimateFromAge(birthYear = 1986, currentYear = 2026))
    }

    @Test
    fun `age estimate is clamped to a sane floor`() {
        // A nonsense birth year must not produce a max HR below the floor.
        assertEquals(120, MaxHeartRate.estimateFromAge(birthYear = 1800, currentYear = 2026))
    }

    @Test
    fun `a peak sustained for ten seconds is corroborated`() {
        val s = (0..14).map { CalibrationSample(it * 1000L, 175, true) }
        assertEquals(175, MaxHeartRate.corroboratedPeak(s))
    }

    @Test
    fun `a brief spike is rejected`() {
        // 220 for two seconds inside an otherwise 150 bpm effort — a classic dry-electrode artifact.
        val s = (0..30).map {
            CalibrationSample(it * 1000L, if (it == 10 || it == 11) 220 else 150, true)
        }
        assertEquals(150, MaxHeartRate.corroboratedPeak(s))
    }

    @Test
    fun `a sustained peak without sensor contact is rejected`() {
        val bad = (0..14).map { CalibrationSample(it * 1000L, 205, false) }
        val good = (15..30).map { CalibrationSample(it * 1000L, 160, true) }
        assertEquals(160, MaxHeartRate.corroboratedPeak(bad + good))
    }

    @Test
    fun `a run broken by one poor contact sample does not span the break`() {
        // 190 for 6s, one bad-contact sample, then 190 for 6s. Neither side reaches 10s,
        // so the break must not be bridged into a single qualifying 13s run.
        val s = (0..12).map {
            CalibrationSample(it * 1000L, 190, contactOk = it != 6)
        }
        assertNull(MaxHeartRate.corroboratedPeak(s))
    }

    @Test
    fun `an effort shorter than the sustain window yields null`() {
        val s = (0..5).map { CalibrationSample(it * 1000L, 180, true) }
        assertNull(MaxHeartRate.corroboratedPeak(s))
    }

    @Test
    fun `an empty ride yields null`() {
        assertNull(MaxHeartRate.corroboratedPeak(emptyList()))
    }

    @Test
    fun `a gap in samples does not count as sustained`() {
        // 195 at t=0, then nothing until t=60s. Not a ten-second effort.
        val s = listOf(
            CalibrationSample(0L, 195, true),
            CalibrationSample(60_000L, 195, true),
        )
        assertNull(MaxHeartRate.corroboratedPeak(s))
    }
}
