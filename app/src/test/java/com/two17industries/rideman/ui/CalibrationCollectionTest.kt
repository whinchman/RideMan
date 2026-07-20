package com.two17industries.rideman.ui

import com.two17industries.rideman.core.BaselineCalibration
import org.junit.Assert.assertEquals
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
        // We aim at the reducer's own gate rather than a looser copy of it. This is an identity
        // check, not a drift check: the gate's actual value is pinned behaviourally over in
        // BaselineCalibrationTest, which is the test that fails if the reducer moves.
        assertEquals(BaselineCalibration.MIN_SPAN_MS, CalibrationCollection.REQUIRED_SPAN_MS)
        // The loop stops the moment the span clears that gate, and keeps going a millisecond
        // short of it — i.e. we overshoot to meet the gate rather than loosening it.
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
    fun `the collection window is anchored to the first sample not the start tap`() {
        // Tap at 0, first notification at 2_000. At wall-clock 10_000 the window has been open
        // for 8_000, not 10_000 — the strap's start-up latency is not charged against it.
        assertEquals(
            8_000L,
            CalibrationCollection.collectedMs(nowMs = 10_000, firstSampleAtMs = 2_000, startedAtMs = 0),
        )
    }

    @Test
    fun `before any sample arrives the tap stands in as the anchor`() {
        // Otherwise a strap that never reports would hold the window open forever.
        assertEquals(
            10_000L,
            CalibrationCollection.collectedMs(nowMs = 10_000, firstSampleAtMs = null, startedAtMs = 0),
        )
    }

    @Test
    fun `a session ends with a span the reducer accepts at every plausible strap rate`() {
        // The regression this guards: the window used to start at the START tap, so the
        // strap's first-notification latency came straight out of the collected span. A 2 s
        // strap lost roughly 4 s, blew through the overshoot cap still short, and a rider who
        // sat perfectly still for five minutes was told they stopped early.
        //
        // 3 s is the limit of what the 2 s cap can absorb: the loop has a window of
        // MAX_OVERSHOOT_MS + 1_000 ms in which a notification must land, so an interval wider
        // than that can straddle it. No consumer strap notifies that slowly.
        // The phase sweep matters: a real strap's notifications are not aligned to the tap, and
        // the old tap-anchored window's deficit depended on that phase (up to two intervals in
        // the worst case). Anchoring on the first sample makes the outcome phase-independent,
        // which is precisely what these iterations assert.
        for (interval in listOf(250L, 500L, 1_000L, 2_000L, 2_200L, 3_000L)) {
            for (phase in 1..interval.toInt() step 137) {
                val span = simulateSession(notifyIntervalMs = interval, firstNotifyAtMs = phase.toLong())
                assertTrue(
                    "a strap notifying every ${interval}ms, first at ${phase}ms, ended $span — " +
                        "under the reducer's ${CalibrationCollection.REQUIRED_SPAN_MS} gate",
                    span >= CalibrationCollection.REQUIRED_SPAN_MS,
                )
            }
        }
    }

    /**
     * Runs the screen's collection loop against a synthetic strap notifying every
     * [notifyIntervalMs] starting at [firstNotifyAtMs], and returns the sample span the reducer
     * would be handed.
     *
     * Mirrors the loop in HeartRateCalibrationScreen: tap at 0, a 250 ms poll tick, and the
     * window anchored on the first sample's arrival.
     */
    private fun simulateSession(
        notifyIntervalMs: Long,
        firstNotifyAtMs: Long,
        tickMs: Long = 250L,
    ): Long {
        val tappedAt = 0L
        var now = tappedAt
        var anchor: Long? = null
        var nextNotify = firstNotifyAtMs
        var first: Long? = null
        var last: Long? = null
        var collected = 0L
        var guard = 0

        while (
            CalibrationCollection.shouldKeepCollecting(
                elapsedMs = collected,
                sampleSpanMs = if (first == null || last == null) 0L else last!! - first!!,
            )
        ) {
            check(guard++ < 10_000) { "collection loop did not terminate" }
            now += tickMs
            while (nextNotify <= now) {
                if (first == null) { first = nextNotify; anchor = nextNotify }
                last = nextNotify
                nextNotify += notifyIntervalMs
            }
            collected = CalibrationCollection.collectedMs(now, anchor, tappedAt)
        }
        return if (first == null || last == null) 0L else last!! - first!!
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
