package com.two17industries.rideman.ui

import com.two17industries.rideman.core.BaselineCalibration
import com.two17industries.rideman.core.CalibrationSample

/**
 * When a calibration session should stop collecting.
 *
 * This exists because two different clocks disagree. The session loop measures *wall elapsed*
 * time, but [BaselineCalibration.reduce] gates on the *sample span* — the distance between the
 * first and last sample's `epochMillis`, which is stamped when the strap notified. A ~1 Hz strap
 * leaves each stamp up to a second stale, so a loop that stopped the instant wall elapsed hit
 * five minutes could hand the reducer a span a few hundred milliseconds short and have a
 * genuine, perfectly-performed session thrown away as TOO_SHORT.
 *
 * The fix is to overshoot: past the nominal end, keep collecting until the *data* spans the
 * duration the reducer demands. The reducer's gate is not relaxed — [REQUIRED_SPAN_MS] is that
 * gate, restated. The rider sees no difference, because the displayed countdown clamps at zero.
 */
object CalibrationCollection {

    /**
     * The span [BaselineCalibration.reduce] requires. Derived from the reducer's own constant
     * rather than restated, so the two cannot drift apart: if that gate moves, this moves with
     * it. Deliberately not looser.
     */
    const val REQUIRED_SPAN_MS = BaselineCalibration.MIN_SPAN_MS

    /**
     * How far past the nominal end we will wait for the data to catch up. Bounded so a strap
     * that has gone silent cannot hang the session forever — it will fail TOO_SHORT instead,
     * which in that case is the truthful answer.
     */
    const val MAX_OVERSHOOT_MS = 2_000L

    /**
     * How long the collection window has been open, in the clock [shouldKeepCollecting] expects.
     *
     * Anchored to the arrival of the *first sample*, not to the START tap. The gap between those
     * two scales with the strap's notification interval, and charging it against the window is
     * what made a slower strap fail: at 2 Hz-and-slower the data finished several seconds short
     * of the reducer's gate, past [MAX_OVERSHOOT_MS], and a rider who sat perfectly still for
     * five minutes was told they stopped early. Anchoring on the first sample removes that
     * leading gap outright, leaving only the trailing one for the overshoot to absorb.
     *
     * Before any sample arrives [firstSampleAtMs] is null and the tap stands in, so a strap that
     * never reports ends the session on the wall clock rather than hanging it forever.
     *
     * This does not move the countdown, which stays anchored to the tap and still reads 5:00 the
     * instant the rider presses START.
     */
    fun collectedMs(nowMs: Long, firstSampleAtMs: Long?, startedAtMs: Long): Long =
        nowMs - (firstSampleAtMs ?: startedAtMs)

    /** True while the session should keep collecting samples. */
    fun shouldKeepCollecting(elapsedMs: Long, sampleSpanMs: Long): Boolean = when {
        // Inside the nominal five minutes: always collecting.
        elapsedMs < BaselineCalibration.DURATION_MS -> true
        // Past the overshoot budget: stop and let the reducer say what it finds.
        elapsedMs >= BaselineCalibration.DURATION_MS + MAX_OVERSHOOT_MS -> false
        // In the overshoot: keep going only until the data actually spans the duration.
        else -> sampleSpanMs < REQUIRED_SPAN_MS
    }

    /** Distance from the first sample's stamp to the last. Zero for fewer than two samples. */
    fun spanMs(samples: List<CalibrationSample>): Long =
        if (samples.size < 2) 0L else samples.last().epochMillis - samples.first().epochMillis
}
