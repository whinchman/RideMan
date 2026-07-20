package com.two17industries.rideman.data

import kotlin.math.roundToInt

/**
 * Ride-level heart rate aggregates, stored on RideEntity so the History list need not load
 * every track point.
 *
 * Time-in-zone is deliberately NOT stored: max HR auto-raises, which moves every zone
 * boundary, so a stored breakdown would silently go stale. It is recomputed from track points
 * on demand instead.
 */
object RideHeartRateStats {

    /** (average, max) over the non-null readings, or (null, null) when there are none. */
    fun summarize(bpms: List<Int?>): Pair<Int?, Int?> {
        val present = bpms.filterNotNull()
        if (present.isEmpty()) return null to null
        return present.average().roundToInt() to present.max()
    }
}
