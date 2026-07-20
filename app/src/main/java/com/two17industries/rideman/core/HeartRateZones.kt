package com.two17industries.rideman.core

import kotlin.math.roundToInt

/**
 * Five-band heart rate zones.
 *
 * With a calibrated baseline these are Karvonen zones — percentages of heart rate *reserve*
 * (max minus baseline) — which is what makes the baseline calibration meaningful. Without
 * one they fall back to plain percentages of max, so zones work before the rider has ever
 * calibrated.
 *
 * Pure. Zone boundaries move whenever max HR is auto-raised, which is exactly why
 * time-in-zone is computed on demand and never stored.
 */
object HeartRateZones {

    const val COUNT = 5

    private val PERCENTS = listOf(0.50, 0.60, 0.70, 0.80, 0.90)

    /** Lower BPM bound of each of the five zones, ascending. */
    fun lowerBounds(maxHr: Int, baselineHr: Int?): List<Int> {
        val useReserve = baselineHr != null && baselineHr < maxHr
        return if (useReserve) {
            val reserve = maxHr - baselineHr!!
            PERCENTS.map { (baselineHr + it * reserve).roundToInt() }
        } else {
            PERCENTS.map { (it * maxHr).roundToInt() }
        }
    }

    /** Zone 1..5 for [bpm], or 0 when it is below zone 1. */
    fun zoneFor(bpm: Int, maxHr: Int, baselineHr: Int?): Int {
        val bounds = lowerBounds(maxHr, baselineHr)
        var zone = 0
        for (i in bounds.indices) if (bpm >= bounds[i]) zone = i + 1
        return zone
    }

    /**
     * Milliseconds spent in each zone. Index 0 is "below zone 1"; indices 1..5 are the zones,
     * so the array has [COUNT] + 1 entries.
     *
     * [samples] is (epochMillis, bpm) in chronological order. Each interval is attributed to
     * the zone of the sample that *starts* it; the final sample contributes no time.
     */
    fun timeInZoneMs(samples: List<Pair<Long, Int>>, maxHr: Int, baselineHr: Int?): LongArray {
        val out = LongArray(COUNT + 1)
        for (i in 0 until samples.size - 1) {
            val (t, bpm) = samples[i]
            val dt = samples[i + 1].first - t
            // A backwards timestamp must not subtract time from a zone.
            if (dt <= 0) continue
            out[zoneFor(bpm, maxHr, baselineHr)] += dt
        }
        return out
    }
}
