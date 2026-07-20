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
        /** Too few readings survived to score a window — a strap problem, not a stillness problem. */
        INSUFFICIENT_DATA,
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

    /**
     * The shortest sample span [reduce] will accept as a full session.
     *
     * [DURATION_MS] minus a one-second tolerance, because a ~1 Hz strap stamps its first and
     * last readings inside the session rather than exactly on its edges, so a perfectly
     * performed five minutes hands us slightly under five minutes of data.
     *
     * This is the single definition of that gate: `reduce` applies it, and the collection loop
     * aims at it. Do not restate the arithmetic anywhere else.
     */
    const val MIN_SPAN_MS = DURATION_MS - 1_000L

    /** Discarded at the start, to allow the rider to settle. */
    const val SETTLE_MS = 60_000L

    /** Rolling window the baseline is averaged over. */
    const val WINDOW_MS = 60_000L

    private const val MAX_POOR_CONTACT_FRACTION = 0.10
    private const val MAX_WINDOW_STD_DEV = 5.0

    /**
     * [stoppedEarly] is the session's actual outcome: true when the rider pressed STOP, false
     * when the session ran its full length. It is the only way to tell the two causes of a short
     * sample span apart, and the reducer cannot derive it — the span is a property of the
     * *data*, while the session's length is a property of the *wall clock*.
     *
     * Without it, a strap that dropped at minute four of a five-minute session yields a
     * four-minute span, and a rider who sat perfectly still is told they stopped early.
     *
     * Defaulted to true (the historical reading, "a short span means the rider stopped") so the
     * behavioural tests that pin the session-length gate still say what they meant. Production
     * callers pass the real outcome explicitly.
     */
    fun reduce(
        samples: List<CalibrationSample>,
        stoppedEarly: Boolean = true,
    ): CalibrationResult {
        // A short span is the rider's fault only if the rider ended the session. If it ran its
        // full length and the data is still short, the readings did not arrive — a strap
        // problem, which is exactly what INSUFFICIENT_DATA exists to say. That case was
        // previously unreachable, because this gate fired first and blamed the rider.
        val shortSpanFailure = if (stoppedEarly) {
            CalibrationResult.Failure.TOO_SHORT
        } else {
            CalibrationResult.Failure.INSUFFICIENT_DATA
        }

        if (samples.isEmpty()) return CalibrationResult.Failed(shortSpanFailure)

        val start = samples.first().epochMillis
        val elapsed = samples.last().epochMillis - start
        if (elapsed < MIN_SPAN_MS) {
            return CalibrationResult.Failed(shortSpanFailure)
        }

        val poor = samples.count { !it.contactOk }
        if (poor.toDouble() / samples.size > MAX_POOR_CONTACT_FRACTION) {
            return CalibrationResult.Failed(CalibrationResult.Failure.POOR_CONTACT)
        }

        // Duration and overall contact ratio are already confirmed above, so an empty usable set
        // here means the settle filter and per-sample contact filter starved the window scorer —
        // a strap problem, not a session-length problem.
        val usable = samples.filter { it.epochMillis - start >= SETTLE_MS && it.contactOk }
        if (usable.isEmpty()) {
            return CalibrationResult.Failed(CalibrationResult.Failure.INSUFFICIENT_DATA)
        }

        var bestMean: Double? = null
        var anyWindowScored = false
        for (i in usable.indices) {
            val windowStart = usable[i].epochMillis
            val window = usable.asSequence()
                .drop(i)
                .takeWhile { it.epochMillis - windowStart < WINDOW_MS }
                .toList()
            // Only score full windows, so the tail of the session cannot win on a short slice.
            if (window.size < 2) continue
            if (window.last().epochMillis - windowStart < WINDOW_MS - 2_000L) continue
            anyWindowScored = true

            val values = window.map { it.bpm.toDouble() }
            val mean = values.average()
            val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
            if (sqrt(variance) > MAX_WINDOW_STD_DEV) continue

            if (bestMean == null || mean < bestMean) bestMean = mean
        }

        val result = bestMean ?: return CalibrationResult.Failed(
            // Windows were scored but all too noisy vs. no window ever reaching full length —
            // the latter is a strap/data problem, not evidence the rider was moving.
            if (anyWindowScored) {
                CalibrationResult.Failure.TOO_VARIABLE
            } else {
                CalibrationResult.Failure.INSUFFICIENT_DATA
            },
        )
        return CalibrationResult.Ok(result.roundToInt())
    }
}
