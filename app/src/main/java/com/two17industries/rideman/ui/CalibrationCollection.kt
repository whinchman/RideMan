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
