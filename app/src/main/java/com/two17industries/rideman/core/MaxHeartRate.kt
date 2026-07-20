package com.two17industries.rideman.core

/**
 * Max heart rate: seeded from age, then raised by observation.
 *
 * The auto-raise rule requires corroboration rather than an absolute cap. A candidate peak
 * counts only if the rider held it for [SUSTAIN_MS] across samples that *all* reported good
 * sensor contact. That is deliberate: dry-electrode artifacts at ride start are brief and
 * usually flagged as poor contact, so they fail on both counts — whereas a naive "reject
 * anything above N" would discard honest data from a genuinely hard effort.
 *
 * Max never auto-lowers.
 */
object MaxHeartRate {

    /** How long a candidate peak must be held to count. */
    const val SUSTAIN_MS = 10_000L

    /**
     * Longest gap between consecutive samples that still counts as continuous.
     *
     * Models BLE strap packet loss: the strap notifies on the heartbeat rather than at a fixed
     * rate, and a dropped packet can leave a gap in an otherwise-continuous run. A gap longer
     * than this means the run was not continuously observed and cannot be treated as sustained.
     * Five seconds is a judgement call, not a value from any spec.
     *
     * Shared with [HeartRateZones.timeInZoneMs], which asks the same *question* — "were these
     * two samples continuously observed?" — but of a different stream: this one paces off strap
     * notifications, which arrive on the heartbeat, while [HeartRateZones.timeInZoneMs] paces off
     * track points, which arrive at the GPS fix rate. The single shared value is only correct
     * because both streams happen to sit near 1 Hz today. If either stream's cadence changes,
     * split this into two constants rather than retuning the value in place — a strap that
     * starts notifying at 1-2 s, for example, must not silently re-attribute time-in-zone across
     * every historical ride just because the sustain-window gate got tighter.
     */
    const val MAX_GAP_MS = 5_000L

    private const val FLOOR_BPM = 120
    private const val CEILING_BPM = 220

    /** Seed estimate, `220 - age`, clamped to a sane range. */
    fun estimateFromAge(birthYear: Int, currentYear: Int): Int {
        val age = currentYear - birthYear
        return (220 - age).coerceIn(FLOOR_BPM, CEILING_BPM)
    }

    /**
     * The highest BPM the rider held for [SUSTAIN_MS] with good sensor contact, or null if no
     * effort in [samples] qualifies.
     *
     * [samples] must be in chronological order.
     */
    fun corroboratedPeak(samples: List<CalibrationSample>): Int? {
        var best: Int? = null

        // For each starting sample, walk forward while contact holds, samples stay contiguous,
        // and BPM stays at or above the candidate. The candidate is the run's own minimum, so
        // a run of 190,195,190 corroborates 190 rather than the momentary 195.
        for (i in samples.indices) {
            if (!samples[i].contactOk) continue
            var floor = samples[i].bpm
            var j = i
            while (j + 1 < samples.size) {
                val next = samples[j + 1]
                if (!next.contactOk) break
                if (next.epochMillis - samples[j].epochMillis > MAX_GAP_MS) break
                j++
                if (next.bpm < floor) floor = next.bpm
                val held = samples[j].epochMillis - samples[i].epochMillis
                val currentBest = best
                if (held >= SUSTAIN_MS && (currentBest == null || floor > currentBest)) {
                    best = floor
                }

                // floor is the run's running minimum, so it is monotonically non-increasing as
                // the run extends (j grows). Once floor has dropped to or below the best
                // qualifying run found so far, every further extension of this run has a floor
                // that is <= this floor, so it can never exceed best either. Breaking here only
                // skips runs that are provably not better than the current best — it cannot
                // change the result.
                val bestSoFar = best
                if (bestSoFar != null && floor <= bestSoFar) break
            }
        }
        return best
    }
}
