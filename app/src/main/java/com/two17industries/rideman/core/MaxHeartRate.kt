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

    /** Longest gap between consecutive samples that still counts as continuous. */
    private const val MAX_GAP_MS = 5_000L

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
                if (held >= SUSTAIN_MS && (best == null || floor > best!!)) best = floor
            }
        }
        return best
    }
}
