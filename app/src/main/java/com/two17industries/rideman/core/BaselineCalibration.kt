package com.two17industries.rideman.core

import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One heart rate reading captured during a calibration session. */
data class CalibrationSample(
    val epochMillis: Long,
    val bpm: Int,
    val contactOk: Boolean,
)

sealed interface CalibrationResult {
    data class Ok(val baselineBpm: Int) : CalibrationResult
    data class Failed(val reason: Failure) : CalibrationResult

    enum class Failure {
        /** The rider stopped before the full five minutes elapsed. */
        TOO_SHORT,
        /** More than 10% of samples reported no skin contact — reseat the strap. */
        POOR_CONTACT,
        /** The rider was still moving; no 60-second window was steady enough. */
        TOO_VARIABLE,
    }
}

/**
 * Reduces a five-minute seated session to a baseline heart rate.
 *
 * The result is the *lowest rolling 60-second mean*, not the lowest single sample — one low
 * reading is noise. The first 60 seconds are discarded so settling after sitting down does
 * not inflate the answer.
 *
 * This is deliberately called a *baseline*, not a resting heart rate: true RHR is measured on
 * waking, lying down, and reads meaningfully lower than seated-for-five-minutes. The trend is
 * valid so long as it is always measured the same way.
 */
object BaselineCalibration {

    /** Required session length: five minutes. */
    const val DURATION_MS = 300_000L

    /** Discarded at the start, to allow the rider to settle. */
    const val SETTLE_MS = 60_000L

    /** Rolling window the baseline is averaged over. */
    const val WINDOW_MS = 60_000L

    private const val MAX_POOR_CONTACT_FRACTION = 0.10
    private const val MAX_WINDOW_STD_DEV = 5.0

    fun reduce(samples: List<CalibrationSample>): CalibrationResult {
        if (samples.isEmpty()) return CalibrationResult.Failed(CalibrationResult.Failure.TOO_SHORT)

        val start = samples.first().epochMillis
        val elapsed = samples.last().epochMillis - start
        if (elapsed < DURATION_MS - 1_000L) {
            return CalibrationResult.Failed(CalibrationResult.Failure.TOO_SHORT)
        }

        val poor = samples.count { !it.contactOk }
        if (poor.toDouble() / samples.size > MAX_POOR_CONTACT_FRACTION) {
            return CalibrationResult.Failed(CalibrationResult.Failure.POOR_CONTACT)
        }

        val usable = samples.filter { it.epochMillis - start >= SETTLE_MS && it.contactOk }
        if (usable.isEmpty()) {
            return CalibrationResult.Failed(CalibrationResult.Failure.TOO_SHORT)
        }

        var bestMean: Double? = null
        for (i in usable.indices) {
            val windowStart = usable[i].epochMillis
            val window = usable.asSequence()
                .drop(i)
                .takeWhile { it.epochMillis - windowStart < WINDOW_MS }
                .toList()
            // Only score full windows, so the tail of the session cannot win on a short slice.
            if (window.size < 2) continue
            if (window.last().epochMillis - windowStart < WINDOW_MS - 2_000L) continue

            val values = window.map { it.bpm.toDouble() }
            val mean = values.average()
            val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
            if (sqrt(variance) > MAX_WINDOW_STD_DEV) continue

            if (bestMean == null || mean < bestMean) bestMean = mean
        }

        val result = bestMean
            ?: return CalibrationResult.Failed(CalibrationResult.Failure.TOO_VARIABLE)
        return CalibrationResult.Ok(result.roundToInt())
    }
}
